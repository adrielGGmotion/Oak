package com.oak.app.ui.chat.composables

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.oak.app.ui.chat.History
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
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
    val snippet: String = "",
    val faviconUrl: String = "",
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
    val parsedArguments: JsonObject? = null,
    val parsedResult: JsonObject? = null,
)

/**
 * A group of tool calls triggered by a message.
 * Rendered as an inline summary row below the triggering message; tapping opens a detail sheet.
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
 * Tool calls are anchored to the most recent message before them — either
 * the last user message or the last assistant text response. Consecutive
 * tool-calling entries are grouped under the same anchor.
 */
fun deriveActionSummaries(history: List<History>): List<ActionSummary> {
    val cleanHistory = history.filter { it.role != History.Role.TOOL_EXECUTING }

    val allToolResults = mutableMapOf<String, History>()
    for (entry in cleanHistory) {
        if (entry.role == History.Role.TOOL && entry.toolCallId != null) {
            allToolResults[entry.toolCallId] = entry
        }
    }

    data class Round(
        val toolActions: List<ToolAction>,
        val anchorId: String,
    )

    val rounds = mutableListOf<Round>()
    var pendingActions = mutableListOf<ToolAction>()
    var pendingAnchorId: String? = null
    var lastMessageId: String? = null

    fun flushRound() {
        val anchor = pendingAnchorId
        if (pendingActions.isNotEmpty() && anchor != null) {
            rounds.add(Round(pendingActions.toList(), anchor))
            pendingActions = mutableListOf()
            pendingAnchorId = null
        }
    }

    for (entry in cleanHistory) {
        when (entry.role) {
            History.Role.USER -> {
                lastMessageId = entry.id
            }

            History.Role.ASSISTANT -> {
                if (!entry.toolCalls.isNullOrEmpty()) {
                    if (pendingActions.isEmpty()) {
                        pendingAnchorId = lastMessageId
                    }
                    for (tc in entry.toolCalls) {
                        val toolEntry = allToolResults[tc.id]
                        val resultContent = toolEntry?.content
                        val registry = ToolDisplayRegistry.lookup(tc.name)
                        val parsedArgs = runCatching {
                            errorJsonParser.parseToJsonElement(tc.arguments).jsonObject
                        }.getOrNull()
                        val parsedResult = runCatching {
                            resultContent?.let { errorJsonParser.parseToJsonElement(it).jsonObject }
                        }.getOrNull()
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
                                parsedArguments = parsedArgs,
                                parsedResult = parsedResult,
                            ),
                        )
                    }
                } else if (entry.content.isNotEmpty() && !entry.isThinking) {
                    flushRound()
                    lastMessageId = entry.id
                }
            }

            else -> {}
        }
    }
    flushRound()

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
    if (allActions.all { ToolDisplayRegistry.isHiddenTool(it.name) }) return null

    val visibleActions = allActions.filter { !ToolDisplayRegistry.isHiddenTool(it.name) }
    if (visibleActions.isEmpty()) return null

    val variant = determineVariant(visibleActions)
    val displayText = buildDisplayText(visibleActions, variant)
    val truncatedText = truncateText(displayText, MAX_SUMMARY_TEXT_LENGTH)
    val fileCreationCount = visibleActions.count { it.isFileCreation }

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
            val snippet = obj["snippet"]?.jsonPrimitive?.content ?: ""
            val faviconLetter = title.firstOrNull()?.uppercase() ?: "?"
            val faviconUrl = extractFaviconUrl(url)
            SearchSource(
                title = title,
                faviconLetter = faviconLetter,
                url = url,
                snippet = snippet,
                faviconUrl = faviconUrl,
            )
        }
        sources.toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }
}

private fun extractFaviconUrl(url: String): String {
    return try {
        val host = URI(url).host ?: return ""
        "https://www.google.com/s2/favicons?domain=$host&sz=32"
    } catch (_: Exception) {
        ""
    }
}

private fun truncateText(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    return text.substring(0, maxLength - 1).trimEnd() + "\u2026"
}
