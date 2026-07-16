package com.oak.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatSystemPromptBuilderTest {

    private val runtime = ChatPromptRuntimeContext(
        nowLocalIsoWithOffset = "2026-04-11T02:00:00+02:00",
        timeZoneId = "Europe/Berlin",
        nowUtcIsoString = "2026-04-11T00:00:00Z",
        platform = "Test",
        modelId = "test-model",
        providerName = "Test Provider",
    )

    private fun memory(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        hitCount: Int = 1,
    ) = MemoryEntry(
        key = key,
        content = content,
        createdAt = 0L,
        updatedAt = 0L,
        category = category,
        hitCount = hitCount,
    )

    private fun task(
        id: String = "task-1",
        description: String = "Do the thing",
        scheduledAtEpochMs: Long = 0L,
        cron: String? = null,
    ) = ScheduledTask(
        id = id,
        description = description,
        prompt = "",
        scheduledAtEpochMs = scheduledAtEpochMs,
        createdAtEpochMs = 0L,
        cron = cron,
    )

    // ── buildChatSystemPrompt (lean system prompt) ────────────────────────

    @Test
    fun `CHAT_REMOTE default emits soul + tool guidance + context`() {
        val out = buildChatSystemPrompt(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You are Oak.",
            memoryInstructions = null,
            runtime = runtime,
            uiMode = ChatPromptUiMode.NONE,
        )
        assertTrue(out.startsWith("You are Oak."))
        assertTrue("## Tool Use" in out)
        assertTrue("## Context" in out)
        assertTrue("- Provider: Test Provider" in out)
    }

    @Test
    fun `CHAT_REMOTE includes memory instructions when provided`() {
        val out = buildChatSystemPrompt(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You are Oak.",
            memoryInstructions = "Use memory_store to save user info.",
            runtime = runtime,
            uiMode = ChatPromptUiMode.NONE,
        )
        assertTrue("Use memory_store to save user info." in out)
    }

    @Test
    fun `CHAT_LOCAL omits tool guidance`() {
        val out = buildChatSystemPrompt(
            variant = SystemPromptVariant.CHAT_LOCAL,
            soul = "You are Oak.",
            memoryInstructions = null,
            runtime = runtime,
            uiMode = ChatPromptUiMode.NONE,
        )
        assertFalse("## Tool Use" in out)
        assertTrue("## Context" in out)
    }

    // ── buildMemoryPrefixMessage ──────────────────────────────────────────

    @Test
    fun `memory prefix includes all categories`() {
        val out = buildMemoryPrefixMessage(
            generalMemories = listOf(memory("user_name", "Alice")),
            preferenceMemories = listOf(memory("tone", "concise", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("lesson", "body", category = MemoryCategory.LEARNING, hitCount = 3)),
            errorMemories = listOf(memory("fix", "retry", category = MemoryCategory.ERROR)),
        )
        assertNotNull(out)
        val s = out!!
        assertTrue("## Known Information" in s)
        assertTrue("- **user_name**: Alice" in s)
        assertTrue("- **tone**: concise" in s)
        assertTrue("- **lesson** (reinforced 3x): body" in s)
        assertTrue("- **fix**: retry" in s)
    }

    @Test
    fun `memory prefix returns null when no memories`() {
        assertEquals(null, buildMemoryPrefixMessage(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    // ── buildSkillPrefixMessage ───────────────────────────────────────────

    @Test
    fun `skill prefix includes enabled skill content`() {
        val skill = Skill(
            id = "test_skill",
            name = "Test Skill",
            description = "A test skill",
            content = "## Test Instructions\nBe helpful.",
            isEnabled = true,
        )
        val out = buildSkillPrefixMessage(listOf(skill))
        assertNotNull(out)
        val s = out!!
        assertTrue("## Active Skills" in s)
        assertTrue("## Test Instructions" in s)
    }

    @Test
    fun `skill prefix returns null when no enabled skills`() {
        val skill = Skill(
            id = "test_skill",
            name = "Test Skill",
            description = "A test skill",
            content = "Hidden",
            isEnabled = false,
        )
        assertEquals(null, buildSkillPrefixMessage(listOf(skill)))
    }

    // ── buildTaskPrefixMessage ────────────────────────────────────────────

    @Test
    fun `task prefix includes scheduled tasks`() {
        val out = buildTaskPrefixMessage(
            pendingTasks = listOf(task(id = "t1", description = "Morning check", cron = "0 9 * * *")),
            heartbeatAdditions = emptyList(),
        )
        assertNotNull(out)
        val s = out!!
        assertTrue("## Active Tasks" in s)
        assertTrue("- **Morning check**" in s)
    }

    @Test
    fun `task prefix returns null when no tasks`() {
        assertEquals(null, buildTaskPrefixMessage(emptyList(), emptyList()))
    }

    @Test
    fun `task prefix includes heartbeat additions`() {
        val heartbeat = ScheduledTask(
            id = "h1", description = "Greeting", prompt = "Hi!",
            scheduledAtEpochMs = 0L, createdAtEpochMs = 0L,
            trigger = TaskTrigger.HEARTBEAT,
        )
        val out = buildTaskPrefixMessage(
            pendingTasks = emptyList(),
            heartbeatAdditions = listOf(heartbeat),
        )
        assertNotNull(out)
        assertTrue("Heartbeat Additions" in out!!)
        // No need to extract since only one assertion on the value
    }
}
