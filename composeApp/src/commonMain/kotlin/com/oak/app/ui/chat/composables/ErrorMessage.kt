package com.oak.app.ui.chat.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.network.UiError
import com.oak.app.ui.handCursor
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun uiErrorText(error: UiError): String = when (error) {
    is UiError.Resource -> stringResource(error.resource)
    is UiError.Text -> error.message
}

@Composable
internal fun ErrorMessage(
    error: UiError,
    retry: () -> Unit,
) {
    val text = uiErrorText(error)
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = retry,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
