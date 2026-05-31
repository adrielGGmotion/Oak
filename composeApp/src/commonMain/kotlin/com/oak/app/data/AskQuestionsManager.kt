package com.oak.app.data

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class AskQuestion(
    val id: String,
    val text: String,
    val options: ImmutableList<String> = kotlinx.collections.immutable.persistentListOf(),
    val multiSelect: Boolean = false,
)

class AskQuestionsManager {

    private val _pendingQuestions = MutableStateFlow<List<AskQuestion>?>(null)
    val pendingQuestions: StateFlow<List<AskQuestion>?> = _pendingQuestions.asStateFlow()

    private val _answers = MutableStateFlow<Map<String, String>>(emptyMap())
    val answers: StateFlow<Map<String, String>> = _answers.asStateFlow()

    private var continuation: CancellableContinuation<String>? = null

    suspend fun ask(questions: List<AskQuestion>): String = suspendCancellableCoroutine { cont ->
        continuation?.cancel(CancellationException("Replaced by a new ask_questions call"))
        _pendingQuestions.value = questions
        _answers.value = emptyMap()
        continuation = cont
        cont.invokeOnCancellation {
            _pendingQuestions.value = null
            _answers.value = emptyMap()
            continuation = null
        }
    }

    fun setAnswer(questionId: String, answer: String) {
        _answers.update { it + (questionId to answer) }
    }

    fun currentAnswer(questionId: String): String = _answers.value[questionId] ?: ""

    fun submit() {
        val questions = _pendingQuestions.value ?: return
        val answers = _answers.value
        val cont = continuation ?: return
        _pendingQuestions.value = null
        _answers.value = emptyMap()
        continuation = null
        val result = buildResultText(questions, answers)
        cont.resumeWith(Result.success(result))
    }

    fun cancel() {
        val cont = continuation ?: return
        _pendingQuestions.value = null
        _answers.value = emptyMap()
        continuation = null
        cont.resumeWith(Result.success(""))
    }

    private fun buildResultText(questions: List<AskQuestion>, answers: Map<String, String>): String {
        return buildString {
            questions.forEachIndexed { index, q ->
                if (index > 0) appendLine()
                appendLine("Q${index + 1}: ${q.text}")
                val answer = answers[q.id]
                if (answer.isNullOrBlank()) {
                    appendLine("A: (Skipped)")
                } else {
                    appendLine("A: $answer")
                }
            }
        }
    }
}
