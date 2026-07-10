@file:OptIn(kotlin.time.ExperimentalTime::class)

// Pure builders for the chat system prompt. Every input is passed explicitly — no DI,
// no suspend, no resource loading, no Clock — so tests can call `buildChatSystemPrompt`
// directly with hand-crafted inputs. Section composition is controlled by
// `SystemPromptVariant`; each `if (variant == ...)` block is the single source of
// truth for where a section belongs. No post-hoc regex stripping.

package com.oak.app.data

/**
 * Identifies which flavour of chat system prompt to build. Public because it's part of
 * [DataRepository.getActiveSystemPrompt]'s signature — callers pick the variant based on
 * whether they're dispatching to a remote or on-device service.
 */
enum class SystemPromptVariant {
    /** Full chat prompt for remote services — every section available. */
    CHAT_REMOTE,

    /**
     * Trimmed prompt for on-device LiteRT plain chat — only sections a 2-4B Gemma can
     * coherently attend to. Soul + basic memory guidance + runtime `## Context`. Drops
     * memory category dumps, scheduled tasks, Structured Learning guidance, and oak-ui
     * modes (the latter is also hidden from the UI for on-device services — see
     * `ChatScreen.kt`).
     */
    CHAT_LOCAL,
}

/** Runtime state rendered into the `## Context` section. */
internal data class ChatPromptRuntimeContext(
    /** Local-zoned ISO 8601 with explicit offset, e.g. `2026-04-22T22:32:39+02:00`. */
    val nowLocalIsoWithOffset: String,
    /** IANA zone id, e.g. `Europe/Berlin`. Rendered next to the local time for clarity. */
    val timeZoneId: String,
    /** UTC ISO 8601 with `Z` suffix — kept so the model can double-check the offset. */
    val nowUtcIsoString: String,
    val platform: String,
    val modelId: String,
    val providerName: String,
)

/** Which oak-ui section, if any, to render. */
internal enum class ChatPromptUiMode { NONE, DYNAMIC_UI, INTERACTIVE_UI }

/**
 * Shared shape for rendering a connected email account into a prompt section — used by
 * both the chat `## Email Accounts` block and the heartbeat `## Email Status` block.
 * Carries enough context for the AI to reason about account state (unread, last sync,
 * last error). Message bodies/previews don't belong here; those are surfaced separately
 * by the heartbeat's `## New Emails` section or fetched via the email-reading tools.
 */
internal data class EmailAccountSummary(
    val email: String,
    val unreadCount: Int,
    val lastSyncEpochMs: Long,
    val lastError: String? = null,
)

/**
 * Total character budget for the memory category sections (`## Your Memories`, etc.)
 * when building the `CHAT_LOCAL` variant. Memories are appended in order — general →
 * preferences → learnings → errors — and entries that would push the combined size
 * past this budget are dropped silently at the entry boundary. Removed the cap
 * (was 800) — modern local models handle larger context windows without issue.
 */
private const val LOCAL_MEMORY_BUDGET_CHARS = Int.MAX_VALUE

/**
 * Tells remote models that tools are handled through the API's structured
 * mechanism, so they don't fall back to inline `<tool_call>` or `<invoke>` blocks.
 * Only composed into the `CHAT_REMOTE` variant — on-device models get separate
 * instructions from `buildLocalToolPrompt()`.
 */
internal const val DEFAULT_TOOL_CALL_GUIDANCE =
    "## Tool Use\n" +
        "All available tools are provided to you through the API's structured `tools` parameter. " +
        "Use the API's native structured tool invocation mechanism to call them — do not output " +
        "inline tool call markup (such as `<tool_call>`, `<invoke>`, or `<TOOLCALL>` blocks) " +
        "in your text responses. If you need a tool, call it through the API; your text responses " +
        "should contain only natural language and, when appropriate, ```oak-ui blocks for " +
        "interactive UI."

/**
 * Composes the full chat system prompt for the given [variant].
 *
 * Returns an empty string when there is literally nothing to render (which the caller
 * should map to `null`). All inputs are passed explicitly — memory lists are pre-split
 * by the caller so this function doesn't touch the `MemoryStore`.
 */
internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    memoryInstructions: String?,
    generalMemories: List<MemoryEntry>,
    preferenceMemories: List<MemoryEntry>,
    learningMemories: List<MemoryEntry>,
    errorMemories: List<MemoryEntry>,
    pendingTasks: List<ScheduledTask>,
    heartbeatAdditions: List<ScheduledTask>,
    emailAccounts: List<EmailAccountSummary>,
    runtime: ChatPromptRuntimeContext,
    uiMode: ChatPromptUiMode,
    activeSkills: List<Skill> = emptyList(),
): String = buildString {
    append(soul)

    if (!memoryInstructions.isNullOrEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(memoryInstructions)
    }

    // Memory category sections are emitted for BOTH variants. memory_store / memory_forget /
    // memory_reinforce are in the local allowlist, and memories may have been learned via
    // remote models — the local model should be able to reference them. A char-count budget
    // (unlimited for remote; [LOCAL_MEMORY_BUDGET_CHARS] for local) prevents runaway growth
    // on small on-device context windows.
    val memoryBudget = when (variant) {
        SystemPromptVariant.CHAT_REMOTE -> Int.MAX_VALUE
        SystemPromptVariant.CHAT_LOCAL -> LOCAL_MEMORY_BUDGET_CHARS
    }
    var remaining = memoryBudget
    remaining = appendMemoryCategorySection("Your Memories", generalMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("User Preferences", preferenceMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("Learnings", learningMemories, withHitCount = true, remaining)
    appendMemoryCategorySection("Known Issues & Resolutions", errorMemories, withHitCount = false, remaining)

    // Data-driven sections: scheduled tasks and heartbeat additions render the actual task
    // lists. Guidance for automation/email/structured-learning is now handled by skills.
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (pendingTasks.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            appendScheduledTasksSection(pendingTasks)
        }
        if (heartbeatAdditions.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            appendHeartbeatAdditionsSection(heartbeatAdditions)
        }
    }

    // Tool call guidance — remote-only. Remote models receive tools through the API's
    // structured `tools` parameter and should respond via the API's native tool invocation
    // mechanism, not by emitting inline markup. Local models get their own
    // tool-use instructions injected by buildLocalToolPrompt().
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (isNotEmpty()) append("\n\n")
        append(DEFAULT_TOOL_CALL_GUIDANCE)
    }

    appendContextSection(runtime)

    // Inject active skill content after Context, before oak-ui sections.
    // Skills can reference time/model info from Context.
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        val enabledSkills = activeSkills.filter { it.isEnabled && it.content.isNotBlank() }
        if (enabledSkills.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("## Active Skills\n")
            append("The following skill modules are active. You MUST follow their instructions when applicable to the user's request. ")
            append("These take priority over default behaviour — if a skill describes how to handle a task, use that approach instead of improvising.\n\n")
            append("What each skill provides:\n")
            for (skill in enabledSkills) {
                append("- **${skill.name}**: ${skill.description}\n")
            }
            append("\n")
            for (skill in enabledSkills) {
                append("\n\n")
                append(skill.content)
            }
        }
    }

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        when (uiMode) {
            ChatPromptUiMode.DYNAMIC_UI -> appendDynamicUiSection()
            ChatPromptUiMode.INTERACTIVE_UI -> appendInteractiveUiSection()
            ChatPromptUiMode.NONE -> {}
        }
    }
}

/**
 * Appends a memory category section subject to a char budget. Entries are added one by
 * one until the next entry would push the section past [remainingBudget]; remaining
 * entries are dropped silently. If no entries fit, the header is not emitted either.
 *
 * Returns the remaining budget after emission so the caller can thread it through the
 * next category section. [Int.MAX_VALUE] means unlimited (no truncation).
 */
private fun StringBuilder.appendMemoryCategorySection(
    header: String,
    entries: List<MemoryEntry>,
    withHitCount: Boolean,
    remainingBudget: Int,
): Int {
    if (entries.isEmpty() || remainingBudget <= 0) return remainingBudget

    val section = StringBuilder()
    section.append("\n\n## ").append(header).append("\n")
    val headerLen = section.length
    var included = 0
    for (entry in entries) {
        val entryStart = section.length
        section.append("- **").append(entry.key).append("**")
        if (withHitCount) {
            section.append(" (reinforced ").append(entry.hitCount).append("x)")
        }
        section.append(": ").append(entry.content).append('\n')
        if (section.length > remainingBudget) {
            // This entry pushed us over. Revert it and stop.
            section.setLength(entryStart)
            break
        }
        included++
    }
    if (included == 0) {
        // Not even the first entry fit — don't emit the header alone.
        return remainingBudget
    }
    append(section)
    return (remainingBudget - section.length).coerceAtLeast(0)
}

private fun StringBuilder.appendHeartbeatAdditionsSection(additions: List<ScheduledTask>) {
    append("\n\n## Heartbeat Additions\n")
    append("Standing instructions the user has set to run on every heartbeat (trigger=HEARTBEAT). Don't duplicate these when the user asks for similar behaviour; cancel via `cancel_task` if they want one removed.\n")
    for (t in additions) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append("): ")
        append(t.prompt)
        append('\n')
    }
}

private fun StringBuilder.appendScheduledTasksSection(pendingTasks: List<ScheduledTask>) {
    append("\n\n## Scheduled Tasks\n")
    for (t in pendingTasks) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append(", scheduled: ")
        append(t.scheduledAt)
        append(")")
        if (t.cron != null) {
            append(" [cron: ")
            append(t.cron)
            append("]")
        }
        append('\n')
    }
}

private fun StringBuilder.appendContextSection(runtime: ChatPromptRuntimeContext) {
    append("\n\n## Context\n")
    // Lead with local time so the model anchors on the user's wall clock when computing
    // relative times ("in 3 minutes", "tomorrow at 9"). Tools that accept a naive datetime
    // (e.g. `schedule_task`'s `execute_at`) interpret it in this zone.
    append("- Local time: ")
    append(runtime.nowLocalIsoWithOffset)
    append(" (")
    append(runtime.timeZoneId)
    append(")\n")
    append("- UTC: ")
    append(runtime.nowUtcIsoString)
    append('\n')
    append("- Platform: ")
    append(runtime.platform)
    append('\n')
    append("- Model: ")
    append(runtime.modelId)
    append('\n')
    append("- Provider: ")
    append(runtime.providerName)
    append('\n')
}

private fun StringBuilder.appendDynamicUiSection() {
    append("\n## Dynamic UI\n")
    append("Dynamic UI mode is active. Use oak-ui blocks to enhance your responses with interactive elements.\n")
    append("The component catalog, layout tips, and examples are provided by the oak-ui skill above.\n")
}

private fun StringBuilder.appendInteractiveUiSection() {
    append("\n## Interactive UI Mode (ACTIVE)\n")
    append("You are in full-screen interactive UI mode. The user ONLY sees rendered oak-ui components — they cannot see markdown, plain text, or anything outside a oak-ui fence.\n")
    append("Your ENTIRE response must be a single ```oak-ui code fence. No text before it, no text after it, no markdown. If you write anything outside the fence, the user will NOT see it.\n\n")
    append("Rules:\n")
    append("- Each response is a COMPLETE screen layout. Include all content and actions in one oak-ui block.\n")
    append("- Always include clear primary action buttons so the user can proceed.\n")
    append("- Every screen MUST have at least one interactive element with a callback action (button, countdown with expiry action, etc.). A screen without any callback is a dead end the user cannot proceed from.\n")
    append("- Use headline text for screen titles. Structure screens with cards for grouping related content.\n")
    append("- Use descriptive callback events (e.g., \"select_destination\", \"submit_form\") so you understand what the user selected.\n")
    append("- Do NOT include back buttons, navigation bars, or any navigation controls. The app provides a back button and close button in the toolbar. The user can also type instructions in a text field below your UI.\n\n")
    append("Limitations — respect these strictly:\n")
    append("- The UI is static once rendered. NEVER show loading, fetching, or verifying states. You cannot fetch data or run operations asynchronously. Present all content immediately.\n")
    append("- Never use indeterminate progress (progress without a value) or text like \"Loading...\", \"Fetching...\", \"Verifying...\" as if something will happen later — nothing will.\n")
    append("- Each screen is independent. Only conversation history carries state between screens — there is no client-side state persistence, no session storage, no variables that survive across responses.\n")
    append("- Do not attempt to build multi-screen stateful applications (e.g., shopping carts that accumulate items, dashboards that refresh). Each response is a fresh, self-contained screen.\n")
    append("- Only use the exact components and properties defined in the oak-ui skill above. Do not invent attributes, component types, or behaviors that are not listed. If a component doesn't support a feature, do not pretend it does.\n")
    append("- Start with simple, clean layouts. A well-structured screen with a few cards and clear actions is better than a complex layout that pushes the component set beyond its capabilities.\n")
    append("- When unsure whether something will work, use a simpler approach. A working simple screen is always better than a broken ambitious one.\n")
}
