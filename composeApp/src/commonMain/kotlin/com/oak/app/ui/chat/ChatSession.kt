package com.oak.app.ui.chat

import com.oak.app.data.Conversation
import com.oak.app.network.UiError
import kotlinx.coroutines.Job

data class ChatSession(
    val conversationId: String,
    val conversation: Conversation,
    val chatboxDraft: String = "",
    val isGenerating: Boolean = false,
    val generationJob: Job? = null,
    val lastError: UiError? = null,
)
