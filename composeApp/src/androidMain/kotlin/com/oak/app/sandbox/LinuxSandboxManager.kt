package com.oak.app.sandbox

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.storage.StorageManager
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.oak.app.DistroState
import com.oak.app.SandboxSessions
import com.oak.app.TerminalLine
import com.oak.app.data.AppSettings
import com.oak.app.data.ConversationStorage
import com.oak.app.getExternalOakRoot
import com.oak.app.isExternalStorageAccessible
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
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
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    private var setupWakeLock: PowerManager.WakeLock? = null
    private var sandboxCompletionNotificationId = 20000
    val state: StateFlow<SandboxState> = _state

    /** Base directory for all sandbox data. */
    private val sandboxDir: File get() = File(context.filesDir, "linux-sandbox")

    /** Directory containing per-distro rootfs directories. */
    private val sandboxesDir: File get() = File(sandboxDir, "sandboxes")

    /** Rootfs path for the active distro. */
    private fun rootfsDirFor(distroId: String): File =
        File(sandboxesDir, "$distroId/rootfs")

    /** Archive file path for a distro download in progress. */
    private fun archiveFileFor(distroId: String, ext: String): File =
        File(sandboxesDir, "$distroId/rootfs.$ext")

    /** The active distro id from settings. */
    var activeDistroId: String
        get() = appSettings.getActiveDistro()
        set(value) = appSettings.setActiveDistro(value)

    /** Rootfs path for the current active distro. */
    val rootfsPath: String get() = rootfsDirFor(activeDistroId).absolutePath

    /** Distro config for the current active distro. */
    private val activeConfig: DistroConfig get() = DistroConfigs.forDistro(Distro.fromId(activeDistroId))

    // Sandbox /root is bind-mounted from externally-visible storage so agent
    // files survive uninstall and can be opened via FileProvider Intents.
    // Tries external shared storage first (/sdcard/Oak/sandbox-home/),
    // falls back to Android/data/<pkg>/sandbox-home/, then sandboxDir/home.
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
        val internalTarget = File(sandboxDir, "home")
        if (!internalTarget.isDirectory && !internalTarget.mkdirs()) {
            throw IllegalStateException("Cannot create sandbox home directory: ${internalTarget.absolutePath}")
        }
        migrateLegacyHome(internalTarget)
        return internalTarget.absolutePath
    }

    private fun migrateLegacyHome(target: File) {
        val legacy = File(sandboxDir, "home")
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

    val tmpPath: String get() = File(sandboxDir, "tmp").absolutePath

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

    private val downloader = RootfsDownloader(HttpClient(Android))

    init {
        migrateLegacyRootfs()
        checkActiveDistro()
    }

    /** Migrate from legacy single-rootfs layout (linux-sandbox/rootfs) to per-distro. */
    private fun migrateLegacyRootfs() {
        val legacyRootfs = File(sandboxDir, "rootfs")
        if (!legacyRootfs.isDirectory) return
        val alpineRootfs = rootfsDirFor("alpine")
        if (alpineRootfs.isDirectory) return // already migrated
        sandboxesDir.mkdirs()
        if (legacyRootfs.renameTo(alpineRootfs)) {
            android.util.Log.i("LinuxSandbox", "Migrated legacy rootfs to sandboxes/alpine/rootfs")
        } else {
            // renameTo can fail across devices; copy instead
            try {
                legacyRootfs.copyRecursively(alpineRootfs, overwrite = true)
                legacyRootfs.deleteRecursively()
                android.util.Log.i("LinuxSandbox", "Copied legacy rootfs to sandboxes/alpine/rootfs")
            } catch (e: Exception) {
                android.util.Log.w("LinuxSandbox", "Legacy migration failed: ${e.message}")
            }
        }
    }

    /** Check if the active distro's rootfs exists and update state. */
    private fun checkActiveDistro() {
        val proot = File(prootPath)
        if (!proot.exists() || !proot.canExecute()) return
        val cfg = activeConfig
        val rootfs = rootfsDirFor(activeDistroId)
        _state.value = if (downloader.verifyRootfs(rootfs, cfg)) {
            SandboxState.Ready
        } else {
            SandboxState.NotInstalled
        }
    }

    /** Check if a specific distro is downloaded. */
    fun isDistroDownloaded(distroId: String): Boolean {
        val cfg = DistroConfigs.forDistro(Distro.fromId(distroId))
        return downloader.verifyRootfs(rootfsDirFor(distroId), cfg)
    }

    /** Get the state of all distros for UI display. */
    fun getAllDistroStates(): List<DistroState> = Distro.entries.map { d ->
        DistroState(
            distroId = d.id,
            isActive = d.id == activeDistroId,
            isDownloaded = isDistroDownloaded(d.id),
        )
    }

    /** Switch active distro. The UI checks isDistroDownloaded before calling this. */
    fun setActiveDistro(distroId: String) {
        if (distroId == activeDistroId) return
        closeAllShells()
        activeDistroId = distroId
        checkActiveDistro()
    }

    /** Download and set up a specific distro. */
    fun downloadDistro(distroId: String) {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                homePath
                downloadDistroInternal(distroId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkActiveDistro()
                throw e
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Download failed for $distroId")
            }
        }
    }

    /** Remove a downloaded distro's rootfs. */
    fun removeDistro(distroId: String) {
        scope.launch {
            closeAllShells()
            val rootfs = rootfsDirFor(distroId)
            if (rootfs.exists()) {
                rootfs.deleteRecursively()
            }
            File(sandboxesDir, distroId).deleteRecursively()
            if (distroId == activeDistroId) {
                activeDistroId = "alpine"
            }
            checkActiveDistro()
            // MutableStateFlow skips emission when the value equals the
            // previous one (equality check). For non-active distro removals
            // the state hasn't changed, so force a re-emission to let the
            // UI refresh distro availability.
            val current = _state.value
            _state.value = SandboxState.NotInstalled
            _state.value = current
        }
    }

    private suspend fun downloadDistroInternal(distroId: String) {
        val distro = Distro.fromId(distroId)
        val config = DistroConfigs.forDistro(distro)
        val rootfsDir = rootfsDirFor(distroId)
        val arch = DistroConfigs.getAndroidArch()

        startSandboxForegroundService("Preparing $distroId...")
        acquireSetupWakeLock()
        try {
            // Verify proot is available
            val proot = File(prootPath)
            if (!proot.exists()) {
                throw IllegalStateException(
                    "Proot binary not found at $prootPath. " +
                        "nativeLibraryDir contents: ${File(nativeLibDir).listFiles()?.map { it.name } ?: "empty"}",
                )
            }

            // Create directories
            sandboxDir.mkdirs()
            File(sandboxDir, "tmp").mkdirs()
            sandboxesDir.mkdirs()
            rootfsDir.parentFile?.mkdirs()

            // Copy libtalloc
            copyLibtalloc()

            // Download if not already present
            if (!downloader.verifyRootfs(rootfsDir, config)) {
                // Clean up partial rootfs if any
                if (rootfsDir.exists()) {
                    rootfsDir.deleteRecursively()
                }
                rootfsDir.parentFile?.mkdirs()

                val ext = when (config.compression) {
                    Compression.GZIP -> "tar.gz"
                    Compression.XZ -> "tar.xz"
                }
                val archive = archiveFileFor(distroId, ext)
                try {
                    updateSandboxSetupNotification("Downloading $distroId rootfs...")
                    _state.value = SandboxState.Downloading(0f, distroId)
                    downloader.download(config, arch, archive) { progress ->
                        _state.value = SandboxState.Downloading(progress, distroId)
                    }

                    // Set as active while extracting so state transitions work
                    activeDistroId = distroId

                    updateSandboxSetupNotification("Extracting $distroId rootfs...")
                    _state.value = SandboxState.Extracting
                    downloader.extract(archive, rootfsDir, config.compression)

                    // Verify extraction
                    if (!downloader.verifyRootfs(rootfsDir, config)) {
                        android.util.Log.e("LinuxSandbox", "Rootfs verification failed for $distroId")
                        // Try to flatten and re-check
                        rootfsDir.listFiles()?.forEach { file ->
                            android.util.Log.d("LinuxSandbox", "  rootfs contents: ${file.name}")
                        }
                        throw IllegalStateException("Rootfs verification failed for $distroId: /bin/sh not found or missing key files")
                    }
                } finally {
                    archive.delete()
                }
            }

            // Post-setup: writable + resolv.conf
            updateSandboxSetupNotification("Configuring $distroId...")
            _state.value = SandboxState.Installing("Configuring...")
            downloader.makeWritable(rootfsDir)
            downloader.writeResolvConf(rootfsDir)

            // Update package lists
            updateSandboxSetupNotification("Updating package lists...")
            val executor = createProotExecutorFor(rootfsDir)
            val pm = config.packageManager
            val updateResult = executor.execute(pm.update, timeoutSeconds = 120)
            if (updateResult["success"] as? Boolean != true) {
                android.util.Log.w("LinuxSandbox", "${pm.update} failed for $distroId, continuing anyway")
            }

            // Install setup packages (bash, python3) which are essential for shell operation
            _state.value = SandboxState.Installing("Installing setup packages...")
            val setupPackages = config.setupPackages
            if (setupPackages.isNotEmpty()) {
                val setupCmd = "${pm.install} ${setupPackages.joinToString(" ") { shellQuote(it) }}"
                val setupResult = executor.execute(setupCmd, timeoutSeconds = 300)
                if (setupResult["success"] as? Boolean != true) {
                    android.util.Log.w("LinuxSandbox", "Setup package install failed for $distroId: ${setupResult["stderr"]}")
                }
            }

            _state.value = SandboxState.Ready
            postSandboxCompleteNotification("$distroId sandbox setup complete")
        } finally {
            stopSandboxForegroundService()
            releaseSetupWakeLock()
        }
    }

    fun setup() {
        downloadDistro(activeDistroId)
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        // Clean up partial archive downloads for all distros
        sandboxesDir.listFiles()?.forEach { distroDir ->
            if (distroDir.isDirectory) {
                distroDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".tar.gz") || file.name.endsWith(".tar.xz"))) {
                        file.delete()
                    }
                }
            }
        }
        checkActiveDistro()
    }

    private suspend fun installSetupPackagesInternal(distroId: String) {
        val config = DistroConfigs.forDistro(Distro.fromId(distroId))
        val rootfsDir = rootfsDirFor(distroId)
        val executor = createProotExecutorFor(rootfsDir)
        val pm = config.packageManager

        // Update package lists first
        executor.execute(pm.update, timeoutSeconds = 120)

        // Install setup packages
        val allPackages = config.setupPackages + config.basicPackages
        val unique = allPackages.distinct()
        for (pkg in unique) {
            currentCoroutineContext().ensureActive()
            updateSandboxSetupNotification("Installing $pkg...")
            _state.value = SandboxState.Installing("Installing $pkg...")
            val cmd = "${pm.install} ${shellQuote(pkg)}"
            val result = executor.execute(cmd, timeoutSeconds = 300)
            currentCoroutineContext().ensureActive()
            val success = result["success"] as? Boolean ?: false
            if (!success) {
                val stderr = result["stderr"] as? String ?: ""
                val stdout = result["stdout"] as? String ?: ""
                val error = result["error"] as? String ?: ""
                android.util.Log.e("LinuxSandbox", "Failed to install $pkg for $distroId: stderr=$stderr error=$error")
                // Don't error out — continue with next packages
            }
        }
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
    )

    private fun createProotExecutorFor(rootfsDir: File): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsDir.absolutePath,
        homePath = homePath,
        tmpPath = tmpPath,
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
        currentJob = scope.launch {
            startSandboxForegroundService("Installing packages...")
            acquireSetupWakeLock()
            try {
                installSetupPackagesInternal(activeDistroId)
                _state.value = SandboxState.Ready
                postSandboxCompleteNotification("Packages installed")
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                // Transition to Ready so the UI shows "Install packages" button
                // instead of prompting re-download
                _state.value = SandboxState.Ready
            } finally {
                stopSandboxForegroundService()
                releaseSetupWakeLock()
            }
        }
    }

    /** Remove a specific distro's rootfs (used by UI "Remove" action). */
    fun resetDistro(distroId: String) {
        scope.launch {
            closeAllShells()
            val rootfs = rootfsDirFor(distroId)
            if (rootfs.exists()) rootfs.deleteRecursively()
            if (distroId == activeDistroId) {
                _state.value = SandboxState.NotInstalled
            }
        }
    }

    /** Remove all sandbox data. */
    fun reset() {
        scope.launch {
            closeAllShells()
            sandboxDir.deleteRecursively()
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

    /** Disk usage of the active distro's rootfs only. */
    fun getDiskUsageMB(): Long {
        val rootfs = rootfsDirFor(activeDistroId)
        if (!rootfs.isDirectory) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(rootfs)
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
                    }
                } catch (_: Throwable) { }
            }
        }
        return total / (1024 * 1024)
    }

    /** Check if packages are installed for the active distro. */
    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        val rootfs = rootfsDirFor(activeDistroId)
        val pkgManager = activeConfig.packageManager
        // Check for setup packages — all must be present
        return activeConfig.setupPackages.all { pkg ->
            val bin = File(rootfs, "usr/bin/$pkg")
            bin.exists() || File(rootfs, "bin/$pkg").exists()
        }
    }

    companion object {
        private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    }
}
