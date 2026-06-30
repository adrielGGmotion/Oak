package com.oak.app.sandbox

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.storage.StorageManager
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.oak.app.SandboxSessions
import com.oak.app.TerminalLine
import com.oak.app.data.AppSettings
import com.oak.app.data.ConversationStorage
import com.oak.app.getExternalOakRoot
import com.oak.app.isExternalStorageAccessible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
    private val appSettings: AppSettings,
    private val distroManager: ProotDistroManager = ProotDistroManager(context),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    private var setupWakeLock: PowerManager.WakeLock? = null
    private var sandboxCompletionNotificationId = 20000
    val state: StateFlow<SandboxState> = _state

    /** Directory for non-distro-specific files (tmp, etc.) */
    private val supportDir: File
        get() = File(context.filesDir, "linux-sandbox")

    /** Rootfs path for the active distro. */
    val rootfsPath: String get() = distroManager.activeRootfsPath

    /** The active distro id. */
    val activeDistroId: String get() = distroManager.activeDistroId

    /** The active SandboxEnvironment. */
    val activeEnvironment: SandboxEnvironment get() = distroManager.activeEnvironment

    // Sandbox /root is bind-mounted from externally-visible storage so agent
    // files survive uninstall and can be opened via FileProvider Intents.
    // Tries external shared storage first (/sdcard/Oak/sandbox-home/),
    // falls back to Android/data/<pkg>/sandbox-home/, then supportDir/home.
    // Computed lazily; directories created + legacy migration happen once.
    val homePath: String by lazy { resolveHome() }

    private fun resolveHome(): String {
        val oakRoot = getExternalOakRoot()
        if (oakRoot != null) {
            val dir = File(oakRoot, "sandbox-home")
            if (dir.isDirectory || dir.mkdirs()) {
                migrateLegacyHome(dir)
                return dir.absolutePath
            }
        }
        val external = context.getExternalFilesDir(null)
        if (external != null) {
            val externalTarget = File(external, "sandbox-home")
            if (externalTarget.isDirectory || externalTarget.mkdirs()) {
                migrateLegacyHome(externalTarget)
                return externalTarget.absolutePath
            }
        }
        val internalTarget = File(supportDir, "home")
        if (!internalTarget.isDirectory && !internalTarget.mkdirs()) {
            throw IllegalStateException("Cannot create sandbox home directory: ${internalTarget.absolutePath}")
        }
        migrateLegacyHome(internalTarget)
        return internalTarget.absolutePath
    }

    private fun migrateLegacyHome(target: File) {
        val legacy = File(supportDir, "home")
        if (!legacy.isDirectory || legacy.absolutePath == target.absolutePath) return
        if (!target.listFiles().isNullOrEmpty()) return
        try {
            legacy.listFiles()?.forEach { entry ->
                val dest = File(target, entry.name)
                if (!dest.exists()) entry.copyRecursively(dest, overwrite = false)
            }
        } catch (e: Exception) {
            android.util.Log.w("LinuxSandbox", "Legacy home migration failed: ${e.message}")
        }
    }

    val tmpPath: String get() = File(supportDir, "tmp").absolutePath

    fun getStorageVolumeMap(): Map<String, String> {
        if (!appSettings.isStorageAccessEnabled()) return emptyMap()
        if (!isExternalStorageAccessible()) return emptyMap()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyMap()
        return buildMap {
            storageManager.storageVolumes.forEach { avolume ->
                val volumePath = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> avolume.directory?.absolutePath
                    avolume.isPrimary -> Environment.getExternalStorageDirectory().absolutePath
                    else -> null
                } ?: return@forEach
                val uuid = avolume.uuid?.takeIf { it.isNotEmpty() && it != "null" }
                val volumeId = if (avolume.isPrimary) "internal" else (uuid ?: "external_${volumePath.hashCode().toUInt().toString(16)}")
                put(volumeId, volumePath)
            }
        }
    }

    // Run proot directly from nativeLibraryDir where Android grants execute permission
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    init {
        // Restore persisted active distro
        val persisted = appSettings.getActiveSandboxDistro()
        distroManager.setActiveDistro(persisted)
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val proot = File(prootPath)
        if (distroManager.isActiveReady && proot.exists() && proot.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                // Resolve homePath on IO so the lazy init + legacy migration
                // never blocks the UI thread via shellFor/transcriptFor.
                homePath
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        // Clean up partial downloads — distroManager now owns the archive
        distroManager.cleanupArchive(activeDistroId)
        // Determine correct state based on what exists
        if (distroManager.isActiveReady && File(prootPath).exists()) {
            _state.value = SandboxState.Ready
        } else {
            _state.value = SandboxState.NotInstalled
        }
    }

    private suspend fun setupInternal() {
        startSandboxForegroundService("Preparing...")
        acquireSetupWakeLock()
        try {
            val env = distroManager.activeEnvironment

            // Verify proot is available in nativeLibraryDir
            val proot = File(prootPath)
            if (!proot.exists()) {
                throw IllegalStateException(
                    "Proot binary not found at $prootPath. " +
                        "nativeLibraryDir contents: ${File(nativeLibDir).listFiles()?.map { it.name } ?: "empty"}",
                )
            }

            // Create support directories (tmp, etc.)
            supportDir.mkdirs()
            File(supportDir, "tmp").mkdirs()

            // Copy libtalloc with correct soname (Android strips .so.2 suffix in jniLibs)
            copyLibtalloc()

            // Download and extract rootfs via distro manager
            val rootfsDir = distroManager.rootfsDir(env.id)
            if (!rootfsDir.isDirectory) {
                try {
                    updateSandboxSetupNotification("Downloading ${env.displayName}...")
                    _state.value = SandboxState.Downloading(0f, env.id)
                    distroManager.download(
                        id = env.id,
                        onProgress = { progress ->
                            _state.value = SandboxState.Downloading(progress, env.id)
                        },
                        onExtracting = {
                            updateSandboxSetupNotification("Extracting rootfs...")
                            _state.value = SandboxState.Extracting
                        },
                    )
                } catch (e: Exception) {
                    android.util.Log.e("LinuxSandbox", "Download/extract failed for ${env.id}", e)
                    throw e
                }
            }

            // First-boot setup: resolv.conf + firstBootCommands
            updateSandboxSetupNotification("Configuring...")
            _state.value = SandboxState.Installing("Configuring...")

            // Create a temporary executor for setup
            val executor = createProotExecutor()
            // Ensure resolv.conf exists (download already writes it, but this
            // covers the case where rootfs was already present on disk).
            distroManager.writeResolvConf(rootfsDir)

            // Run first-boot commands (package manager init, etc.)
            for (cmd in env.firstBootCommands) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                updateSandboxSetupNotification("Running: ${cmd.take(60)}...")
                val result = executor.execute(cmd, timeoutSeconds = 120)
                val success = result["success"] as? Boolean ?: false
                if (!success) {
                    val stderr = result["stderr"] as? String ?: ""
                    android.util.Log.w("LinuxSandbox", "First-boot command failed: $cmd — $stderr")
                    // Non-fatal for most first-boot commands (some may be best-effort)
                }
            }

            _state.value = SandboxState.Ready
            postSandboxCompleteNotification("${env.displayName} setup complete")
        } finally {
            stopSandboxForegroundService()
            releaseSetupWakeLock()
        }
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(supportDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = supportDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
        extraProotArgs = activeEnvironment.extraProotArgs,
        extraEnv = ProotConfig.envFor(activeEnvironment),
    )

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    // Debounce per-session transcript writes. A burst of commands (e.g. a
    // 1000-iteration loop) would otherwise re-serialize the entire conversations
    // JSON and rewrite SharedPreferences once per command.
    private val pendingSaves = mutableMapOf<String, Job>()

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = SessionShell(sessionId, inner, initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        synchronized(pendingSaves) {
            pendingSaves[sessionId]?.cancel()
            pendingSaves[sessionId] = scope.launch {
                try {
                    delay(TRANSCRIPT_SAVE_DEBOUNCE)
                    conversationStorage.updateShellTranscript(sessionId, lines)
                } finally {
                    synchronized(pendingSaves) { pendingSaves.remove(sessionId) }
                }
            }
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    fun clearTranscript(sessionId: String) {
        synchronized(shells) { shells[sessionId] }?.transcript?.clear()
    }

    fun closeShell(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    private fun closeAllShells() {
        val all = synchronized(shells) {
            val snapshot = shells.values.toList()
            shells.clear()
            _sessions.value = emptyList()
            snapshot
        }
        all.forEach { it.reset() }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        val env = activeEnvironment
        val pm = env.packageManager
        val packages = env.defaultPackages
        currentJob = scope.launch {
            startSandboxForegroundService("Installing packages...")
            acquireSetupWakeLock()
            try {
                val executor = createProotExecutor()
                for (pkg in packages) {
                    ensureActive()
                    updateSandboxSetupNotification("Installing $pkg...")
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val result = executor.execute(pm.install(pkg), timeoutSeconds = 120)
                    ensureActive()
                    val success = result["success"] as? Boolean ?: false
                    if (!success) {
                        val stderr = result["stderr"] as? String ?: ""
                        val stdout = result["stdout"] as? String ?: ""
                        val error = result["error"] as? String ?: ""
                        val timedOut = result["timed_out"] as? Boolean ?: false
                        val exitCode = result["exit_code"] as? Int ?: -1
                        android.util.Log.e("LinuxSandbox", "Failed to install $pkg: exit=$exitCode timedOut=$timedOut error=$error stdout=$stdout stderr=$stderr")
                        // Rootfs is still valid — transition to Ready so the UI
                        // shows "Install packages" (not a misleading re-download
                        // prompt). The partial state is surfaced via the
                        // packagesInstalled / arePackagesInstalled() check.
                        _state.value = SandboxState.Ready
                        postSandboxCompleteNotification("Package install failed for: $pkg")
                        return@launch
                    }
                }
                _state.value = SandboxState.Ready
                postSandboxCompleteNotification("Packages installed")
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            } finally {
                stopSandboxForegroundService()
                releaseSetupWakeLock()
            }
        }
    }

    /** Switch the active distro, closing all shells. No-op if already active. */
    fun switchDistro(id: String) {
        if (activeDistroId == id) return
        closeAllShells()
        distroManager.setActiveDistro(id)
        appSettings.setActiveSandboxDistro(id)
        checkExistingInstallation()
    }

    fun getDistroManager(): ProotDistroManager = distroManager

    fun removeDistro(id: String) {
        val wasActive = (activeDistroId == id)
        if (wasActive) {
            closeAllShells()
        }
        distroManager.remove(id)
        if (wasActive) {
            appSettings.setActiveSandboxDistro(activeDistroId)
            checkExistingInstallation()
        }
    }

    fun reset() {
        val id = activeDistroId
        scope.launch {
            closeAllShells()
            distroManager.remove(id)
            appSettings.setActiveSandboxDistro(activeDistroId)
            _state.value = SandboxState.NotInstalled
        }
    }

    private fun startSandboxForegroundService(detail: String? = null) {
        try {
            val intent = Intent(context, SandboxSetupService::class.java).apply {
                if (detail != null) putExtra(SandboxSetupService.EXTRA_DETAIL, detail)
            }
            context.startForegroundService(intent)
        } catch (_: Exception) {
            // Service start may fail if app is in restricted state
        }
    }

    private fun updateSandboxSetupNotification(detail: String) {
        try {
            // Restart with updated detail so the notification text reflects the current phase
            startSandboxForegroundService(detail)
        } catch (_: Exception) { }
    }

    private fun stopSandboxForegroundService() {
        try {
            context.stopService(Intent(context, SandboxSetupService::class.java))
        } catch (_: Exception) { }
    }

    private fun postSandboxCompleteNotification(text: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = android.app.Notification.Builder(context, SANDBOX_SETUP_CHANNEL_ID)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = builder
                .setContentTitle("Sandbox setup")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            manager.notify(sandboxCompletionNotificationId++, notification)
        } catch (_: Exception) { }
    }

    private fun acquireSetupWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Oak:SandboxSetupWakeLock",
            )
            lock.acquire()
            setupWakeLock = lock
        } catch (_: Exception) { }
    }

    private fun releaseSetupWakeLock() {
        try {
            setupWakeLock?.release()
        } catch (_: Exception) { }
        setupWakeLock = null
    }

    fun getDiskUsageMB(): Long {
        // Walk the active distro's rootfs for disk usage
        val rootDir = File(rootfsPath)
        if (!rootDir.isDirectory) return 0
        // Manual stack walk instead of walkTopDown(): the latter throws an
        // AssertionError if a child entry transitions from directory→non-directory
        // between the iterator's isDirectory check and DirectoryState construction.
        // The rootfs can contain unix sockets / FIFOs / broken symlinks (e.g. from
        // user-run programs like node), and concurrent install activity also races
        // the walk. We skip bad entries and keep going.
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(rootDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                        // skip sockets, FIFOs, broken symlinks
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry, keep iterating
                }
            }
        }
        return total / (1024 * 1024)
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return activeEnvironment.installedCheckPaths.all { path ->
            File(rootfsPath, path).exists()
        }
    }
}
