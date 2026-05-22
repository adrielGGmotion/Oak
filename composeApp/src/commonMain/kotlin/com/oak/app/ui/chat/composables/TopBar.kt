package com.oak.app.ui.chat.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.ui.chat.ChatActions
import com.oak.app.ui.handCursor
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.sandbox_content_description
import oak.composeapp.generated.resources.settings_content_description
import oak.composeapp.generated.resources.toggle_speech_output_content_description
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.stringResource


@Composable
internal fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    onNavigateToSettings: () -> Unit,
    isSandboxAvailable: Boolean,
    isSandboxOpen: Boolean,
    isShellExecuting: Boolean,
    onToggleSandbox: () -> Unit,
    onToggleDrawer: (() -> Unit)? = null,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    if (navigationTabBar != null) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        ) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                if (onToggleDrawer != null) {
                    IconButton(
                        modifier = Modifier.handCursor(),
                        onClick = { onToggleDrawer() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open navigation",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                LeadingButtons(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions, isSandboxAvailable, isSandboxOpen, isShellExecuting, onToggleSandbox)
            }
            Box(modifier = Modifier.align(Alignment.Center)) {
                navigationTabBar()
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (textToSpeech != null) {
                    SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
                }
                IconButton(
                    modifier = Modifier.handCursor(),
                    onClick = onNavigateToSettings,
                ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(Res.string.settings_content_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    } else {
        Row {
            if (onToggleDrawer != null) {
                IconButton(
                    modifier = Modifier.handCursor(),
                    onClick = { onToggleDrawer() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            LeadingButtons(
                textToSpeech = textToSpeech,
                isSpeechOutputEnabled = isSpeechOutputEnabled,
                isSpeaking = isSpeaking,
                actions = actions,
                isSandboxAvailable = isSandboxAvailable,
                isSandboxOpen = isSandboxOpen,
                isShellExecuting = isShellExecuting,
                onToggleSandbox = onToggleSandbox,
            )
            Spacer(Modifier.weight(1f))
            if (textToSpeech != null) {
                SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
            }
        }
    }
}

@Composable
private fun LeadingButtons(
    textToSpeech: TextToSpeechInstance?,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isSandboxAvailable: Boolean,
    isSandboxOpen: Boolean,
    isShellExecuting: Boolean,
    onToggleSandbox: () -> Unit,
) {
    if (isSandboxAvailable) {
        val flashAlpha = remember { Animatable(0f) }
        LaunchedEffect(isShellExecuting) {
            if (isShellExecuting) {
                flashAlpha.snapTo(0.4f)
                flashAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                )
            }
        }
        val primary = MaterialTheme.colorScheme.primary
        val checkedContainer = primary.copy(alpha = 0.2f)
        val flashContainer = primary.copy(alpha = flashAlpha.value)
        IconToggleButton(
            checked = isSandboxOpen,
            onCheckedChange = { onToggleSandbox() },
            modifier = Modifier.handCursor(),
            colors = IconButtonDefaults.iconToggleButtonColors(
                containerColor = flashContainer,
                checkedContainerColor = if (flashAlpha.value > 0f) flashContainer else checkedContainer,
                checkedContentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = stringResource(Res.string.sandbox_content_description),
                tint = if (isSandboxOpen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }
    }
}

@Composable
private fun SpeechToggleButton(
    textToSpeech: TextToSpeechInstance,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
) {
    IconButton(
        modifier = Modifier.handCursor(),
        onClick = {
            if (isSpeechOutputEnabled && isSpeaking) {
                actions.setIsSpeaking(false, "")
                textToSpeech.stop()
            }
            actions.toggleSpeechOutput()
        },
    ) {
        Icon(
            imageVector = if (isSpeechOutputEnabled) {
                Icons.AutoMirrored.Filled.VolumeUp
            } else {
                Icons.AutoMirrored.Filled.VolumeOff
            },
            contentDescription = stringResource(Res.string.toggle_speech_output_content_description),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
