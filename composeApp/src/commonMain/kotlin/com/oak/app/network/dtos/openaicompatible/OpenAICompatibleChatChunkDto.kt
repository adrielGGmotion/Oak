package com.oak.app.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleChatChunkDto(
    val choices: List<Choice>? = null,
) {
    @Serializable
    data class Choice(
        val delta: Delta,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class Delta(
        val content: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
    )

    @Serializable
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val type: String? = null,
        val function: FunctionDelta? = null,
    )

    @Serializable
    data class FunctionDelta(
        val name: String? = null,
        val arguments: String? = null,
    )

    companion object {
        const val DONE_MARKER = "[DONE]"
    }
}
