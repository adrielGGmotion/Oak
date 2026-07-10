package com.oak.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oak.app.DaemonController
import com.oak.app.Platform
import com.oak.app.currentPlatform
import com.oak.app.data.DataRepository
import com.oak.app.data.ImportSection
import com.oak.app.data.OakFontFamily
import com.oak.app.data.Service
import com.oak.app.data.TaskScheduler
import com.oak.app.data.ThemeMode
import com.oak.app.data.supportsAgenticFlows
import com.oak.app.getBackgroundDispatcher
import com.oak.app.inference.LocalModel
import com.oak.app.isEmailSupported
import com.oak.app.isNotificationsSupported
import com.oak.app.isSmsSupported
import com.oak.app.mcp.PopularMcpServer
import com.oak.app.network.AnthropicInsufficientCreditsException
import com.oak.app.network.AnthropicInvalidApiKeyException
import com.oak.app.network.AnthropicOverloadedException
import com.oak.app.network.AnthropicRateLimitExceededException
import com.oak.app.network.GeminiInvalidApiKeyException
import com.oak.app.network.GeminiRateLimitExceededException
import com.oak.app.network.OpenAICompatibleConnectionException
import com.oak.app.network.OpenAICompatibleInvalidApiKeyException
import com.oak.app.network.OpenAICompatibleQuotaExhaustedException
import com.oak.app.network.OpenAICompatibleRateLimitExceededException
import com.oak.app.ssh.SshAuthType
import com.oak.app.ssh.SshConnectionStatus
import com.oak.app.tools.NotificationPermissionController
import com.oak.app.tools.StoragePermissionController
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val FRONTMATTER_REGEX = Regex("^---\\n(.*?)\\n---", RegexOption.DOT_MATCHES_ALL)
private val FRONTMATTER_NAME_REGEX = Regex("name:\\s*(.+)")
private val FRONTMATTER_DESC_REGEX = Regex("description:\\s*(.+)")
private val FILE_NAME_SEPARATOR_REGEX = Regex("[-_]")

class SettingsViewModel(
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
    private val notificationPermissionController: NotificationPermissionController,
    val storagePermissionController: StoragePermissionController,
    private val taskScheduler: TaskScheduler,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private val pendingDeleteJobs: MutableMap<KClass<out PendingDeletion>, Job> = mutableMapOf()

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        soulText = dataRepository.getSoulText(),
        isDynamicUiEnabled = dataRepository.isDynamicUiEnabled(),
        themeMode = dataRepository.getThemeMode(),
        useDynamicColors = dataRepository.isUseDynamicColorsEnabled(),
        showDynamicColorsToggle = currentPlatform is Platform.Mobile.Android,
        fontFamily = dataRepository.getFontFamily(),
        aiFontFamily = dataRepository.getAiFontFamily(),
        isStorageAccessEnabled = dataRepository.isStorageAccessEnabled(),
        storagePermissionGranted = storagePermissionController.hasPermission(),
        isMemoryEnabled = dataRepository.isMemoryEnabled(),
        memories = dataRepository.getMemories().toImmutableList(),
        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
        scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(),
        isDaemonEnabled = dataRepository.isDaemonEnabled(),
        showDaemonToggle = currentPlatform is Platform.Mobile.Android,
        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
        heartbeatIntervalMinutes = dataRepository.getHeartbeatConfig().intervalMinutes,
        heartbeatActiveHoursStart = dataRepository.getHeartbeatConfig().activeHoursStart,
        heartbeatActiveHoursEnd = dataRepository.getHeartbeatConfig().activeHoursEnd,
        heartbeatPrompt = dataRepository.getHeartbeatPrompt(),
        heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
        heartbeatServiceEntries = dataRepository.getServiceEntries()
            .filter { supportsAgenticFlows(it.serviceId, it.modelId) }
            .toImmutableList(),
        heartbeatSelectedInstanceId = dataRepository.getHeartbeatInstanceId()?.takeIf { id ->
            dataRepository.getServiceEntries().any { it.instanceId == id }
        }.also { validId ->
            val savedId = dataRepository.getHeartbeatInstanceId()
            if (savedId != null && validId == null) dataRepository.setHeartbeatInstanceId(null)
        },
        isEmailEnabled = dataRepository.isEmailEnabled(),
        showEmailToggle = isEmailSupported,
        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
        emailPollIntervalMinutes = dataRepository.getEmailPollIntervalMinutes(),
        emailPendingCount = dataRepository.getPendingEmailCount(),
        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
        showSmsSection = isSmsSupported,
        isSmsEnabled = dataRepository.isSmsEnabled(),
        smsPermissionGranted = dataRepository.hasSmsPermission(),
        smsPollIntervalMinutes = dataRepository.getSmsPollIntervalMinutes(),
        smsPendingCount = dataRepository.getPendingSmsCount(),
        smsSyncState = dataRepository.getSmsSyncState(),
        isSmsSendEnabled = dataRepository.isSmsSendEnabled(),
        smsSendPermissionGranted = dataRepository.hasSmsSendPermission(),
        showNotificationsSection = isNotificationsSupported,
        isNotificationsEnabled = dataRepository.isNotificationsEnabled(),
        notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
        notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
        notificationPendingCount = dataRepository.getPendingNotificationCount(),
        isStreamingEnabled = dataRepository.isStreamingEnabled(),
        isUnlimitedToolCallsEnabled = dataRepository.isUnlimitedToolCallsEnabled(),
        uiScale = dataRepository.getUiScale(),
        showUiScale = currentPlatform is Platform.Desktop,
        mcpServers = buildMcpServerEntries().toImmutableList(),
        sshServers = buildSshServerEntries().toImmutableList(),
        skills = buildSkillEntries().toImmutableList(),
        localActiveBackend = dataRepository.getLocalActiveBackend()?.value,
        backendPreference = dataRepository.getBackendPreference(),
        localAvailableModels = dataRepository.getLocalAvailableModels().toImmutableList(),
        totalDeviceMemoryBytes = dataRepository.getTotalDeviceMemoryBytes(),
        localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes(),
        localDownloadingModelId = dataRepository.getLocalDownloadingModelId()?.value,
        localDownloadProgress = dataRepository.getLocalDownloadProgress()?.value,
        modelContextTokens = buildModelContextTokensMap(),
    )

    // Bound once so downstream Compose skipping works — a new SettingsActions
    // instance on every state emission would defeat it.
    val actions: SettingsActions = SettingsActions(
        onSelectTab = ::onSelectTab,
        onAddService = ::onAddService,
        onRemoveService = ::onRemoveService,
        onReorderServices = ::onReorderServices,
        onExpandService = ::onExpandService,
        onChangeApiKey = ::onChangeApiKey,
        onChangeBaseUrl = ::onChangeBaseUrl,
        onSelectModel = ::onSelectModel,
        onToggleTool = ::onToggleTool,
        onSaveSoul = ::onSaveSoul,
        onToggleDynamicUi = ::onToggleDynamicUi,
        onChangeThemeMode = ::onChangeThemeMode,
        onChangeFontFamily = ::onChangeFontFamily,
        onChangeAiFontFamily = ::onChangeAiFontFamily,
        onToggleDynamicColors = ::onToggleDynamicColors,
        onToggleMemory = ::onToggleMemory,
        onDeleteMemory = ::onDeleteMemory,
        onUpdateMemory = ::onUpdateMemory,
        onToggleScheduling = ::onToggleScheduling,
        onCancelTask = ::onCancelTask,
        onToggleDaemon = ::onToggleDaemon,
        onToggleHeartbeat = ::onToggleHeartbeat,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = ::onChangeHeartbeatService,
        onRefreshHeartbeat = ::onRefreshHeartbeat,
        onToggleEmail = ::onToggleEmail,
        onRemoveEmailAccount = ::onRemoveEmailAccount,
        onChangeEmailPollInterval = ::onChangeEmailPollInterval,
        onRefreshEmailAccount = ::onRefreshEmailAccount,
        onToggleSms = ::onToggleSms,
        onChangeSmsPollInterval = ::onChangeSmsPollInterval,
        onRefreshSms = ::onRefreshSms,
        onToggleSmsSend = ::onToggleSmsSend,
        onToggleNotifications = ::onToggleNotifications,
        onOpenNotificationListenerSettings = ::onOpenNotificationListenerSettings,
        onClearPendingNotifications = ::onClearPendingNotifications,
        onToggleStorageAccess = ::onToggleStorageAccess,
        onToggleStreaming = ::onToggleStreaming,
        onToggleUnlimitedToolCalls = ::onToggleUnlimitedToolCalls,
        onChangeUiScale = ::onChangeUiScale,
        onAddMcpServer = ::onAddMcpServer,
        onRemoveMcpServer = ::onRemoveMcpServer,
        onToggleMcpServer = ::onToggleMcpServer,
        onRefreshMcpServer = ::onRefreshMcpServer,
        onShowAddMcpServerDialog = ::onShowAddMcpServerDialog,
        onAddPopularMcpServer = ::onAddPopularMcpServer,
        onAddSshServer = ::onAddSshServer,
        onRemoveSshServer = ::onRemoveSshServer,
        onToggleSshServer = ::onToggleSshServer,
        onConnectSshServer = ::onConnectSshServer,
        onShowAddSshServerDialog = ::onShowAddSshServerDialog,
        onToggleSkill = ::onToggleSkill,
        onRemoveSkill = ::onRemoveSkill,
        onImportSkill = ::onImportSkill,
        onShowImportSkillDialog = ::onShowImportSkillDialog,
        onImportSkillFromFile = ::onImportSkillFromFile,
        onSkillFilePicked = ::onSkillFilePicked,
        onEditSkill = ::onEditSkill,
        onShowEditSkillDialog = ::onShowEditSkillDialog,
        onResetSkill = ::onResetSkill,
        onDownloadLocalModel = ::onDownloadLocalModel,
        onCancelLocalModelDownload = ::onCancelLocalModelDownload,
        onDeleteLocalModel = ::onDeleteLocalModel,
        onChangeModelContextTokens = ::onChangeModelContextTokens,
        onChangeBackendPreference = ::onChangeBackendPreference,
        onExportSettings = ::onExportSettings,
        onPrepareExport = ::onPrepareExport,
        onImportSettings = ::onImportSettings,
        onUndoDelete = ::onUndoDelete,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    init {
        // Observe download state from the engine singleton (survives activity recreation)
        val downloadingFlow = dataRepository.getLocalDownloadingModelId() ?: flowOf(null)
        val progressFlow = dataRepository.getLocalDownloadProgress() ?: flowOf(null)
        val errorFlow = dataRepository.getLocalDownloadError() ?: flowOf(null)
        val backendFlow = dataRepository.getLocalActiveBackend() ?: flowOf(null)
        viewModelScope.launch {
            combine(downloadingFlow, progressFlow, errorFlow) { modelId, progress, error ->
                Triple(modelId, progress, error)
            }.collect { (modelId, progress, error) ->
                val wasDownloading = _state.value.localDownloadingModelId != null
                _state.update {
                    it.copy(
                        localDownloadingModelId = modelId,
                        localDownloadProgress = progress,
                        localDownloadError = error,
                    )
                }
                if (modelId == null && wasDownloading) {
                    // Download finished or cancelled — refresh
                    _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    refreshServiceList()
                    _state.value.configuredServices
                        .filter { it.service.isOnDevice }
                        .forEach { checkConnection(it.instanceId, it.service) }
                }
            }
        }
        viewModelScope.launch {
            backendFlow.collect { backend ->
                _state.update { it.copy(localActiveBackend = backend) }
            }
        }
    }

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkAllConnections()
            connectEnabledMcpServers()
            viewModelScope.launch(backgroundDispatcher) {
                val enabledServers = dataRepository.getSshServers().filter { it.isEnabled }
                enabledServers
                    .filterNot { dataRepository.isSshServerConnected(it.id) }
                    .forEach { server -> connectSshServerWithStatus(server.id) }
            }
        }
        // Re-read notification listener state every time the screen becomes visible:
        // the user may have toggled access in system settings while we were backgrounded.
        if (isNotificationsSupported) {
            _state.update {
                it.copy(
                    notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
                    notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
                    notificationPendingCount = dataRepository.getPendingNotificationCount(),
                )
            }
        }
    }

    private fun buildConfiguredServiceEntries(): List<ConfiguredServiceEntry> = dataRepository.getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val models = dataRepository.getInstanceModels(instance.instanceId, service).value
        ConfiguredServiceEntry(
            instanceId = instance.instanceId,
            service = service,
            apiKey = dataRepository.getInstanceApiKey(instance.instanceId),
            baseUrl = dataRepository.getInstanceBaseUrl(instance.instanceId, service),
            selectedModel = models.firstOrNull { it.isSelected },
            models = models.toImmutableList(),
        )
    }

    private fun computeAvailableServices(): List<Service> {
        // Allow all services (multiple instances of same type are allowed)
        // Pin OpenAI-Compatible and LiteRT (Local Model) to the top, then sort the rest alphabetically
        // Hide on-device services on platforms that don't support them
        return Service.all
            .filter { !it.isOnDevice || dataRepository.isLocalInferenceAvailable() }
            .sortedWith(compareBy<Service> { !(it is Service.OpenAICompatible || it.isOnDevice) }.thenBy { it.displayName })
    }

    private fun refreshServiceList() {
        _state.update { current ->
            val existingStatuses = current.configuredServices.associate { it.instanceId to it.connectionStatus }
            val newEntries = buildConfiguredServiceEntries().map { entry ->
                val preservedStatus = existingStatuses[entry.instanceId]
                if (preservedStatus != null) entry.copy(connectionStatus = preservedStatus) else entry
            }
            current.copy(
                configuredServices = newEntries.toImmutableList(),
                availableServicesToAdd = computeAvailableServices().toImmutableList(),
            )
        }
    }

    private fun onSelectTab(tab: SettingsTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    private fun onAddService(service: Service) {
        val instance = dataRepository.addConfiguredService(service.id)
        refreshServiceList()
        _state.update { it.copy(expandedServiceId = instance.instanceId) }
        checkConnection(instance.instanceId, service)
    }

    private fun onRemoveService(instanceId: String) {
        commitPendingDeletion(PendingDeletion.Service::class)
        _state.update {
            it.copy(
                expandedServiceId = if (it.expandedServiceId == instanceId) null else it.expandedServiceId,
                pendingDeletion = PendingDeletion.Service(instanceId),
            )
        }
        pendingDeleteJobs[PendingDeletion.Service::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Service(instanceId))
        }
    }

    private fun onReorderServices(orderedIds: List<String>) {
        dataRepository.reorderConfiguredServices(orderedIds)
        refreshServiceList()
    }

    private fun onExpandService(instanceId: String?) {
        _state.update { it.copy(expandedServiceId = instanceId) }
        if (instanceId != null) {
            refreshInstanceModels(instanceId)
        }
    }

    private fun refreshInstanceModels(instanceId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        val models = dataRepository.getInstanceModels(instanceId, entry.service).value
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(
                            models = models.toImmutableList(),
                            selectedModel = models.firstOrNull { it.isSelected },
                        )
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onChangeApiKey(instanceId: String, apiKey: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceApiKey(instanceId, apiKey)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(apiKey = apiKey, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onChangeBaseUrl(instanceId: String, baseUrl: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceBaseUrl(instanceId, baseUrl)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(baseUrl = baseUrl, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onSelectModel(instanceId: String, modelId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceSelectedModel(instanceId, entry.service, modelId)
        refreshInstanceModels(instanceId)
    }

    private fun onSaveSoul(text: String) {
        dataRepository.setSoulText(text)
        _state.update { it.copy(soulText = text) }
    }

    private fun onToggleDynamicUi(enabled: Boolean) {
        dataRepository.setDynamicUiEnabled(enabled)
        _state.update { it.copy(isDynamicUiEnabled = enabled) }
    }

    private fun onChangeThemeMode(mode: ThemeMode) {
        dataRepository.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    private fun onChangeFontFamily(family: OakFontFamily) {
        dataRepository.setFontFamily(family)
        _state.update { it.copy(fontFamily = family) }
    }

    private fun onChangeAiFontFamily(family: OakFontFamily) {
        dataRepository.setAiFontFamily(family)
        _state.update { it.copy(aiFontFamily = family) }
    }

    private fun onToggleDynamicColors(enabled: Boolean) {
        dataRepository.setUseDynamicColorsEnabled(enabled)
        _state.update { it.copy(useDynamicColors = enabled) }
    }

    private fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _state.update { it.copy(isMemoryEnabled = enabled) }
    }

    private fun onDeleteMemory(key: String) {
        commitPendingDeletion(PendingDeletion.Memory::class)
        _state.update { it.copy(pendingDeletion = PendingDeletion.Memory(key)) }
        pendingDeleteJobs[PendingDeletion.Memory::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Memory(key))
        }
    }

    private fun onUpdateMemory(key: String, content: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.updateMemoryContent(key, content)
            _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList()) }
        }
    }

    private fun onToggleScheduling(enabled: Boolean) {
        dataRepository.setSchedulingEnabled(enabled)
        _state.update { it.copy(isSchedulingEnabled = enabled) }
    }

    private fun onCancelTask(id: String) {
        commitPendingDeletion(PendingDeletion.Task::class)
        _state.update { it.copy(pendingDeletion = PendingDeletion.Task(id)) }
        pendingDeleteJobs[PendingDeletion.Task::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Task(id))
        }
    }

    private fun onToggleDaemon(enabled: Boolean) {
        dataRepository.setDaemonEnabled(enabled)
        if (enabled) {
            viewModelScope.launch { notificationPermissionController.requestPermission() }
            daemonController.start()
        } else {
            daemonController.stop()
        }
        _state.update { it.copy(isDaemonEnabled = enabled) }
    }

    private fun onToggleHeartbeat(enabled: Boolean) {
        dataRepository.setHeartbeatEnabled(enabled)
        _state.update { it.copy(isHeartbeatEnabled = enabled) }
    }

    private fun onChangeHeartbeatInterval(minutes: Int) {
        dataRepository.setHeartbeatIntervalMinutes(minutes)
        _state.update { it.copy(heartbeatIntervalMinutes = minutes) }
    }

    private fun onChangeHeartbeatActiveHours(start: Int, end: Int) {
        dataRepository.setHeartbeatActiveHours(start, end)
        _state.update { it.copy(heartbeatActiveHoursStart = start, heartbeatActiveHoursEnd = end) }
    }

    private fun onSaveHeartbeatPrompt(text: String) {
        dataRepository.setHeartbeatPrompt(text)
        _state.update { it.copy(heartbeatPrompt = text) }
    }

    private fun onChangeHeartbeatService(instanceId: String?) {
        dataRepository.setHeartbeatInstanceId(instanceId)
        _state.update { it.copy(heartbeatSelectedInstanceId = instanceId) }
    }

    private fun onRefreshHeartbeat() {
        if (_state.value.isRefreshingHeartbeat) return
        _state.update { it.copy(isRefreshingHeartbeat = true) }
        viewModelScope.launch(backgroundDispatcher) {
            taskScheduler.triggerHeartbeatNow()
            _state.update {
                it.copy(
                    isRefreshingHeartbeat = false,
                    heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
                )
            }
        }
    }

    private fun onToggleEmail(enabled: Boolean) {
        dataRepository.setEmailEnabled(enabled)
        _state.update { it.copy(isEmailEnabled = enabled) }
    }

    private fun onRemoveEmailAccount(id: String) {
        commitPendingDeletion(PendingDeletion.EmailAccount::class)
        _state.update { it.copy(pendingDeletion = PendingDeletion.EmailAccount(id)) }
        pendingDeleteJobs[PendingDeletion.EmailAccount::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.EmailAccount(id))
        }
    }

    private fun onChangeEmailPollInterval(minutes: Int) {
        dataRepository.setEmailPollIntervalMinutes(minutes)
        _state.update { it.copy(emailPollIntervalMinutes = minutes) }
    }

    private fun onRefreshEmailAccount(id: String) {
        if (id in _state.value.refreshingEmailAccountIds) return
        _state.update { it.copy(refreshingEmailAccountIds = (it.refreshingEmailAccountIds + id).toPersistentSet()) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollEmailAccount(id)
            _state.update {
                it.copy(
                    refreshingEmailAccountIds = (it.refreshingEmailAccountIds - id).toPersistentSet(),
                    emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                    emailPendingCount = dataRepository.getPendingEmailCount(),
                )
            }
        }
    }

    private fun onToggleSms(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsPermission()) {
            // Ask for the OS permission first; only flip the toggle on if it's granted.
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsPermission()
                _state.update { it.copy(smsPermissionGranted = granted, isSmsEnabled = granted) }
                if (granted) {
                    dataRepository.setSmsEnabled(true)
                    // First poll seeds lastSeenId to the current inbox max, so the AI
                    // isn't drowned in historical messages on opt-in.
                    dataRepository.pollSms()
                    _state.update {
                        it.copy(
                            smsSyncState = dataRepository.getSmsSyncState(),
                            smsPendingCount = dataRepository.getPendingSmsCount(),
                        )
                    }
                }
            }
        } else {
            dataRepository.setSmsEnabled(enabled)
            _state.update { it.copy(isSmsEnabled = enabled) }
        }
    }

    private fun onChangeSmsPollInterval(minutes: Int) {
        dataRepository.setSmsPollIntervalMinutes(minutes)
        _state.update { it.copy(smsPollIntervalMinutes = minutes) }
    }

    private fun onRefreshSms() {
        if (_state.value.isRefreshingSms) return
        _state.update { it.copy(isRefreshingSms = true) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollSms()
            _state.update {
                it.copy(
                    isRefreshingSms = false,
                    smsSyncState = dataRepository.getSmsSyncState(),
                    smsPendingCount = dataRepository.getPendingSmsCount(),
                    smsPermissionGranted = dataRepository.hasSmsPermission(),
                )
            }
        }
    }

    private fun onToggleSmsSend(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsSendPermission()) {
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsSendPermission()
                _state.update { it.copy(smsSendPermissionGranted = granted, isSmsSendEnabled = granted) }
                if (granted) dataRepository.setSmsSendEnabled(true)
            }
        } else {
            dataRepository.setSmsSendEnabled(enabled)
            _state.update { it.copy(isSmsSendEnabled = enabled) }
        }
    }

    private fun onToggleStorageAccess(enabled: Boolean) {
        if (enabled && !storagePermissionController.hasPermission()) {
            viewModelScope.launch(backgroundDispatcher) {
                val granted = storagePermissionController.requestPermission()
                _state.update { it.copy(storagePermissionGranted = granted, isStorageAccessEnabled = granted) }
                if (granted) {
                    dataRepository.setStorageAccessEnabled(true)
                }
            }
        } else {
            dataRepository.setStorageAccessEnabled(enabled)
            _state.update {
                it.copy(
                    isStorageAccessEnabled = enabled,
                    storagePermissionGranted = storagePermissionController.hasPermission(),
                )
            }
        }
    }

    private fun onToggleNotifications(enabled: Boolean) {
        // Listener access is granted via system Settings, not a runtime permission
        // dialog. Set the toggle, then if access is missing, deep-link the user out
        // so they can enable Oak there. The toggle reflects the user's *intent*; the
        // listener still drops everything until access is granted.
        dataRepository.setNotificationsEnabled(enabled)
        _state.update {
            it.copy(
                isNotificationsEnabled = enabled,
                notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
            )
        }
        if (enabled && !dataRepository.isNotificationListenerAccessGranted()) {
            dataRepository.openNotificationListenerSettings()
        }
    }

    private fun onOpenNotificationListenerSettings() {
        dataRepository.openNotificationListenerSettings()
    }

    private fun onClearPendingNotifications() {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.clearPendingNotifications()
            _state.update { it.copy(notificationPendingCount = 0) }
        }
    }

    private fun onToggleStreaming(enabled: Boolean) {
        dataRepository.setStreamingEnabled(enabled)
        _state.update { it.copy(isStreamingEnabled = enabled) }
    }

    private fun onToggleUnlimitedToolCalls(enabled: Boolean) {
        dataRepository.setUnlimitedToolCallsEnabled(enabled)
        _state.update { it.copy(isUnlimitedToolCallsEnabled = enabled) }
    }

    private fun onDownloadLocalModel(model: LocalModel) {
        dataRepository.startLocalModelDownload(model)
    }

    private fun onCancelLocalModelDownload() {
        dataRepository.cancelLocalModelDownload()
    }

    private fun onChangeModelContextTokens(modelId: String, contextTokens: Int) {
        if (_state.value.modelContextTokens[modelId] == contextTokens) return
        dataRepository.setModelContextTokens(modelId, contextTokens)
        _state.update {
            it.copy(modelContextTokens = it.modelContextTokens.toMutableMap().apply { put(modelId, contextTokens) }.toImmutableMap())
        }
        // Release engine so the next message re-initializes with the new context size
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.releaseLocalEngine()
        }
    }

    private fun buildModelContextTokensMap() = dataRepository.getLocalAvailableModels().associate { model ->
        val stored = dataRepository.getModelContextTokens(model.id)
        model.id to if (stored > 0) stored else model.defaultContextTokens
    }.toImmutableMap()

    private fun onChangeBackendPreference(pref: String) {
        dataRepository.setBackendPreference(pref)
        _state.update { it.copy(backendPreference = pref) }
    }

    private fun onDeleteLocalModel(modelId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteLocalModel(modelId)
            _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
            refreshServiceList()
            _state.value.configuredServices
                .filter { it.service.isOnDevice }
                .forEach { checkConnection(it.instanceId, it.service) }
        }
    }

    private fun onChangeUiScale(scale: Float) {
        dataRepository.setUiScale(scale)
        _state.update { it.copy(uiScale = scale) }
    }

    private fun onExportSettings(sections: Set<ImportSection>): String = dataRepository.exportSettingsToJson(sections)

    private fun onPrepareExport(): Map<ImportSection, String?> = dataRepository.getExportPreview()

    private fun onImportSettings(bytes: ByteArray, sections: Set<ImportSection>, replace: Boolean): ImportResult = try {
        val currentTab = _state.value.currentTab
        val errors = dataRepository.importSettingsFromJson(bytes.decodeToString(), sections, replace)
        _state.value = buildFullState().copy(currentTab = currentTab)
        checkAllConnections()
        connectEnabledMcpServers()
        if (errors == 0) ImportResult.Success else ImportResult.PartialSuccess(errors)
    } catch (_: Exception) {
        ImportResult.Failure
    }

    private fun onToggleTool(toolId: String, enabled: Boolean) {
        dataRepository.setToolEnabled(toolId, enabled)
        _state.update { state ->
            state.copy(
                tools = state.tools.map { tool ->
                    if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                }.toImmutableList(),
                mcpServers = state.mcpServers.map { server ->
                    server.copy(
                        tools = server.tools.map { tool ->
                            if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                        }.toImmutableList(),
                    )
                }.toImmutableList(),
            )
        }
    }

    // Skill management

    private fun buildSkillEntries(): List<SkillUiState> = dataRepository.getSkills().map { skill ->
        val isModified = if (skill.isBuiltIn) {
            val original = com.oak.app.data.Skill.BUILT_IN_SKILLS.find { it.id == skill.id }
            original != null && (
                skill.name != original.name ||
                    skill.description != original.description ||
                    skill.content != original.content ||
                    skill.requiredTools != original.requiredTools
                )
        } else {
            false
        }
        SkillUiState(
            id = skill.id,
            name = skill.name,
            description = skill.description,
            content = skill.content,
            isEnabled = skill.isEnabled,
            isBuiltIn = skill.isBuiltIn,
            requiredTools = skill.requiredTools,
            isModified = isModified,
        )
    }

    private fun onToggleSkill(skillId: String, enabled: Boolean) {
        dataRepository.setSkillEnabled(skillId, enabled)
        _state.update { state ->
            state.copy(
                skills = state.skills.map { skill ->
                    if (skill.id == skillId) skill.copy(isEnabled = enabled) else skill
                }.toImmutableList(),
            )
        }
    }

    private fun onRemoveSkill(skillId: String) {
        pendingDeleteJobs[PendingDeletion.Skill::class]?.cancel()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Skill(skillId)) }
        pendingDeleteJobs[PendingDeletion.Skill::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(5.seconds)
            dataRepository.removeSkill(skillId)
            _state.update { state ->
                state.copy(
                    skills = state.skills.filter { it.id != skillId }.toImmutableList(),
                    pendingDeletion = null,
                )
            }
            pendingDeleteJobs.remove(PendingDeletion.Skill::class)
        }
    }

    private fun onImportSkill(name: String, description: String, content: String, requiredTools: List<String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val existingIds = dataRepository.getSkills().map { it.id }.toSet()
            val id = com.oak.app.tools.generateSkillIdFromName(name, existingIds)
            val skill = com.oak.app.data.Skill(
                id = id,
                name = name,
                description = description,
                content = content,
                requiredTools = requiredTools,
                isBuiltIn = false,
                isEnabled = true,
                source = com.oak.app.data.SkillSource.USER,
            )
            dataRepository.importSkill(skill)
            _state.update { state ->
                state.copy(
                    skills = buildSkillEntries().toImmutableList(),
                    showImportSkillDialog = false,
                )
            }
        }
    }

    private fun onShowImportSkillDialog(show: Boolean) {
        _state.update { it.copy(showImportSkillDialog = show, importSkillPrefill = null) }
    }

    private fun onImportSkillFromFile() {
        _state.update { it.copy(importSkillPrefill = null) }
    }

    private fun onSkillFilePicked(content: ByteArray, fileName: String) {
        val text = content.decodeToString()
        val parsed = parseSkillFile(text, fileName)
        _state.update {
            it.copy(
                importSkillPrefill = parsed.copy(requestId = System.nanoTime()),
                showImportSkillDialog = true,
            )
        }
    }

    private fun parseSkillFile(fileContent: String, fileName: String): ImportSkillPrefill {
        val normalizedContent = fileContent.replace("\r\n", "\n")
        val frontmatterMatch = FRONTMATTER_REGEX.find(normalizedContent)
        if (frontmatterMatch != null) {
            val frontmatter = frontmatterMatch.groupValues[1]
            val name = FRONTMATTER_NAME_REGEX.find(frontmatter)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"") ?: ""
            val description = FRONTMATTER_DESC_REGEX.find(frontmatter)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"") ?: ""
            val body = normalizedContent.substring(frontmatterMatch.range.last + 1).trim()
            return ImportSkillPrefill(
                name = name,
                description = description,
                content = body,
            )
        }
        val nameFromFileName = fileName
            .substringBeforeLast(".")
            .replace(FILE_NAME_SEPARATOR_REGEX, " ")
            .trim()
        return ImportSkillPrefill(
            name = nameFromFileName,
            content = normalizedContent.trim(),
        )
    }

    private fun onEditSkill(id: String, name: String, description: String, content: String, requiredTools: List<String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val existingSkill = dataRepository.getSkills().find { it.id == id }
            val skill = com.oak.app.data.Skill(
                id = id,
                name = name,
                description = description,
                content = content,
                requiredTools = requiredTools,
                isBuiltIn = existingSkill?.isBuiltIn ?: false,
                isEnabled = existingSkill?.isEnabled ?: true,
                source = existingSkill?.source ?: com.oak.app.data.SkillSource.USER,
            )
            dataRepository.importSkill(skill)
            _state.update { state ->
                state.copy(
                    skills = buildSkillEntries().toImmutableList(),
                    showEditSkillDialog = false,
                    editingSkillId = null,
                )
            }
        }
    }

    private fun onShowEditSkillDialog(skillId: String?) {
        _state.update {
            it.copy(
                showEditSkillDialog = skillId != null,
                editingSkillId = skillId,
            )
        }
    }

    private fun onResetSkill(skillId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            val original = com.oak.app.data.Skill.BUILT_IN_SKILLS.find { it.id == skillId }
            if (original != null) {
                dataRepository.importSkill(original)
                _state.update { state ->
                    state.copy(
                        skills = buildSkillEntries().toImmutableList(),
                        showEditSkillDialog = false,
                        editingSkillId = null,
                    )
                }
            }
        }
    }

    // MCP server management
    private fun buildMcpServerEntries(): List<McpServerUiState> = dataRepository.getMcpServers().map { config ->
        McpServerUiState(
            id = config.id,
            name = config.name,
            url = config.url,
            isEnabled = config.isEnabled,
            connectionStatus = if (dataRepository.isMcpServerConnected(config.id)) {
                McpConnectionStatus.Connected
            } else {
                McpConnectionStatus.Unknown
            },
            tools = dataRepository.getMcpToolsForServer(config.id).toImmutableList(),
        )
    }

    private fun refreshMcpServers() {
        _state.update { current ->
            val existingStatuses = current.mcpServers.associate { it.id to it.connectionStatus }
            current.copy(
                mcpServers = buildMcpServerEntries().map { entry ->
                    val preservedStatus = existingStatuses[entry.id]
                    // Only preserve transient statuses (Connecting/Error) — derive Connected/Unknown from actual state
                    if (preservedStatus == McpConnectionStatus.Connecting || preservedStatus == McpConnectionStatus.Error) {
                        entry.copy(connectionStatus = preservedStatus)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onAddMcpServer(name: String, url: String, headers: Map<String, String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val config = dataRepository.addMcpServer(name, url, headers)
            refreshMcpServers()
            connectMcpServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddMcpServerDialog = false) }
    }

    private fun onRemoveMcpServer(serverId: String) {
        commitPendingDeletion(PendingDeletion.McpServer::class)
        _state.update { it.copy(pendingDeletion = PendingDeletion.McpServer(serverId)) }
        pendingDeleteJobs[PendingDeletion.McpServer::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.McpServer(serverId))
        }
    }

    private fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        dataRepository.setMcpServerEnabled(serverId, enabled)
        refreshMcpServers()
        if (enabled) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(serverId)
            }
        }
    }

    private fun onRefreshMcpServer(serverId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            connectMcpServerWithStatus(serverId)
        }
    }

    private fun onShowAddMcpServerDialog(show: Boolean) {
        _state.update { it.copy(showAddMcpServerDialog = show) }
    }

    private fun onAddPopularMcpServer(server: PopularMcpServer) {
        onAddMcpServer(server.name, server.url, emptyMap())
    }

    private suspend fun connectMcpServerWithStatus(serverId: String) {
        updateMcpConnectionStatus(serverId, McpConnectionStatus.Connecting)
        val result = dataRepository.connectMcpServer(serverId)
        if (result.isSuccess) {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Connected)
            refreshMcpServers()
        } else {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Error)
        }
    }

    private fun updateMcpConnectionStatus(serverId: String, status: McpConnectionStatus) {
        _state.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map { entry ->
                    if (entry.id == serverId) entry.copy(connectionStatus = status) else entry
                }.toImmutableList(),
            )
        }
    }

    private fun connectEnabledMcpServers() {
        val enabledServers = _state.value.mcpServers.filter { it.isEnabled && it.connectionStatus != McpConnectionStatus.Connected }
        for (server in enabledServers) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(server.id)
            }
        }
    }

    // SSH server management
    private fun buildSshServerEntries(): List<SshServerUiState> = dataRepository.getSshServers().map { config ->
        SshServerUiState(
            id = config.id,
            name = config.name,
            host = config.host,
            port = config.port,
            username = config.username,
            authType = config.authType,
            isEnabled = config.isEnabled,
            connectionStatus = if (dataRepository.isSshServerConnected(config.id)) {
                SshConnectionStatus.Connected
            } else {
                SshConnectionStatus.Disconnected
            },
        )
    }

    private fun refreshSshServers() {
        _state.update { current ->
            val existingState = current.sshServers.associateBy { it.id }
            current.copy(
                sshServers = buildSshServerEntries().map { entry ->
                    val existing = existingState[entry.id]
                    if (existing != null && (existing.connectionStatus == SshConnectionStatus.Connecting || existing.connectionStatus == SshConnectionStatus.Error)) {
                        entry.copy(connectionStatus = existing.connectionStatus, errorMessage = existing.errorMessage)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onAddSshServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        privateKey: String,
        passphrase: String,
        authType: SshAuthType,
    ) {
        viewModelScope.launch(backgroundDispatcher) {
            val config = dataRepository.addSshServer(name, host, port, username, authType, password, privateKey, passphrase)
            refreshSshServers()
            connectSshServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddSshServerDialog = false) }
    }

    private fun onRemoveSshServer(serverId: String) {
        commitPendingDeletion(PendingDeletion.SshServer::class)
        _state.update { it.copy(pendingDeletion = PendingDeletion.SshServer(serverId)) }
        pendingDeleteJobs[PendingDeletion.SshServer::class] = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.SshServer(serverId))
        }
    }

    private fun onToggleSshServer(serverId: String, enabled: Boolean) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.setSshServerEnabled(serverId, enabled)
            refreshSshServers()
            if (enabled) {
                connectSshServerWithStatus(serverId)
            }
        }
    }

    private fun onConnectSshServer(serverId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            connectSshServerWithStatus(serverId)
        }
    }

    private fun onShowAddSshServerDialog(show: Boolean) {
        _state.update { it.copy(showAddSshServerDialog = show) }
    }

    private suspend fun connectSshServerWithStatus(serverId: String) {
        updateSshConnectionStatus(serverId, SshConnectionStatus.Connecting)
        val result = dataRepository.connectSshServer(serverId)
        if (result.isSuccess) {
            updateSshConnectionStatus(serverId, SshConnectionStatus.Connected)
            refreshSshServers()
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Connection failed"
            updateSshConnectionStatus(serverId, SshConnectionStatus.Error, errorMessage)
        }
    }

    private fun updateSshConnectionStatus(serverId: String, status: SshConnectionStatus, errorMessage: String? = null) {
        _state.update { state ->
            state.copy(
                sshServers = state.sshServers.map { entry ->
                    if (entry.id == serverId) entry.copy(connectionStatus = status, errorMessage = errorMessage) else entry
                }.toImmutableList(),
            )
        }
    }

    private fun commitPendingDeletion(type: KClass<out PendingDeletion>) {
        pendingDeleteJobs[type]?.cancel()
        pendingDeleteJobs.remove(type)
        val deletion = _state.value.pendingDeletion ?: return
        if (deletion::class != type) return
        _state.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            executeDeletion(deletion)
        }
    }

    private suspend fun executeDeletion(deletion: PendingDeletion) {
        when (deletion) {
            is PendingDeletion.Memory -> {
                dataRepository.deleteMemory(deletion.key)
                _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList(), pendingDeletion = null) }
            }

            is PendingDeletion.Task -> {
                dataRepository.cancelScheduledTask(deletion.id)
                _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(), pendingDeletion = null) }
            }

            is PendingDeletion.EmailAccount -> {
                dataRepository.removeEmailAccount(deletion.id)
                _state.update {
                    it.copy(
                        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
                        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                        emailPendingCount = dataRepository.getPendingEmailCount(),
                        pendingDeletion = null,
                    )
                }
            }

            is PendingDeletion.Service -> {
                val service = _state.value.configuredServices.find { it.instanceId == deletion.instanceId }?.service
                dataRepository.removeConfiguredService(deletion.instanceId)
                // If removing the last on-device service, delete all downloaded models
                if (service?.isOnDevice == true) {
                    val hasOtherOnDevice = dataRepository.getConfiguredServiceInstances().any {
                        Service.fromId(it.serviceId).isOnDevice
                    }
                    if (!hasOtherOnDevice) {
                        dataRepository.getLocalDownloadedModels().forEach {
                            dataRepository.deleteLocalModel(it.id)
                        }
                        _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    }
                }
                _state.update { it.copy(pendingDeletion = null) }
                refreshServiceList()
            }

            is PendingDeletion.McpServer -> {
                dataRepository.removeMcpServer(deletion.serverId)
                _state.update { it.copy(pendingDeletion = null) }
                refreshMcpServers()
            }

            is PendingDeletion.SshServer -> {
                dataRepository.removeSshServer(deletion.serverId)
                _state.update { it.copy(pendingDeletion = null) }
                refreshSshServers()
            }

            is PendingDeletion.Skill -> {
                dataRepository.removeSkill(deletion.skillId)
                _state.update { it.copy(skills = buildSkillEntries().toImmutableList(), pendingDeletion = null) }
            }
        }
    }

    private fun onUndoDelete() {
        pendingDeleteJobs.values.forEach { it.cancel() }
        pendingDeleteJobs.clear()
        _state.update { it.copy(pendingDeletion = null) }
    }

    override fun onCleared() {
        pendingDeleteJobs.values.forEach { it.cancel() }
        pendingDeleteJobs.clear()
        val deletion = _state.value.pendingDeletion ?: run {
            super.onCleared()
            return
        }
        _state.update { it.copy(pendingDeletion = null) }
        CoroutineScope(backgroundDispatcher).launch {
            executeDeletion(deletion)
        }
        super.onCleared()
    }

    private fun checkAllConnections() {
        for (entry in _state.value.configuredServices) {
            checkConnection(entry.instanceId, entry.service)
        }
    }

    private fun checkConnectionDebounced(instanceId: String, service: Service) {
        connectionCheckJobs[instanceId]?.cancel()
        connectionCheckJobs[instanceId] = viewModelScope.launch {
            delay(800.milliseconds)
            checkConnection(instanceId, service)
        }
    }

    private fun checkConnection(instanceId: String, service: Service) {
        if (service.isOnDevice) {
            validateConnectionWithStatus(instanceId, service)
            return
        }
        if (service.requiresApiKey && dataRepository.getInstanceApiKey(instanceId).isBlank()) {
            updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
            return
        }
        validateConnectionWithStatus(instanceId, service)
    }

    private fun updateConnectionStatus(instanceId: String, status: ConnectionStatus) {
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { entry ->
                    if (entry.instanceId == instanceId) {
                        entry.copy(connectionStatus = status)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun validateConnectionWithStatus(instanceId: String, service: Service) {
        updateConnectionStatus(instanceId, ConnectionStatus.Checking)
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.validateConnection(service, instanceId)
                if (service.isOnDevice && dataRepository.getLocalDownloadedModels().isEmpty()) {
                    updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
                } else {
                    updateConnectionStatus(instanceId, ConnectionStatus.Connected)
                }
                refreshInstanceModels(instanceId)
            } catch (e: Exception) {
                val status = when (e) {
                    is OpenAICompatibleInvalidApiKeyException, is GeminiInvalidApiKeyException, is AnthropicInvalidApiKeyException ->
                        ConnectionStatus.ErrorInvalidKey

                    is OpenAICompatibleQuotaExhaustedException, is AnthropicInsufficientCreditsException ->
                        ConnectionStatus.ErrorQuotaExhausted

                    is OpenAICompatibleRateLimitExceededException, is GeminiRateLimitExceededException, is AnthropicRateLimitExceededException ->
                        ConnectionStatus.ErrorRateLimited

                    is AnthropicOverloadedException ->
                        ConnectionStatus.Error

                    is OpenAICompatibleConnectionException ->
                        ConnectionStatus.ErrorConnectionFailed

                    else -> ConnectionStatus.Error
                }
                updateConnectionStatus(instanceId, status)
            }
        }
    }
}
