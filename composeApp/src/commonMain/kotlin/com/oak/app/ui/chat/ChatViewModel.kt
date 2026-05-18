package com.oak.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oak.app.data.Conversation
import com.oak.app.data.DataRepository
import com.oak.app.data.FreeMode
import com.oak.app.data.Service
import com.oak.app.data.ServiceEntry
import com.oak.app.data.TaskScheduler
import com.oak.app.data.UiSubmission
import com.oak.app.getBackgroundDispatcher
import com.oak.app.network.toUiError
import com.oak.app.ui.markdown.OakUiBlock
import com.oak.app.ui.markdown.OakUiError
import com.oak.app.ui.markdown.parseMarkdown
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.conversation_untitled
import oak.composeapp.generated.resources.error_unsupported_file_type
import oak.composeapp.generated.resources.litert_no_model_warning
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class ChatViewModel(
    private val dataRepository: DataRepository,
    private val taskScheduler: TaskScheduler,
    private val sessionManager: ChatSessionManager,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val actions = ChatActions(
        ask = ::ask,
        retry = ::retry,
        toggleSpeechOutput = ::toggleSpeechOutput,
        clearHistory = ::clearHistory,
        setIsSpeaking = ::setIsSpeaking,
        addFile = ::addFile,
        removeFile = ::removeFile,
        startNewChat = ::startNewChat,
        regenerate = ::regenerate,
        cancel = ::cancel,
        selectService = ::selectService,
        loadConversation = ::loadConversation,
        deleteConversation = ::deleteConversation,
        clearUnreadHeartbeat = ::clearUnreadHeartbeat,
        clearSnackbar = ::clearSnackbar,
        undoDeleteConversation = ::undoDeleteConversation,
        submitUiCallback = ::submitUiCallback,
        resubmit = ::resubmit,
        enterInteractiveMode = ::enterInteractiveMode,
        exitInteractiveMode = ::exitInteractiveMode,
        goBackInteractiveMode = ::goBackInteractiveMode,
        sendSmsDraft = ::sendSmsDraft,
        discardSmsDraft = ::discardSmsDraft,
    )
    private val freeModeNames: Map<FreeMode, String> = FreeMode.entries.associateWith { "Free ${it.modelId.replaceFirstChar { c -> c.uppercase() }}" }
    @Volatile
    private var activeSessionId: String? = null
    private var pendingConversationDeleteJob: Job? = null
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            showPrivacyInfo = dataRepository.isUsingSharedKey(),
        ),
    )
    private var sendVersion = 0

    init {
        updateAvailableServices()

        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.loadConversations()
            dataRepository.restoreCurrentConversation()
            val convId = dataRepository.currentConversationId.value
            if (convId != null) {
                activeSessionId = convId
                sessionManager.getOrCreateSession(convId)
            }
            presetInteractiveModeForCurrentConversation()
            _state.update { it.copy(isRestoring = false) }
        }

        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.connectEnabledMcpServers()
        }
        viewModelScope.launch {
            dataRepository.fallbackStatus.collect { status ->
                _state.update { it.copy(fallbackStatus = status) }
            }
        }
        taskScheduler.isLoadingCheck = { _state.value.isLoading }
        taskScheduler.start()

        viewModelScope.launch {
            dataRepository.smsDrafts.collect { drafts ->
                _state.update { it.copy(smsDrafts = drafts.toImmutableList()) }
            }
        }

        viewModelScope.launch {
            dataRepository.openHeartbeatRequested
                .filter { it }
                .collect {
                    val heartbeatId = dataRepository.savedConversations.value
                        .firstOrNull { it.type == Conversation.TYPE_HEARTBEAT }?.id
                    if (heartbeatId != null) {
                        loadConversation(heartbeatId)
                        clearUnreadHeartbeat()
                    }
                    dataRepository.consumeOpenHeartbeatRequest()
                }
        }

        viewModelScope.launch {
            sessionManager.generatingSessionIds.collectLatest { ids ->
                _state.update { it.copy(generatingSessionIds = ids) }
            }
        }
    }

    val state = combine(
        _state,
        dataRepository.chatHistory,
        dataRepository.savedConversations,
        dataRepository.currentConversationId,
        dataRepository.hasUnreadHeartbeat,
        dataRepository.streamingReasoning,
        dataRepository.streamingContent,
    ) { state, history, conversations, conversationId, hasUnreadHeartbeat, streamingReasoning, streamingContent ->
        val summaries = conversations
            .sortedByDescending { it.updatedAt }
            .map {
                val isHeartbeat = it.type == Conversation.TYPE_HEARTBEAT
                val isInteractive = it.type == Conversation.TYPE_INTERACTIVE
                ConversationSummary(
                    id = it.id,
                    title = if (isHeartbeat) "" else it.title.ifEmpty { getString(Res.string.conversation_untitled) },
                    updatedAt = it.updatedAt,
                    isHeartbeat = isHeartbeat,
                    isInteractive = isInteractive,
                )
            }
        state.copy(
            history = history.toImmutableList(),
            supportedFileExtensions = dataRepository.supportedFileExtensions().toImmutableList(),
            savedConversations = summaries.toImmutableList(),
            currentConversationId = conversationId,
            hasUnreadHeartbeat = hasUnreadHeartbeat,
            streamingReasoning = streamingReasoning,
            streamingContent = streamingContent,
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun submitUiCallback(event: String, data: Map<String, String>) {
        val message = if (data.isNotEmpty()) {
            val formattedData = data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            "Responded with: $formattedData"
        } else {
            "Pressed: $event"
        }
        val lastAssistant = dataRepository.chatHistory.value.lastRenderedAssistant()
        val submission = lastAssistant?.let {
            UiSubmission(sourceContent = it.content, values = data, pressedEvent = event)
        }
        askInternal(message, submission)
    }

    private fun ask(question: String?) {
        askInternal(question, null)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun ensureActiveSession(): String {
        val existing = activeSessionId
        if (existing != null) return existing
        val id = Uuid.random().toString()
        activeSessionId = id
        sessionManager.getOrCreateSession(id)
        return id
    }

    private fun askInternal(question: String?, uiSubmission: UiSubmission?) {
        if (_state.value.isLoading) return
        val id = ensureActiveSession()

        // Only one session may generate at a time — concurrent askForConversation
        // calls for different sessions corrupt each other's context tracking.
        if (sessionManager.getGeneratingSessionIds().any { it != id }) {
            val otherId = sessionManager.getGeneratingSessionIds().first { it != id }
            val otherTitle = sessionManager.getSession(otherId)
                ?.conversation?.title?.ifEmpty { "another chat" } ?: "another chat"
            _state.update { it.copy(snackbarText = "Generation already in progress on $otherTitle") }
            return
        }

        val files = _state.value.files
        val interactiveModeAtSend = _state.value.isInteractiveMode

        sessionManager.setChatboxDraft(id, "")

        sendVersion++
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                files = persistentListOf(),
                sendVersion = sendVersion,
            )
        }

        sessionManager.startGeneration(id) {
            try {
                sessionManager.setSessionError(id, null)

                dataRepository.askForConversation(id, question, files, uiSubmission)

                // askForConversation restores the previous context in its finally
                // block — for a new chat this means _currentConversationId goes back
                // to null. Reload if the conversation was persisted under this ID.
                if (activeSessionId == id &&
                    dataRepository.currentConversationId.value != id &&
                    dataRepository.savedConversations.value.any { it.id == id }) {
                    dataRepository.loadConversation(id)
                }

                if (interactiveModeAtSend) {
                    dataRepository.loadConversation(id)
                    retryIfNoValidOakUi(id)
                    if (activeSessionId != id && activeSessionId != null) {
                        dataRepository.loadConversation(activeSessionId!!)
                    }
                }

                if (activeSessionId == id) {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (activeSessionId == id) {
                    _state.update { it.copy(error = e.toUiError(), isLoading = false) }
                } else {
                    sessionManager.setSessionError(id, e.toUiError())
                }
            }
        }
    }

    private suspend fun retryIfNoValidOakUi(sessionId: String, maxRetries: Int = 2) {
        repeat(maxRetries) {
            currentCoroutineContext().ensureActive()
            val lastAssistant = dataRepository.chatHistory.value.lastRenderedAssistant() ?: return

            val blocks = parseMarkdown(lastAssistant.content).blocks
            val hasValidUi = blocks.any { it is OakUiBlock }
            if (hasValidUi) return

            val errorBlock = blocks.filterIsInstance<OakUiError>().firstOrNull()
            val errorDetail = if (errorBlock != null) {
                "JSON parse error in: ${errorBlock.rawJson.take(200)}"
            } else {
                "No oak-ui code fence found in your response."
            }
            val retryMessage = "[SYSTEM] Your previous response failed to render as interactive UI. $errorDetail " +
                "Remember: respond with ONLY a single ```oak-ui code fence containing valid JSON. No text outside the fence."

            dataRepository.askForConversation(sessionId, retryMessage, emptyList())
        }
    }

    private fun clearHistory() {
        dataRepository.clearHistory()
        _state.update {
            it.copy(error = null)
        }
    }

    private fun setIsSpeaking(isSpeaking: Boolean, contentId: String) {
        _state.update {
            it.copy(
                isSpeaking = isSpeaking,
                isSpeakingContentId = if (isSpeaking) {
                    contentId
                } else {
                    it.isSpeakingContentId
                },
            )
        }
    }

    private fun addFile(file: PlatformFile) {
        val ext = file.extension.lowercase()
        val supported = dataRepository.supportedFileExtensions()
        if (ext.isEmpty() || ext !in supported) {
            _state.update {
                it.copy(snackbarMessage = Res.string.error_unsupported_file_type)
            }
            return
        }
        _state.update {
            it.copy(files = (it.files + file).toImmutableList())
        }
    }

    private fun removeFile(file: PlatformFile) {
        _state.update {
            it.copy(files = it.files.filterNot { f -> f == file }.toImmutableList())
        }
    }

    private fun clearSnackbar() {
        _state.update {
            it.copy(snackbarMessage = null, snackbarText = null)
        }
    }

    private fun retry() {
        ask(null)
    }

    private fun toggleSpeechOutput() {
        _state.update {
            it.copy(
                isSpeechOutputEnabled = !it.isSpeechOutputEnabled,
            )
        }
    }

    private fun cancel() {
        val id = activeSessionId
        if (id != null) sessionManager.cancelGeneration(id)
        _state.update {
            it.copy(isLoading = false)
        }
    }

    private fun selectService(instanceId: String) {
        val freeMode = FREE_MODE_INSTANCE_IDS[instanceId]
        if (freeMode != null) {
            dataRepository.setFreeMode(freeMode)
            dataRepository.setFreeServicePrimary(true)
            updateAvailableServices()
            return
        }

        dataRepository.setFreeServicePrimary(false)
        val instances = dataRepository.getConfiguredServiceInstances()
        val currentIds = instances.map { it.instanceId }
        if (instanceId !in currentIds) return
        val reordered = listOf(instanceId) + currentIds.filter { it != instanceId }
        dataRepository.reorderConfiguredServices(reordered)
        updateAvailableServices()
    }

    private fun updateAvailableServices() {
        val configuredEntries = dataRepository.getServiceEntries()
        val currentFreeMode = dataRepository.getFreeMode()
        val freeIsPrimary = dataRepository.isFreeServicePrimary() || configuredEntries.isEmpty()

        val freeModes = (listOf(currentFreeMode) + FreeMode.entries.filter { it != currentFreeMode }).map { mode ->
            ServiceEntry(
                instanceId = mode.instanceId,
                serviceId = Service.Free.id,
                serviceName = freeModeNames.getValue(mode),
                modelId = "",
                icon = mode.icon,
            )
        }

        val entries = if (freeIsPrimary) {
            freeModes + configuredEntries
        } else {
            configuredEntries + freeModes
        }.toImmutableList()

        val primaryService = entries.firstOrNull()?.let { Service.fromId(it.serviceId) }
        val warning = if (primaryService?.isOnDevice == true && dataRepository.getLocalDownloadedModels().isEmpty()) {
            Res.string.litert_no_model_warning
        } else {
            null
        }
        _state.update { it.copy(availableServices = entries, warning = warning, showPrivacyInfo = dataRepository.isUsingSharedKey()) }
    }

    companion object {
        private val FREE_MODE_INSTANCE_IDS = FreeMode.entries.associateBy { it.instanceId }
    }

    private fun regenerate() {
        val id = ensureActiveSession()
        sessionManager.cancelGeneration(id)
        dataRepository.regenerate()
        ask(null)
    }

    private fun loadConversation(id: String) {
        val conversation = dataRepository.savedConversations.value.find { it.id == id }
        val isInteractive = conversation?.type == Conversation.TYPE_INTERACTIVE
        dataRepository.setInteractiveMode(isInteractive)
        dataRepository.loadConversation(id)

        // If loadConversation was deferred (generation in progress for another
        // session), currentConversationId won't match — alert the user.
        if (dataRepository.currentConversationId.value != id) {
            val currentId = dataRepository.currentConversationId.value
            val title = currentId?.let { sessionManager.getSession(it)?.conversation?.title?.ifEmpty { null } }
                ?: "current conversation"
            _state.update { it.copy(snackbarText = "Please wait — generation in progress on $title") }
            return
        }

        activeSessionId = id
        val session = sessionManager.getOrCreateSession(id)
        _state.update {
            it.copy(
                error = session.lastError,
                isInteractiveMode = isInteractive,
                isLoading = id in _state.value.generatingSessionIds,
            )
        }
    }

    private fun deleteConversation(id: String) {
        commitPendingConversationDeletion()
        _state.update { it.copy(pendingConversationDeletion = id) }
        pendingConversationDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            dataRepository.deleteConversation(id)
            sessionManager.removeSession(id)
            _state.update { it.copy(pendingConversationDeletion = null) }
        }
    }

    private fun undoDeleteConversation() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        _state.update { it.copy(pendingConversationDeletion = null) }
    }

    private fun commitPendingConversationDeletion() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        val pendingId = _state.value.pendingConversationDeletion ?: return
        _state.update { it.copy(pendingConversationDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteConversation(pendingId)
            sessionManager.removeSession(pendingId)
        }
    }

    override fun onCleared() {
        commitPendingConversationDeletion()
        taskScheduler.isLoadingCheck = { false }
        super.onCleared()
    }

    private fun clearUnreadHeartbeat() {
        dataRepository.clearUnreadHeartbeat()
    }

    private fun sendSmsDraft(draftId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.sendSmsDraft(draftId)
        }
    }

    private fun discardSmsDraft(draftId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.discardSmsDraft(draftId)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun startNewChat() {
        val prevId = activeSessionId
        if (prevId != null) {
            sessionManager.cancelGeneration(prevId)
            sessionManager.removeSession(prevId)
        }
        val id = Uuid.random().toString()
        activeSessionId = id
        sessionManager.getOrCreateSession(id)
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(error = null, isInteractiveMode = false, isLoading = false)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun enterInteractiveMode() {
        val id = Uuid.random().toString()
        activeSessionId = id
        sessionManager.getOrCreateSession(id)
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(true)
        _state.update {
            it.copy(isInteractiveMode = true, error = null)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun exitInteractiveMode() {
        val prevId = activeSessionId
        if (prevId != null) {
            sessionManager.cancelGeneration(prevId)
            sessionManager.removeSession(prevId)
        }
        val id = Uuid.random().toString()
        activeSessionId = id
        sessionManager.getOrCreateSession(id)
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(isInteractiveMode = false, isLoading = false, error = null)
        }
    }

    private fun resubmit(messageId: String, event: String, data: Map<String, String>) {
        if (_state.value.isLoading) return
        dataRepository.truncateFrom(messageId)
        submitUiCallback(event, data)
    }

    private fun goBackInteractiveMode() {
        val userCount = dataRepository.chatHistory.value.count { it.role == History.Role.USER }
        if (userCount <= 1) {
            dataRepository.clearHistory()
        } else {
            dataRepository.popLastExchange()
        }
    }

    fun getDraft(): String {
        return sessionManager.getChatboxDraft(ensureActiveSession())
    }

    fun saveDraft(text: String) {
        sessionManager.setChatboxDraft(ensureActiveSession(), text)
    }

    fun refreshSettings() {
        updateAvailableServices()
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.restoreCurrentConversation()
            presetInteractiveModeForCurrentConversation()
        }
    }

    private fun presetInteractiveModeForCurrentConversation() {
        val currentId = dataRepository.currentConversationId.value
        val conversation = dataRepository.savedConversations.value.find { it.id == currentId }
        val isInteractive = if (conversation != null) {
            conversation.type == Conversation.TYPE_INTERACTIVE
        } else {
            dataRepository.isInteractiveModeActive()
        }
        dataRepository.setInteractiveMode(isInteractive)
        _state.update { it.copy(isInteractiveMode = isInteractive) }
    }
}
