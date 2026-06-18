@file:OptIn(ExperimentalMaterial3Api::class)

package com.oak.app.ui.chat.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oak.app.data.AskQuestion
import com.oak.app.ui.handCursor

@Composable
fun AskQuestionsSheet(
    questions: List<AskQuestion>,
    currentAnswers: Map<String, String>,
    onAnswer: (questionId: String, answer: String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showSummary by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    var direction by remember { mutableIntStateOf(1) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(questions) {
        currentIndex = 0
        showSummary = false
        customInput = ""
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header: back + counter + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!showSummary && currentIndex > 0) {
                    Text(
                        text = "← Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .handCursor()
                            .clickable {
                                direction = -1
                                currentIndex--
                                customInput = ""
                            },
                    )
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (showSummary) "Summary" else "${currentIndex + 1} of ${questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp).handCursor(),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = if (showSummary) -1 else currentIndex,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { if (direction > 0) it else -it })
                            .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { if (direction > 0) -it else it })
                    },
                    label = "question_pager",
                ) { state ->
                    if (state == -1) {
                        SummaryContent(
                            questions = questions,
                            answers = currentAnswers,
                            onSubmit = onSubmit,
                            onCancel = onCancel,
                            onBackToQuestions = {
                                direction = -1
                                showSummary = false
                            },
                        )
                    } else {
                        val question = questions.getOrNull(state) ?: return@AnimatedContent
                        QuestionContent(
                            question = question,
                            currentAnswer = currentAnswers[question.id] ?: "",
                            customInput = customInput,
                            onSelectOption = { option ->
                                if (question.multiSelect) {
                                    val current = currentAnswers[question.id] ?: ""
                                    val selected = current.split(", ").filter { it.isNotEmpty() }.toMutableSet()
                                    if (option in selected) selected.remove(option) else selected.add(option)
                                    val result = selected.sorted().joinToString(", ")
                                    onAnswer(question.id, result)
                                } else {
                                    onAnswer(question.id, option)
                                    customInput = ""
                                    direction = 1
                                    if (state == questions.size - 1) {
                                        showSummary = true
                                    } else {
                                        currentIndex++
                                    }
                                }
                            },
                            onCustomInput = { text ->
                                customInput = text
                                if (text.isNotBlank()) {
                                    onAnswer(question.id, text)
                                } else if (question.options.isEmpty()) {
                                    onAnswer(question.id, "")
                                }
                            },
                        )
                    }
                }
            }

            if (!showSummary) {
                Spacer(Modifier.height(16.dp))
                val isLast = currentIndex == questions.size - 1
                Button(
                    onClick = {
                        if (isLast) {
                            direction = 1
                            showSummary = true
                        } else {
                            direction = 1
                            currentIndex++
                            customInput = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (isLast) "Review answers" else "Next")
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    question: AskQuestion,
    currentAnswer: String,
    customInput: String,
    onSelectOption: (String) -> Unit,
    onCustomInput: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = question.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(20.dp))

        if (question.options.isNotEmpty()) {
            OptionsSection(
                options = question.options,
                multiSelect = question.multiSelect,
                currentAnswer = currentAnswer,
                onSelect = onSelectOption,
            )
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = if (currentAnswer.isNotEmpty() && currentAnswer !in question.options &&
                !question.options.any { currentAnswer.split(", ").contains(it) }
            ) {
                currentAnswer
            } else {
                customInput
            },
            onValueChange = onCustomInput,
            label = { Text("Type your own answer...") },
            placeholder = { Text("Type your own answer...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )
    }
}

@Composable
private fun OptionsSection(
    options: List<String>,
    multiSelect: Boolean,
    currentAnswer: String,
    onSelect: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            val selectedOptions = currentAnswer.split(", ").filter { it.isNotEmpty() }
            val isSelected = if (multiSelect) {
                option in selectedOptions
            } else {
                option == currentAnswer
            }

            OptionChip(
                index = index,
                label = option,
                isSelected = isSelected,
                multiSelect = multiSelect,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun OptionChip(
    index: Int,
    label: String,
    isSelected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .handCursor()
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
        if (multiSelect && isSelected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SummaryContent(
    questions: List<AskQuestion>,
    answers: Map<String, String>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onBackToQuestions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "← Back to questions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .handCursor()
                .clickable(onClick = onBackToQuestions),
        )

        Text(
            text = "Review your answers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        questions.forEachIndexed { index, question ->
            val answer = answers[question.id]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = "Q${index + 1}: ${question.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (answer.isNullOrBlank()) "(Skipped)" else answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (answer.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Submit answers")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(48.dp).handCursor(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Cancel")
        }
    }
}
