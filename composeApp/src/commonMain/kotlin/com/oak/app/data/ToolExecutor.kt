package com.oak.app.data

import com.oak.app.getAvailableTools
import com.oak.app.getPlatformToolDefinitions
import com.oak.app.smartTruncate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.getString

private const val MAX_TOOL_RESULT_LENGTH = 20_000
private const val MAX_TOOL_CALLS_SINCE_READ = 10

class ToolExecutor {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    // Per-conversation read-before-edit tracking: conversationId -> (normalizedPath -> toolCallNumberAtLastRead)
    private val conversationReads = mutableMapOf<String, MutableMap<String, Int>>()
    private val conversationCounters = mutableMapOf<String, Int>()
    private val mutex = Mutex()

    /** Resolves simple "." and ".." path segments. Symlinks are NOT resolved (JVM-only). */
    private fun normalizePath(raw: String): String {
        val isAbsolute = raw.startsWith("/")
        val parts = mutableListOf<String>()
        for (segment in raw.split("/")) {
            when (segment) {
                "", "." -> { /* skip */ }
                ".." -> { if (parts.isNotEmpty() && parts.last() != "..") parts.removeLast() else parts.add(segment) }
                else -> parts.add(segment)
            }
        }
        return if (isAbsolute) "/${parts.joinToString("/")}" else parts.joinToString("/")
    }

    fun formatJsonElement(element: JsonElement): String = when {
        element is JsonNull -> "null"
        element is JsonPrimitive && element.isString -> "\"${element.content}\""
        element is JsonPrimitive -> element.content
        else -> element.toString()
    }

    suspend fun executeTool(
        name: String,
        arguments: String,
        conversationId: String? = null,
    ): String {
        val tools = getAvailableTools()
        val tool = tools.find { it.schema.name == name }
            ?: return """{"success": false, "error": "Unknown tool: '$name'. Available: ${tools.joinToString(", ") { it.schema.name }}"}"""

        val args = try {
            parseJsonToMap(arguments)
        } catch (e: Exception) {
            val safeMsg = escapeJsonString(e.message ?: "unknown error")
            return """{"success": false, "error": "Failed to parse arguments: $safeMsg"}"""
        }

        return try {
            val rawPath = args["path"]?.toString()
            val normPath = rawPath?.let { normalizePath(it) }

            // Read-before-edit enforcement (under mutex to prevent concurrent-tool races)
            if (name == "edit_file" && conversationId != null && normPath != null) {
                mutex.withLock {
                    val convReads = conversationReads[conversationId]
                    val lastReadCall = convReads?.get(normPath)
                    if (lastReadCall == null) {
                        return@executeTool """{"success": false, "error": "You must read '$rawPath' with read_file first before editing it."}"""
                    }
                    val convCounter = conversationCounters[conversationId] ?: 0
                    val staleCount = convCounter - lastReadCall
                    if (staleCount > MAX_TOOL_CALLS_SINCE_READ) {
                        return@executeTool """{"success": false, "error": "Your last read of '$rawPath' was $staleCount tool calls ago. Call read_file('$rawPath') again to refresh before editing."}"""
                    }
                }
            }

            val result = withTimeout(tool.timeout) {
                if (conversationId != null) {
                    withContext(ConversationIdElement(conversationId)) { tool.execute(args) }
                } else {
                    tool.execute(args)
                }
            }

            // Track reads + increment counter (under mutex)
            if (conversationId != null) {
                mutex.withLock {
                    conversationCounters[conversationId] = (conversationCounters[conversationId] ?: 0) + 1

                    if (name == "read_file" && normPath != null) {
                        val resultPath = if (result is Map<*, *>) result["path"]?.toString() else null
                        val trackedPath = resultPath ?: normPath
                        conversationReads
                            .getOrPut(conversationId) { mutableMapOf() }[trackedPath] =
                            conversationCounters[conversationId] ?: 0
                    }
                }
            }

            val resultString = when (result) {
                is Map<*, *> -> {
                    val jsonObject = JsonObject(
                        result.entries.associate { (k, v) ->
                            k.toString() to anyToJsonElement(v)
                        },
                    )
                    jsonParser.encodeToString(JsonElement.serializer(), jsonObject)
                }

                is String -> result

                else -> """{"result": "$result"}"""
            }
            truncateResult(resultString)
        } catch (e: TimeoutCancellationException) {
            """{"success": false, "error": "Tool '$name' timed out after ${tool.timeout}"}"""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = escapeJsonString(e.message ?: "unknown error")
            """{"success": false, "error": "Tool execution failed: $safeMsg"}"""
        }
    }

    /** Resets read tracking for a conversation. Used when switching conversations. */
    fun resetReadTracking(conversationId: String) {
        conversationReads.remove(conversationId)
        conversationCounters.remove(conversationId)
    }

    private fun truncateResult(result: String): String = result.smartTruncate(MAX_TOOL_RESULT_LENGTH)

    /** Escapes a string for safe embedding in a JSON string value. */
    private fun escapeJsonString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value)

        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) },
        )

        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })

        else -> JsonPrimitive(value.toString())
    }

    private fun parseJsonToMap(json: String): Map<String, Any> {
        val jsonObject = jsonParser.parseToJsonElement(json).jsonObject
        return jsonObject.toMap()
    }

    private fun JsonObject.toMap(): Map<String, Any> = entries.associate { (key, value) ->
        key to when (value) {
            is JsonPrimitive -> when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.boolean
                value.intOrNull != null -> value.int
                value.doubleOrNull != null -> value.double
                else -> value.toString()
            }

            is JsonObject -> value.toMap()

            else -> value.toString()
        }
    }
    suspend fun getToolDisplayName(toolId: String): String {
        val toolInfo = getPlatformToolDefinitions().find { it.id == toolId } ?: return toolId
        return toolInfo.nameRes?.let { getString(it) } ?: toolInfo.name
    }
}
