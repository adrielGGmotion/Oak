package com.oak.app.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.oak_leaf_loop
import org.jetbrains.compose.resources.painterResource

@Composable
fun LogoAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Icon(
        painter = painterResource(Res.drawable.oak_leaf_loop),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                transformOrigin = TransformOrigin(
                    pivotFractionX = 0.5f,
                    pivotFractionY = 1f,
                )
                rotationZ = rotation
            },
        tint = MaterialTheme.colorScheme.primary,
    )
}
