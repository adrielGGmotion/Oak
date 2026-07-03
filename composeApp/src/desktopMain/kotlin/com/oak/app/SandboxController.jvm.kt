package com.oak.app

import com.oak.app.data.StorageVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createSandboxController(): SandboxController = NoOpSandboxController()

class NoOpSandboxController : SandboxController {
    override val status: StateFlow<SandboxStatus> = MutableStateFlow(SandboxStatus())
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}

    override fun getActiveDistroId(): String = "alpine"
    override fun setActiveDistro(distroId: String) {}
    override fun getAllDistroStates(): List<DistroState> = emptyList()
    override fun downloadDistro(distroId: String) {}
    override fun removeDistro(distroId: String) {}

    override fun getPackageInstallCmd(): String = "apk add --no-cache"
    override fun getPackageUninstallCmd(): String = "apk del"
    override fun getPackageSearchCmd(): String = "apk search -v"
    override fun getPackageListInstalledCmd(): String = "apk info -v | sort"
    override fun getPackageUpdateCmd(): String = "apk update"
    override fun getPackageUpgradeCmd(): String = "apk upgrade"

    override suspend fun executeCommand(command: String, sessionId: String): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override fun getStorageVolumes(): List<StorageVolume> = emptyList()
    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
}
