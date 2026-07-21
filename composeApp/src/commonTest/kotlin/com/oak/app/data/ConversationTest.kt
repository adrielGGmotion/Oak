package com.oak.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConversationTest {

    @Test
    fun `conversation defaults`() {
        val conv = Conversation(
            id = "conv-1",
            messages = emptyList(),
            createdAt = 0L,
            updatedAt = 0L,
        )

        assertEquals("conv-1", conv.id)
        assertEquals("", conv.title)
        assertEquals(Conversation.TYPE_CHAT, conv.type)
        assertNotNull(conv.shellTranscript)
        assertNotNull(conv.excludedSkillIds)
    }

    @Test
    fun `conversation with messages`() {
        val msg = Conversation.Message(
            id = "msg-1",
            role = "user",
            content = "Hello",
        )
        val conv = Conversation(
            id = "conv-1",
            messages = listOf(msg),
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        assertEquals(1, conv.messages.size)
        assertEquals("user", conv.messages[0].role)
        assertEquals("Hello", conv.messages[0].content)
    }

    @Test
    fun `message supports attachment`() {
        val attachment = Attachment(
            data = "base64data",
            mimeType = "image/png",
            fileName = "screenshot.png",
        )
        val msg = Conversation.Message(
            id = "msg-1",
            role = "user",
            content = "Here is a screenshot",
            attachments = listOf(attachment),
        )

        assertEquals(1, msg.attachments.size)
        assertEquals("image/png", msg.attachments[0].mimeType)
        assertEquals("screenshot.png", msg.attachments[0].fileName)
    }

    @Test
    fun `message supports optional fields`() {
        val msg = Conversation.Message(
            id = "msg-1",
            role = "assistant",
            content = "Thinking...",
            reasoningContent = "Let me think about this",
            isThinking = true,
        )

        assertEquals("Let me think about this", msg.reasoningContent)
        assertEquals(true, msg.isThinking)
    }

    @Test
    fun `message with tool calls`() {
        val toolCall = Conversation.ToolCallInfoData(
            id = "call-1",
            name = "web_search",
            arguments = "{\"query\": \"test\"}",
        )
        val msg = Conversation.Message(
            id = "msg-1",
            role = "assistant",
            content = "",
            toolCalls = listOf(toolCall),
        )

        assertEquals(1, msg.toolCalls?.size)
        assertEquals("web_search", msg.toolCalls?.get(0)?.name)
    }

    @Test
    fun `UiSubmission stores values`() {
        val submission = UiSubmission(
            sourceContent = "What color?",
            values = mapOf("color" to "blue"),
            pressedEvent = "submit_color",
        )

        assertEquals("What color?", submission.sourceContent)
        assertEquals("blue", submission.values["color"])
        assertEquals("submit_color", submission.pressedEvent)
    }

    @Test
    fun `conversation types are defined`() {
        assertEquals("chat", Conversation.TYPE_CHAT)
        assertEquals("heartbeat", Conversation.TYPE_HEARTBEAT)
        assertEquals("interactive", Conversation.TYPE_INTERACTIVE)
    }
}
