package com.oak.app.sandbox

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class DistroDownloadState {
    NotDownloaded,
    Downloading,
    Extracting,
    Ready,
    Error,
}

data class DistroState(
    val id: String,
    val displayName: String,
    val downloadState: DistroDownloadState = DistroDownloadState.NotDownloaded,
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
)

class ProotDistroManager(
    private val context: Context,
) {
    private val downloader = RootfsDownloader(HttpClient(Android))

    /** Base directory for all sandbox distros. */
    private val sandboxesBase: File
        get() = File(context.filesDir, "sandboxes")

    /** Old Alpine location (pre-multi-distro) for migration. */
    private val legacyAlpineDir: File
        get() = File(context.filesDir, "linux-sandbox")

    /** The active distro id, persisted in prefs (default alpine). */
    @Volatile
    var activeDistroId: String = "alpine"
        private set

    init {
        migrateLegacyAlpine()
        // Restore persisted preference — called after construction by setActiveDistroFromPrefs
    }

    /** Run once at init: move old linux-sandbox/rootfs to sandboxes/alpine/rootfs. */
    private fun migrateLegacyAlpine() {
        val oldRootfs = File(legacyAlpineDir, "rootfs")
        val newRootfs = rootfsDir("alpine")
        if (oldRootfs.isDirectory && !newRootfs.isDirectory) {
            try {
                newRootfs.parentFile?.mkdirs()
                oldRootfs.renameTo(newRootfs)
                // Also migrate tmp and home if they exist
                File(legacyAlpineDir, "tmp").takeIf { it.isDirectory }
                    ?.renameTo(File(sandboxesBase, "alpine/tmp"))
                File(legacyAlpineDir, "home").takeIf { it.isDirectory }
                    ?.renameTo(File(sandboxesBase, "alpine/home"))
            } catch (e: Exception) {
                android.util.Log.w("ProotDistro", "Legacy migration failed: ${e.message}")
            }
        }
    }

    /** Directory where a distro's rootfs lives. */
    fun rootfsDir(id: String): File = File(sandboxesBase, "$id/rootfs")

    /** Absolute path to a distro's rootfs. */
    fun rootfsPath(id: String): String = rootfsDir(id).absolutePath

    /** Absolute path to the active distro's rootfs. */
    val activeRootfsPath: String get() = rootfsPath(activeDistroId)

    /** The active [SandboxEnvironment]. */
    val activeEnvironment: SandboxEnvironment
        get() = SandboxEnvironment.fromId(activeDistroId)

    /** Check if a distro's rootfs is extracted and ready. */
    fun isDownloaded(id: String): Boolean {
        val dir = rootfsDir(id)
        return dir.isDirectory && dir.listFiles().orEmpty().isNotEmpty()
    }

    /** Check if the active distro is ready. */
    val isActiveReady: Boolean get() = isDownloaded(activeDistroId)

    /** Build the state snapshot for all distros. */
    fun getAllDistroStates(activeId: String = activeDistroId): List<DistroState> =
        SandboxEnvironment.ALL.map { env ->
            DistroState(
                id = env.id,
                displayName = env.displayName,
                downloadState = when {
                    isDownloaded(env.id) -> DistroDownloadState.Ready
                    else -> DistroDownloadState.NotDownloaded
                },
                isActive = env.id == activeId,
            )
        }

    /** Switch the active distro to [id]. Returns the [SandboxEnvironment]. */
    fun setActiveDistro(id: String): SandboxEnvironment {
        require(SandboxEnvironment.ALL.any { it.id == id }) { "Unknown distro: $id" }
        activeDistroId = id
        return SandboxEnvironment.fromId(id)
    }

    /**
     * Download and extract a distro rootfs. Suspends until complete.
     * @param onProgress called with 0..1 progress for download phase.
     */
    suspend fun download(id: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val env = SandboxEnvironment.fromId(id)
        val targetDir = rootfsDir(id)
        if (targetDir.isDirectory && targetDir.listFiles().orEmpty().isNotEmpty()) return@withContext

        targetDir.parentFile?.mkdirs()
        val arch = getLinuxArch()

        val tarGzFile = File(sandboxesBase, "$id/rootfs.tar.gz")
        tarGzFile.parentFile?.mkdirs()

        // Download
        downloader.download(env, arch, tarGzFile, onProgress)

        // Extract
        downloader.extractTarGz(tarGzFile, targetDir)
        tarGzFile.delete()

        // Make writable
        downloader.makeWritable(targetDir)

        // Write resolv.conf (distro-agnostic)
        downloader.writeResolvConf(targetDir)
    }

    /** Remove a downloaded distro's files. */
    fun remove(id: String) {
        val dir = File(sandboxesBase, id)
        if (dir.isDirectory) dir.deleteRecursively()
        // If this was the active distro, fall back to Alpine
        if (activeDistroId == id) {
            activeDistroId = SandboxEnvironment.DEFAULT.id
        }
    }

    /** Get the download URLs for a distro given current arch. */
    fun getDownloadUrls(id: String): List<String> {
        val env = SandboxEnvironment.fromId(id)
        return env.getDownloadUrls(getLinuxArch())
    }

    /** Get the Linux arch string for the current device. */
    private fun getLinuxArch(): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }
}
