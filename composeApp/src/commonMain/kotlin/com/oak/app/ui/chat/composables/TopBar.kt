package com.oak.app.ui.chat.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.data.ServiceEntry
import com.oak.app.ui.chat.ChatActions
import com.oak.app.ui.handCursor
import kotlinx.collections.immutable.ImmutableList
import nl.marc_apps.tts.TextToSpeechInstance
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_content_description
import oak.composeapp.generated.resources.toggle_speech_output_content_description
import org.jetbrains.compose.resources.stringResource


@Composable
internal fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    onNavigateToSettings: () -> Unit,
    availableServices: ImmutableList<ServiceEntry> = kotlinx.collections.immutable.persistentListOf(),
    onSelectService: (String) -> Unit = {},
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
                if (availableServices.size > 1) {
                    ServiceSelector(
                        services = availableServices,
                        onSelectService = onSelectService,
                    )
                }
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
            if (availableServices.size > 1) {
                ServiceSelector(
                    services = availableServices,
                    onSelectService = onSelectService,
                )
            }
            Spacer(Modifier.weight(1f))
            if (textToSpeech != null) {
                SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
            }
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
