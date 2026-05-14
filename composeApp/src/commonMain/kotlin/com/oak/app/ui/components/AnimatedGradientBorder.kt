package com.oak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.oakOutlinedBorder(
    cornerRadius: Dp,
    borderWidth: Dp = 2.dp,
    backgroundColor: Color? = null,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .then(
        if (backgroundColor != null) {
            Modifier.background(backgroundColor, RoundedCornerShape(cornerRadius))
        } else {
            Modifier
        },
    )
    .border(borderWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius))
