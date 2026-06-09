package com.oak.app.ui.chat.composables

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.oak.app.ui.chat.History
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

private val errorJsonParser = Json { ignoreUnknownKeys = true }

/**
 * Semantic variant that determines the icon and display style of a summary row.
 */
enum class ActionSummaryVariant {
    /** Reasoning/analysis actions (clock icon). "Analyzed...", "Dismissed..." */
    REASONING,
    /** Search/web actions (magnifying glass icon). "Searched for '...'". */
    SEARCH,
    /** Multi-step tool execution (steps icon). "N steps". */
    STEPS,
    /** File/code creation (document icon). "Created oak-chatbox.jsx". */
    DOCUMENT,
}

/**
 * Metadata for a web search result source shown in the Sources section.
 */
@Immutable
data class SearchSource(
    val title: String,
    val faviconLetter: String,
    val url: String,
)

/**
 * A single tool action within an [ActionSummary].
 */
@Immutable
data class ToolAction(
    val id: String,
    val name: String,
    val displayName: String,
    val icon: ImageVector,
    val isError: Boolean,
    val arguments: String,
    val result: String?,
    val sources: ImmutableList<SearchSource> = persistentListOf(),
    val isFileCreation: Boolean = false,
)

/**
 * A group of tool calls that happened between two assistant text messages.
 * Rendered as an inline summary row in the chat; tapping opens a detail sheet.
 */
@Immutable
data class ActionSummary(
    val id: String,
    val assistantHistoryId: String,
    val actions: ImmutableList<ToolAction>,
    val variant: ActionSummaryVariant,
    val displayText: String,
    val truncatedText: String,
    val isComplete: Boolean,
    val hasArtifacts: Boolean,
    val artifactCount: Int,
)

// ---------------------------------------------------------------------------
// Derivation
// ---------------------------------------------------------------------------

/**
 * Derives [ActionSummary] groups from chat [history].
 *
 * Groups all tool calls between two text messages into a single summary.
 * The summary is keyed to the *following* text ASSISTANT entry so it renders
 * right before that answer — matching Claude's "N steps >" pattern.
 *
 * If history ends with tool calls (no final text yet), the summary is keyed
 * to the last tool-calling entry so it still appears.
 */
fun deriveActionSummaries(history: List<History>): List<ActionSummary> {
    val cleanHistory = history.filter { it.role != History.Role.TOOL_EXECUTING }

    // --- First pass: collect all tool results keyed by toolCallId -----------
    val allToolResults = mutableMapOf<String, History>()
    for (entry in cleanHistory) {
        if (entry.role == History.Role.TOOL && entry.toolCallId != null) {
            allToolResults[entry.toolCallId] = entry
        }
    }

    // --- Second pass: walk history, accumulating tool calls into rounds ---
    // A "round" = all tool calls since the last user/text-assistant message,
    // ending at the next text ASSISTANT (or end of history).
    data class Round(
        val toolActions: List<ToolAction>,
        val anchorId: String,      // the ASSISTANT id this summary attaches to
    )

    val rounds = mutableListOf<Round>()
    var pendingActions = mutableListOf<ToolAction>()
    var pendingAnchorId: String? = null

    fun flushRound() {
        val anchor = pendingAnchorId
        if (pendingActions.isNotEmpty() && anchor != null) {
            rounds.add(Round(pendingActions.toList(), anchor))
            pendingActions = mutableListOf()
            pendingAnchorId = null
        }
    }

    var lastUserOrTextIdx = -1

    for ((idx, entry) in cleanHistory.withIndex()) {
        if (entry.role != History.Role.ASSISTANT) continue

        if (!entry.toolCalls.isNullOrEmpty()) {
            // Tool-calling assistant entry — accumulate its tool calls
            for (tc in entry.toolCalls) {
                val toolEntry = allToolResults[tc.id]
                val resultContent = toolEntry?.content
                val registry = ToolDisplayRegistry.lookup(tc.name)
                pendingActions.add(
                    ToolAction(
                        id = tc.id,
                        name = tc.name,
                        displayName = registry.displayName.ifEmpty { ToolDisplayRegistry.humanizeToolName(tc.name) },
                        icon = registry.icon,
                        isError = resultContent?.let { isToolError(it) } ?: false,
                        arguments = tc.arguments,
                        result = resultContent,
                        sources = if (registry.isSearchTool && resultContent != null) {
                            extractSearchSources(resultContent)
                        } else {
                            persistentListOf()
                        },
                        isFileCreation = registry.isFileCreationTool,
                    ),
                )
            }
            // This tool-calling entry becomes the fallback anchor
            // if no text response follows
            if (pendingAnchorId == null) {
                pendingAnchorId = entry.id
            }
        } else if (entry.content.isNotEmpty() && !entry.isThinking) {
            // Text response — this anchors the previous round of tool calls
            flushRound()
            // Start a new potential round anchored to this text entry
            pendingAnchorId = entry.id
            lastUserOrTextIdx = idx
        }
    }
    // Flush any trailing round (in-progress streaming)
    flushRound()

    // --- Third pass: build summaries for each round ------------------------
    return rounds.mapNotNull { round ->
        buildSummaryFromActions(round.toolActions, round.anchorId)
    }
}

/**
 * Builds an [ActionSummary] from a merged list of [ToolAction]s.
 * Returns `null` if all actions are hidden.
 */
private fun buildSummaryFromActions(
    allActions: List<ToolAction>,
    anchorAssistantId: String,
): ActionSummary? {
    // Skip if all tools are hidden
    if (allActions.all { ToolDisplayRegistry.isHiddenTool(it.name) }) return null

    // Filter out hidden tools for display
    val visibleActions = allActions.filter { !ToolDisplayRegistry.isHiddenTool(it.name) }
    if (visibleActions.isEmpty()) return null

    val variant = determineVariant(visibleActions)
    val displayText = buildDisplayText(visibleActions, variant)
    val truncatedText = truncateText(displayText, MAX_SUMMARY_TEXT_LENGTH)
    val fileCreationCount = visibleActions.count { it.isFileCreation }

    // Complete = all actions have results
    val isComplete = visibleActions.all { action ->
        allActions.any { it.id == action.id && it.result != null }
    }

    return ActionSummary(
        id = "summary_$anchorAssistantId",
        assistantHistoryId = anchorAssistantId,
        actions = visibleActions.toImmutableList(),
        variant = variant,
        displayText = displayText,
        truncatedText = truncatedText,
        isComplete = isComplete,
        hasArtifacts = fileCreationCount > 0,
        artifactCount = fileCreationCount,
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private const val MAX_SUMMARY_TEXT_LENGTH = 45

private fun determineVariant(actions: List<ToolAction>): ActionSummaryVariant {
    if (actions.size >= 2) return ActionSummaryVariant.STEPS
    val single = actions.first()
    if (ToolDisplayRegistry.isSearchTool(single.name)) return ActionSummaryVariant.SEARCH
    if (single.isFileCreation) return ActionSummaryVariant.DOCUMENT
    return ActionSummaryVariant.REASONING
}

private fun buildDisplayText(
    actions: List<ToolAction>,
    variant: ActionSummaryVariant,
): String = when (variant) {
    ActionSummaryVariant.SEARCH -> {
        val query = extractSearchQuery(actions.first().arguments)
        if (query.isNotEmpty()) "Searched for '$query'" else actions.first().displayName
    }
    ActionSummaryVariant.DOCUMENT -> {
        val filename = extractFilename(actions.first())
        if (filename.isNotEmpty()) "Created $filename" else actions.first().displayName
    }
    ActionSummaryVariant.STEPS -> {
        val count = actions.size
        val hasError = actions.any { it.isError }
        if (hasError) "$count steps (with errors)" else "$count steps"
    }
    ActionSummaryVariant.REASONING -> actions.first().displayName
}

private fun extractSearchQuery(arguments: String): String {
    return try {
        val json = errorJsonParser.parseToJsonElement(arguments).jsonObject
        json["query"]?.jsonPrimitive?.content
            ?: json["q"]?.jsonPrimitive?.content
            ?: ""
    } catch (_: Exception) {
        ""
    }
}

private fun extractFilename(action: ToolAction): String {
    return try {
        val json = errorJsonParser.parseToJsonElement(action.arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content
            ?: json["file_path"]?.jsonPrimitive?.content
            ?: return ""
        path.substringAfterLast("/")
    } catch (_: Exception) {
        ""
    }
}

private fun isToolError(content: String): Boolean {
    if (content.length > 200) {
        // Only check the beginning to avoid parsing huge truncated results
        val head = content.substring(0, min(200, content.length))
        return head.contains("\"success\"") && head.contains("false") ||
            head.contains("\"error\"")
    }
    return try {
        val json = errorJsonParser.parseToJsonElement(content).jsonObject
        val success = json["success"]?.jsonPrimitive?.content
        success == "false" || json.containsKey("error")
    } catch (_: Exception) {
        false
    }
}

private fun extractSearchSources(content: String): ImmutableList<SearchSource> {
    return try {
        val json = errorJsonParser.parseToJsonElement(content).jsonObject
        val results = json["results"] ?: return persistentListOf()
        val resultsArray = when (results) {
            is kotlinx.serialization.json.JsonArray -> results
            else -> return persistentListOf()
        }
        val sources = resultsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content ?: obj["link"]?.jsonPrimitive?.content ?: ""
            val faviconLetter = title.firstOrNull()?.uppercase() ?: "?"
            SearchSource(title = title, faviconLetter = faviconLetter, url = url)
        }
        sources.toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }
}

private fun truncateText(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    return text.substring(0, maxLength - 1).trimEnd() + "\u2026"
}
