package com.oak.app.ui.chat.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.oak.app.data.ServiceEntry
import com.oak.app.ui.handCursor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.open_navigation_content_description
import oak.composeapp.generated.resources.settings_content_description
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource


@Composable
internal fun TopBar(
    onNavigateToSettings: () -> Unit,
    onToggleDrawer: (() -> Unit)? = null,
    navigationTabBar: (@Composable () -> Unit)? = null,
    availableServices: ImmutableList<ServiceEntry> = kotlinx.collections.immutable.persistentListOf(),
    onSelectService: (String) -> Unit = {},
) {
    if (navigationTabBar != null) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggleDrawer != null) {
                    IconButton(
                        modifier = Modifier.handCursor(),
                        onClick = { onToggleDrawer() },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = stringResource(Res.string.open_navigation_content_description),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                if (availableServices.size > 1) {
                    CenteredModelPill(
                        currentService = availableServices.first(),
                        services = availableServices,
                        onSelectService = onSelectService,
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.Center)) {
                navigationTabBar()
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
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
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggleDrawer != null) {
                IconButton(
                    modifier = Modifier.handCursor(),
                    onClick = { onToggleDrawer() },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(Res.string.open_navigation_content_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (availableServices.size > 1) {
                CenteredModelPill(
                    currentService = availableServices.first(),
                    services = availableServices,
                    onSelectService = onSelectService,
                )
            }

            Spacer(Modifier.weight(1f))

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
}

@Composable
private fun CenteredModelPill(
    currentService: ServiceEntry,
    services: ImmutableList<ServiceEntry>,
    onSelectService: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fromDismiss by remember { mutableStateOf(false) }

    val label = if (currentService.modelId.isNotEmpty()) {
        currentService.modelId
    } else {
        currentService.serviceName
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
    )

    val dropdownScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.85f,
        animationSpec = tween(durationMillis = 200),
    )
    val dropdownAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
    )

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable {
                    if (fromDismiss) {
                        fromDismiss = false
                        return@clickable
                    }
                    expanded = !expanded
                }
                .handCursor()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        val spacingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        if (expanded) {
            Popup(
                onDismissRequest = {
                    fromDismiss = true
                    expanded = false
                },
                properties = PopupProperties(focusable = false),
                popupPositionProvider = remember(spacingPx) { ModelDropdownPositionProvider(spacingPx) },
            ) {
                Surface(
                    modifier = Modifier.graphicsLayer {
                        scaleX = dropdownScale
                        scaleY = dropdownScale
                        this.alpha = dropdownAlpha
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        services.forEachIndexed { index, entry ->
                            val isCurrent = entry.instanceId == currentService.instanceId
                            DropdownItem(
                                entry = entry,
                                isCurrent = isCurrent,
                                index = index,
                                totalItems = services.size,
                                expanded = expanded,
                                onClick = {
                                    expanded = false
                                    if (!isCurrent) {
                                        onSelectService(entry.instanceId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded) {
            delay(100)
            fromDismiss = false
        }
    }
}

@Composable
private fun DropdownItem(
    entry: ServiceEntry,
    isCurrent: Boolean,
    index: Int,
    totalItems: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val itemDelay = index * 30

    val itemAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 200, delayMillis = if (expanded) itemDelay else 0),
    )
    val itemOffsetY by animateFloatAsState(
        targetValue = if (expanded) 0f else -8f,
        animationSpec = tween(durationMillis = 200, delayMillis = if (expanded) itemDelay else 0),
    )

    val rowBackground = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subTextColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 200.dp)
            .graphicsLayer {
                alpha = itemAlpha
                translationY = itemOffsetY
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(entry.icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = textColor,
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = entry.serviceName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            if (entry.modelId.isNotEmpty()) {
                Text(
                    text = entry.modelId,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor,
                )
            }
        }
    }
}

private class ModelDropdownPositionProvider(
    private val verticalSpacing: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, maxX)
        val above = anchorBounds.top - popupContentSize.height - verticalSpacing
        val y = if (above >= 0) {
            above
        } else {
            val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
            (anchorBounds.bottom + verticalSpacing).coerceAtMost(maxY)
        }
        return IntOffset(x, y)
    }
}
