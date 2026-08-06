package com.inspiredandroid.oak.ui.chat.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inspiredandroid.oak.ui.components.LogoAnimation
import com.inspiredandroid.oak.ui.components.animatedGradientBorder
import com.inspiredandroid.oak.ui.handCursor
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.oak_build_open
import oak.composeapp.generated.resources.start_interactive_ui
import oak.composeapp.generated.resources.welcome_message
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyState(
    modifier: Modifier,
    isUsingSharedKey: Boolean,
    onStartInteractiveMode: (() -> Unit)? = null,
    onOpenOakBuild: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoAnimation()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (onStartInteractiveMode != null) {
            Spacer(Modifier.height(16.dp))
            AnimatedBorderButton(
                text = stringResource(Res.string.start_interactive_ui),
                onClick = onStartInteractiveMode,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (onOpenOakBuild != null) {
            OutlinedButton(
                onClick = onOpenOakBuild,
                modifier = Modifier.handCursor(),
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.oak_build_open))
            }
            Spacer(Modifier.height(8.dp))
        }

    }
}

@Composable
private fun AnimatedBorderButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .handCursor()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .animatedGradientBorder(
                cornerRadius = 50.dp,
                borderWidth = 3.dp,
                backgroundColor = MaterialTheme.colorScheme.background,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
