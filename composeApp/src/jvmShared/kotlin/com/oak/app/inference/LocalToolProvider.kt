package com.oak.app.inference

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.oak.app.network.tools.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Wraps an Oak [Tool] as a litertlm [OpenApiTool] so the on-device model can call
 * tools natively via constrained decoding instead of text-based prompt injection.
 *
 * This is JVM-only because litertlm tool execution is synchronous while Oak tools
 * are suspend functions — we bridge the gap with [runBlocking].
 */
class OakToolProvider(private val tool: Tool) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String {
        val schema = tool.schema
        val jsonObject = buildJsonObject {
            put("name", schema.name)
            put("description", schema.description)
            if (schema.parameters.isNotEmpty()) {
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for ((name, param) in schema.parameters) {
                            put(name, buildJsonObject {
                                put("type", param.type)
                                put("description", param.description)
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for ((name, param) in schema.parameters) {
                            if (param.required) add(JsonPrimitive(name))
                        }
                    })
                })
            }
        }
        return Json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    override fun execute(paramsJsonString: String): String {
        return runBlocking {
            try {
                val parsed = Json.parseToJsonElement(paramsJsonString)
                val convertedArgs = mutableMapOf<String, Any>()
                if (parsed is JsonObject) {
                    for ((key, value) in parsed) {
                        convertedArgs[key] = when (value) {
                            is JsonPrimitive -> {
                                val content = value.content
                                when {
                                    content.equals("true", ignoreCase = true) -> true
                                    content.equals("false", ignoreCase = true) -> false
                                    content.toIntOrNull() != null -> content.toInt()
                                    content.toLongOrNull() != null -> content.toLong()
                                    content.toDoubleOrNull() != null -> content.toDouble()
                                    else -> content
                                }
                            }
                            else -> value.toString()
                        }
                    }
                }
                val result = tool.execute(convertedArgs)
                result.toString()
            } catch (e: Exception) {
                """{"error": "${e.message ?: "Unknown error"}"}"""
            }
        }
    }

    companion object {
        /**
         * Converts a list of Oak [Tool] instances to litertlm [ToolProvider]s.
         */
        fun toToolProviders(tools: List<Tool>): List<ToolProvider> {
            return tools.map { tool -> tool(OakToolProvider(tool)) }
        }
    }
}

/**
 * Wraps a [LocalTool] as a litertlm [OpenApiTool] for native tool calling.
 * [LocalTool] is the common interface; this adapter bridges it to litertlm's API.
 */
class LocalToolAdapter(private val localTool: LocalTool) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String {
        if (localTool.descriptionJsonString.isNotBlank()) {
            return localTool.descriptionJsonString
        }
        val jsonObject = buildJsonObject {
            put("name", localTool.name)
            put("description", localTool.name)
        }
        return Json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    override fun execute(paramsJsonString: String): String {
        return runBlocking {
            try {
                localTool.execute(paramsJsonString)
            } catch (e: Exception) {
                """{"error": "${e.message ?: "Unknown error"}"}"""
            }
        }
    }
}
