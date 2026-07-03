package com.oak.app.ui.chat

import com.oak.app.data.Conversation
import com.oak.app.data.DataRepository
import com.oak.app.getBackgroundDispatcher
import com.oak.app.network.UiError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ChatSessionManager(
    private val dataRepository: DataRepository,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) {
    // Scope on Main for thread-safe session map access; generation block
    // switches to backgroundDispatcher internally via withContext.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineName("ChatSessionManager") + crashHandler,
    )

    private val sessions = mutableMapOf<String, ChatSession>()
    private val generationCounter = mutableMapOf<String, Int>()

    private val _generatingSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val generatingSessionIds: StateFlow<Set<String>> = _generatingSessionIds.asStateFlow()

    fun getOrCreateSession(id: String): ChatSession = sessions.getOrPut(id) {
        val conversation = dataRepository.savedConversations.value.find { it.id == id }
        ChatSession(
            conversationId = id,
            conversation = conversation ?: Conversation(
                id = id,
                messages = emptyList(),
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    fun startGeneration(sessionId: String, block: suspend () -> Unit) {
        val session = sessions[sessionId] ?: return
        session.generationJob?.cancel()

        val genId = (generationCounter[sessionId] ?: 0) + 1
        generationCounter[sessionId] = genId

        updateSession(sessionId) { it.copy(isGenerating = true, generationJob = null) }
        refreshGeneratingIds()

        val job = scope.launch {
            try {
                withContext(backgroundDispatcher) {
                    block()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("ChatSessionManager: unhandled exception in generation job for session $sessionId: ${e.message}")
            } finally {
                if (generationCounter[sessionId] == genId) {
                    generationCounter.remove(sessionId)
                    updateSession(sessionId) { it.copy(isGenerating = false, generationJob = null) }
                    refreshGeneratingIds()
                }
            }
        }

        updateSession(sessionId) { it.copy(generationJob = job) }
    }

    fun cancelGeneration(sessionId: String) {
        val session = sessions[sessionId] ?: return
        session.generationJob?.cancel()
        updateSession(sessionId) { it.copy(isGenerating = false, generationJob = null) }
        refreshGeneratingIds()
    }

    fun setChatboxDraft(sessionId: String, draft: String) {
        updateSession(sessionId) { it.copy(chatboxDraft = draft) }
    }

    fun getChatboxDraft(sessionId: String): String = sessions[sessionId]?.chatboxDraft ?: ""

    fun getGeneratingSessionIds(): Set<String> = sessions.filter { it.value.isGenerating }.keys

    fun setSessionError(sessionId: String, error: UiError?) {
        updateSession(sessionId) { it.copy(lastError = error) }
    }

    fun removeSession(id: String) {
        sessions[id]?.generationJob?.cancel()
        sessions.remove(id)
        generationCounter.remove(id)
        refreshGeneratingIds()
    }

    fun getSession(id: String): ChatSession? = sessions[id]

    private fun updateSession(sessionId: String, transform: (ChatSession) -> ChatSession) {
        val current = sessions[sessionId] ?: return
        sessions[sessionId] = transform(current)
    }

    private fun refreshGeneratingIds() {
        _generatingSessionIds.value = sessions.filter { it.value.isGenerating }.keys
    }

    companion object {
        /**
         * Catches a known Android OkHttp `AsyncTimeout` bug where
         * `IllegalStateException("Unbalanced enter/exit")` is thrown from inside a Ktor
         * cleanup handler when a streaming `callbackFlow` is cancelled. This is a framework
         * bug, not a genuine app crash, so we log and swallow it.
         */
        private val crashHandler = CoroutineExceptionHandler { _, throwable ->
            if (throwable.isAndroidOkHttpCrash()) {
                println("ChatSessionManager: suppressed Android OkHttp AsyncTimeout crash: $throwable")
            } else {
                throw RuntimeException("Unhandled coroutine exception in ChatSessionManager", throwable)
            }
        }
    }
}

/**
 * Checks whether the throwable chain originates from Android OkHttp's `AsyncTimeout`
 * throwing `IllegalStateException("Unbalanced enter/exit")`. This is a known Android
 * framework bug triggered when Ktor's `attachToUserJob` cleanup handler closes the
 * response stream during coroutine cancellation.
 */
private fun Throwable.isAndroidOkHttpCrash(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is IllegalStateException && cause.message == "Unbalanced enter/exit") {
            return true
        }
        cause = cause.cause
    }
    return false
}
