package com.oak.app.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.oak.app.getBackgroundDispatcher
import com.oak.app.ui.dynamicui.FrozenSubmission
import com.oak.app.ui.dynamicui.toSpeakableText
import com.oak.app.ui.handCursor
import com.oak.app.ui.markdown.MarkdownContent
import com.oak.app.ui.markdown.parseMarkdown
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.bot_message_copy_content_description
import oak.composeapp.generated.resources.bot_message_regenerate_content_description
import oak.composeapp.generated.resources.bot_message_speech_content_description
import oak.composeapp.generated.resources.ic_copy
import oak.composeapp.generated.resources.ic_refresh
import oak.composeapp.generated.resources.ic_stop
import oak.composeapp.generated.resources.ic_volume_up
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BotMessage(
    message: String,
    reasoningContent: String? = null,
    textToSpeech: TextToSpeechInstance?,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    isInteractive: Boolean = false,
    onUiCallback: ((event: String, data: Map<String, String>) -> Unit)? = null,
    frozen: FrozenSubmission? = null,
    onResubmit: ((event: String, data: Map<String, String>) -> Unit)? = null,
) {
    val document = remember(message) { parseMarkdown(message) }
    var isEditing by remember(frozen) { mutableStateOf(false) }
    var reasoningExpanded by remember { mutableStateOf(false) }
    val effectiveFrozen = if (isEditing && frozen != null) frozen.copy(pressedEvent = null) else frozen
    val effectiveInteractive = if (frozen != null) (onResubmit != null && isEditing) else isInteractive
    val oakUiCallback: (String, Map<String, String>) -> Unit = if (onResubmit != null) {
        { event, data ->
            isEditing = false
            onResubmit(event, data)
        }
    } else {
        onUiCallback ?: { _, _ -> }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        BotAvatar(
            modifier = Modifier
                .align(Alignment.Top)
                .padding(top = 16.dp, start = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            if (!reasoningContent.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp, end = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { reasoningExpanded = !reasoningExpanded },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (reasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Thought",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (reasoningExpanded) {
                            MarkdownContent(
                                content = reasoningContent,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Box {
                SelectionContainer {
                    MarkdownContent(
                        document = document,
                        isInteractive = effectiveInteractive,
                        onUiCallback = oakUiCallback,
                        frozen = effectiveFrozen,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                if (frozen != null && onResubmit != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .handCursor()
                            .clickable { isEditing = !isEditing },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Cancel edit" else "Edit submission",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton(
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = stringResource(Res.string.bot_message_speech_content_description),
                onClick = {
                    componentScope.launch(getBackgroundDispatcher()) {
                        textToSpeech.stop()
                        if (isSpeaking) {
                            setIsSpeaking(false)
                        } else {
                            setIsSpeaking(true)
                            try {
                                textToSpeech.say(text = message.toSpeakableText())
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) {
                                // Expected interruption - no action needed
                            } catch (e: Exception) {
                                // Handle TTS errors gracefully (service failure, audio issues, etc.)
                            }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        val clipboardManager = LocalClipboardManager.current
        SmallIconButton(
            iconResource = Res.drawable.ic_copy,
            contentDescription = stringResource(Res.string.bot_message_copy_content_description),
            onClick = {
                clipboardManager.setText(buildAnnotatedString { append(message) })
            },
        )
        if (onRegenerate != null) {
            SmallIconButton(
                iconResource = Res.drawable.ic_refresh,
                contentDescription = stringResource(Res.string.bot_message_regenerate_content_description),
                onClick = onRegenerate,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
