@file:OptIn(kotlin.time.ExperimentalTime::class)

// Pure builders for system prompt and prefix messages. Every input is passed explicitly
// — no DI, no suspend, no resource loading, no Clock — so tests can call builders
// directly with hand-crafted inputs.

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
     * oak-ui modes (the latter is also hidden from the UI for on-device services — see
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
 * both the chat prefix block and the heartbeat `## Email Status` block.
 */
internal data class EmailAccountSummary(
    val email: String,
    val unreadCount: Int,
    val lastSyncEpochMs: Long,
    val lastError: String? = null,
)

/**
 * Tells remote models that tools are handled through the API's structured
 * mechanism, and instructs them to proactively review available tools and
 * skills before falling back to ad-hoc solutions. Only composed into the
 * `CHAT_REMOTE` variant — on-device models get separate instructions from
 * `buildLocalToolPrompt()`.
 */
internal const val DEFAULT_TOOL_CALL_GUIDANCE =
    "## Tool Use\n" +
        "All available tools are provided to you through the API's structured `tools` parameter. " +
        "Use the API's native structured tool invocation mechanism to call them — do not output " +
        "inline tool call markup (such as `<tool_call>`, `<invoke>`, or `<TOOLCALL>` blocks) " +
        "in your text responses. When the user asks you to generate, create, produce, or " +
        "transform something (e.g. make a file, design a layout, write code, format data), " +
        "follow this procedure:\n" +
        "1. Check the `Available (not loaded) Skills` section below — if a matching skill " +
        "exists, call `load_skill` with its id before proceeding, then follow its instructions.\n" +
        "2. If the skill is already listed under `Active (loaded) Skills`, follow its instructions.\n" +
        "3. Only use an ad-hoc approach if no skill covers the request.\n" +
        "Use `list_skills` to discover disabled skills. Do not call `create_skill` unless " +
        "the user explicitly asks you to — suggest the idea and get their approval first. " +
        "Your text responses should contain only " +
        "natural language and, when appropriate, ```oak-ui blocks for interactive UI."

// ── Limits for prefix messages (memories, skills, tasks) ──────────────────────

/** Max combined character budget for the memory prefix block. */
private const val MEMORY_PREFIX_BUDGET_CHARS = 4_000
/** Max combined character budget for the skill content prefix block. */
private const val SKILL_PREFIX_BUDGET_CHARS = Int.MAX_VALUE
/** Max combined character budget for the tasks/emails prefix block. */
private const val TASK_PREFIX_BUDGET_CHARS = 2_000

// ── System prompt (lean — identity + behavior + context only) ─────────────────

/**
 * Composes the LEAN system prompt for the given [variant].
 *
 * Contains only what the model needs for behaviour and identity:
 * soul + memory instructions + tool guidance + runtime context.
 *
 * Dynamic data (memories, skills content, tasks, emails) is injected as
 * **prefix messages** — separate system-role messages that participate in
 * front-truncation when the context window is full. See [buildMemoryPrefixMessage],
 * [buildSkillPrefixMessage], and [buildTaskPrefixMessage].
 */
internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    memoryInstructions: String?,
    runtime: ChatPromptRuntimeContext,
    uiMode: ChatPromptUiMode,
): String = buildString {
    append(soul)

    if (!memoryInstructions.isNullOrEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(memoryInstructions)
    }

    // Tool call guidance — remote-only.
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (isNotEmpty()) append("\n\n")
        append(DEFAULT_TOOL_CALL_GUIDANCE)
    }

    appendContextSection(runtime)

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        when (uiMode) {
            ChatPromptUiMode.DYNAMIC_UI -> appendDynamicUiSection()
            ChatPromptUiMode.INTERACTIVE_UI -> appendInteractiveUiSection()
            ChatPromptUiMode.NONE -> {}
        }
    }
}

// ── Prefix message builders ──────────────────────────────────────────────────

/**
 * Builds the memory prefix block — stored memories grouped by category.
 *
 * Participates in front-truncation: when context is tight this block is
 * dropped before any conversation messages. Budgeted at [MEMORY_PREFIX_BUDGET_CHARS]
 * to prevent runaway growth.
 *
 * Returns null when there are no memories so the caller can skip injecting
 * an empty prefix message.
 */
internal fun buildMemoryPrefixMessage(
    generalMemories: List<MemoryEntry>,
    preferenceMemories: List<MemoryEntry>,
    learningMemories: List<MemoryEntry>,
    errorMemories: List<MemoryEntry>,
): String? {
    if (generalMemories.isEmpty() && preferenceMemories.isEmpty() &&
        learningMemories.isEmpty() && errorMemories.isEmpty()
    ) return null

    var remaining = MEMORY_PREFIX_BUDGET_CHARS
    val buffer = StringBuilder()
    buffer.append("## Known Information\n")
    buffer.append("The following information has been learned from your conversation. Use `memory_store`/`memory_forget` to manage it.\n")
    val headerLen = buffer.length

    remaining = appendMemoryCategorySection(buffer, "Your Memories", generalMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection(buffer, "User Preferences", preferenceMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection(buffer, "Learnings", learningMemories, withHitCount = true, remaining)
    appendMemoryCategorySection(buffer, "Known Issues & Resolutions", errorMemories, withHitCount = false, remaining)

    if (buffer.length == headerLen) return null // nothing fit
    return buffer.toString()
}

/**
 * Builds the skills prefix block — full content of each active skill,
 * followed by descriptions.
 *
 * Skills are loaded on-demand by the agent via `load_skill`, so truncating
 * them would defeat the purpose. The [SKILL_PREFIX_BUDGET_CHARS] budget is
 * effectively unlimited ([Int.MAX_VALUE]); context management is handled by
 * [trimMessagesForContext] which drops entire prefix messages from the front.
 *
 * Returns null when there are no active skills.
 */
internal fun buildSkillPrefixMessage(activeSkills: List<Skill>): String? {
    val enabled = activeSkills.filter { it.isEnabled && it.content.isNotBlank() }
    if (enabled.isEmpty()) return null

    val buffer = StringBuilder()
    buffer.append("## Active Skills\n")
    buffer.append("The following skills are loaded and their instructions are in effect:\n")

    // Full content first — the actual instructions the model needs.
    for (skill in enabled) {
        buffer.append("\n\n### ${skill.name}\n${skill.content}")
    }

    // Descriptions after content
    buffer.append("\n\n")
    for (skill in enabled) {
        buffer.append("- **${skill.name}**: ${skill.description}\n")
    }

    // Also list available (not loaded) skills
    val available = activeSkills.filter { it.isEnabled && it.content.isBlank() }
    if (available.isNotEmpty()) {
        buffer.append("\n\n### Available Skills\n")
        buffer.append("Call `load_skill` with a skill id to activate it:\n")
        for (skill in available) {
            buffer.append("- **${skill.name}** (id: `${skill.id}`): ${skill.description}\n")
        }
    }

    return buffer.toString()
}

/**
 * Builds the tasks prefix block — scheduled tasks and heartbeat additions.
 * Returns null when there are no pending items.
 */
internal fun buildTaskPrefixMessage(
    pendingTasks: List<ScheduledTask>,
    heartbeatAdditions: List<ScheduledTask>,
): String? {
    if (pendingTasks.isEmpty() && heartbeatAdditions.isEmpty()) return null

    var remaining = TASK_PREFIX_BUDGET_CHARS
    val buffer = StringBuilder()
    buffer.append("## Active Tasks\n")
    val headerLen = buffer.length

    if (pendingTasks.isNotEmpty()) {
        val section = buildScheduledTasksSection(pendingTasks)
        if (section.length <= remaining) {
            buffer.append(section)
            remaining -= section.length
        }
    }

    if (heartbeatAdditions.isNotEmpty() && remaining > 0) {
        val section = buildHeartbeatAdditionsSection(heartbeatAdditions)
        if (section.length <= remaining) {
            buffer.append(section)
            remaining -= section.length
        }
    }

    if (buffer.length == headerLen) return null
    return buffer.toString()
}

// ── Internal helpers (also used by heartbeats) ───────────────────────────────

internal fun buildHeartbeatAdditionsSection(additions: List<ScheduledTask>): String = buildString {
    append("\n\n### Heartbeat Additions\n")
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

internal fun buildScheduledTasksSection(pendingTasks: List<ScheduledTask>): String = buildString {
    append("\n\n### Scheduled Tasks\n")
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

/**
 * Appends a memory category section subject to a char budget. Entries are added
 * one by one until the next entry would push the section past [remainingBudget];
 * remaining entries are dropped silently. If no entries fit, the header is not
 * emitted either.
 *
 * Returns the remaining budget after emission so the caller can thread it through
 * the next category section.
 */
internal fun appendMemoryCategorySection(
    buffer: StringBuilder,
    header: String,
    entries: List<MemoryEntry>,
    withHitCount: Boolean,
    remainingBudget: Int,
): Int {
    if (entries.isEmpty() || remainingBudget <= 0) return remainingBudget

    val section = StringBuilder()
    section.append("\n\n### ").append(header).append("\n")
    var included = 0
    for (entry in entries) {
        val entryStart = section.length
        section.append("- **").append(entry.key).append("**")
        if (withHitCount) {
            section.append(" (reinforced ").append(entry.hitCount).append("x)")
        }
        section.append(": ").append(entry.content).append('\n')
        if (section.length > remainingBudget) {
            section.setLength(entryStart)
            break
        }
        included++
    }
    if (included == 0) return remainingBudget
    buffer.append(section)
    return (remainingBudget - section.length).coerceAtLeast(0)
}

// ── Sections injected into the lean system prompt ────────────────────────────

private fun StringBuilder.appendContextSection(runtime: ChatPromptRuntimeContext) {
    append("\n\n## Context\n")
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
    append("The component catalog, layout tips, and examples are provided by the oak-ui skill.\n")
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
