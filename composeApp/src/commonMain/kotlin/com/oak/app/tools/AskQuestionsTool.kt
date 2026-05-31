package com.oak.app.tools

import com.oak.app.data.AskQuestion
import com.oak.app.data.AskQuestionsManager
import com.oak.app.network.tools.ParameterSchema
import com.oak.app.network.tools.Tool
import com.oak.app.network.tools.ToolInfo
import com.oak.app.network.tools.ToolSchema
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Serializable
private data class QuestionDto(
    val id: String,
    val text: String,
    val options: List<String>? = null,
    val multiSelect: Boolean? = null,
)

@Serializable
private data class QuestionsDto(
    val questions: List<QuestionDto>,
)

fun createAskQuestionsTool(manager: AskQuestionsManager): Tool = object : Tool {
    override val timeout: Duration = 24.hours
    override val schema = ToolSchema(
        name = "ask_questions",
        description = "Ask the user one or more questions and wait for their answers. Use this when you need the user to make choices, provide preferences, or give input before you can proceed. The user can pick from the provided options or type a custom answer. Returns a formatted Q&A summary as a user message.",
        parameters = mapOf(
            "questions" to ParameterSchema(
                type = "string",
                description = "A JSON array of question objects. Each object has: id (string, unique), text (string, the question), options (optional array of strings, answer choices), multiSelect (optional boolean, default false). Example: [{\"id\": \"q1\", \"text\": \"What color?\", \"options\": [\"Red\", \"Blue\"], \"multiSelect\": false}]",
                required = true,
            ),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val rawQuestions = args["questions"]?.toString()
            ?: return mapOf("success" to false, "error" to "Missing 'questions' parameter")

        val parsed = try {
            parseQuestions(rawQuestions)
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "Failed to parse questions: ${e.message}")
        }

        if (parsed.isEmpty()) {
            return mapOf("success" to false, "error" to "At least one question is required")
        }

        return manager.ask(parsed)
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun parseQuestions(raw: String): List<AskQuestion> {
    val trimmed = raw.trim()
    val dtos = if (trimmed.startsWith("[")) {
        json.decodeFromString<List<QuestionDto>>(trimmed)
    } else {
        json.decodeFromString<QuestionsDto>(trimmed).questions
    }
    return dtos.map { dto ->
        AskQuestion(
            id = dto.id,
            text = dto.text,
            options = (dto.options ?: emptyList()).toImmutableList(),
            multiSelect = dto.multiSelect ?: false,
        )
    }
}

val askQuestionsToolInfo = ToolInfo(
    id = "ask_questions",
    name = "Ask Questions",
    description = "Ask the user questions and wait for their answers",
)
