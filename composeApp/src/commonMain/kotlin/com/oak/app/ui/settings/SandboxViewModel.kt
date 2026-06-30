package com.oak.app.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oak.app.DistroInfo
import com.oak.app.Platform
import com.oak.app.SandboxController
import com.oak.app.SandboxStatus
import com.oak.app.currentPlatform
import com.oak.app.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SandboxUiState(
    val showSandbox: Boolean = false,
    val sandboxInstalled: Boolean = false,
    val sandboxReady: Boolean = false,
    val sandboxProgress: Float? = null,
    val sandboxStatusText: String = "",
    val sandboxDiskUsageMB: Long = 0,
    val sandboxPackagesInstalled: Boolean = false,
    val isSandboxEnabled: Boolean = true,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    val distros: List<DistroInfo> = emptyList(),
    val activeDistroName: String = "Alpine Linux",
)

class SandboxViewModel(
    private val dataRepository: DataRepository,
    private val sandboxController: SandboxController,
) : ViewModel() {

    // Seed synchronously from the controller's current status so the first
    // composition doesn't briefly render the install UI when the sandbox is
    // already ready. The controller mirrors LinuxSandboxManager's synchronous
    // installation check, so reading status.value here returns the real state.
    private val _state = MutableStateFlow(
        applyStatus(
            sandboxController.status.value,
            SandboxUiState(
                showSandbox = currentPlatform is Platform.Mobile.Android,
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
                distros = sandboxController.getDistros(),
                activeDistroName = sandboxController.getActiveDistroName(),
            ),
        ),
    )

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sandboxController.status.collect { sandboxStatus ->
                _state.update {
                    applyStatus(sandboxStatus, it).copy(
                        distros = sandboxController.getDistros(),
                        activeDistroName = sandboxController.getActiveDistroName(),
                    )
                }
            }
        }
    }

    private fun applyStatus(status: SandboxStatus, base: SandboxUiState): SandboxUiState = base.copy(
        sandboxInstalled = status.installed,
        sandboxReady = status.ready,
        sandboxProgress = status.progress,
        sandboxStatusText = status.statusText,
        sandboxDiskUsageMB = status.diskUsageMB,
        sandboxPackagesInstalled = status.packagesInstalled,
        isWorking = status.working,
        hasError = status.error,
    )

    fun refreshDistros() {
        _state.update {
            it.copy(
                distros = sandboxController.getDistros(),
                activeDistroName = sandboxController.getActiveDistroName(),
            )
        }
    }

    fun onToggleSandbox(enabled: Boolean) {
        dataRepository.setSandboxEnabled(enabled)
        _state.update { it.copy(isSandboxEnabled = enabled) }
    }

    fun onSetupSandbox() {
        sandboxController.setup()
    }

    fun onCancelSandbox() {
        sandboxController.cancel()
    }

    fun onResetSandbox() {
        sandboxController.reset()
    }

    fun onInstallPackages() {
        sandboxController.installPackages()
    }

    fun onSelectDistro(id: String) {
        sandboxController.setActiveDistro(id)
        refreshDistros()
    }

    fun onDownloadDistro(id: String) {
        sandboxController.downloadDistro(id)
    }

    fun onRemoveDistro(id: String) {
        sandboxController.removeDistro(id)
        refreshDistros()
    }
}
