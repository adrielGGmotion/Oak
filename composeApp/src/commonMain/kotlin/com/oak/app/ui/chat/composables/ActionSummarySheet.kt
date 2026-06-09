@file:OptIn(ExperimentalMaterial3Api::class)

package com.oak.app.ui.chat.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.action_detail_back
import oak.composeapp.generated.resources.action_no_result
import oak.composeapp.generated.resources.action_summary_close
import oak.composeapp.generated.resources.action_summary_error_indicator
import oak.composeapp.generated.resources.action_summary_title
import oak.composeapp.generated.resources.action_sources_title
import org.jetbrains.compose.resources.stringResource

/**
 * Internal navigation state for the summary sheet.
 * A single ModalBottomSheet handles both levels to avoid M3 stacking issues.
 */
private sealed class SheetContent {
    data class Summary(val summary: ActionSummary) : SheetContent()
    data class Detail(val action: ToolAction, val summary: ActionSummary) : SheetContent()
}

/**
 * Single bottom sheet that handles both summary list (Level 1)
 * and detail drill-down (Level 2) internally, avoiding dual-dialog stacking.
 */
@Composable
fun ActionSummarySheet(
    summary: ActionSummary,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Internal navigation state — starts at summary level
    var content by remember(summary.id) { mutableStateOf<SheetContent>(SheetContent.Summary(summary)) }

    ModalBottomSheet(
        onDismissRequest = {
            // Always close everything on dismiss
            content = SheetContent.Summary(summary)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { target ->
            when (target) {
                is SheetContent.Summary -> {
                    SummaryContent(
                        summary = target.summary,
                        onDismiss = {
                            content = SheetContent.Summary(summary)
                            onDismiss()
                        },
                        onDrillDown = { action ->
                            content = SheetContent.Detail(action, summary)
                        },
                    )
                }
                is SheetContent.Detail -> {
                    DetailContent(
                        action = target.action,
                        onBack = {
                            content = SheetContent.Summary(target.summary)
                        },
                        onDismiss = {
                            content = SheetContent.Summary(summary)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Level 1: Summary list content
// ---------------------------------------------------------------------------

@Composable
private fun SummaryContent(
    summary: ActionSummary,
    onDismiss: () -> Unit,
    onDrillDown: (ToolAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        SummaryHeader(summary = summary, onDismiss = onDismiss)

        if (summary.variant == ActionSummaryVariant.SEARCH) {
            val searchAction = summary.actions.firstOrNull()
            if (searchAction != null && searchAction.sources.isNotEmpty()) {
                SourcesSection(sources = searchAction.sources)
            }
        }

        Spacer(Modifier.height(8.dp))

        ToolActionList(
            actions = summary.actions,
            onDrillDown = onDrillDown,
        )
    }
}

@Composable
private fun SummaryHeader(
    summary: ActionSummary,
    onDismiss: () -> Unit,
) {
    val closeCd = stringResource(Res.string.action_summary_close)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (summary.variant == ActionSummaryVariant.SEARCH) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, closeCd)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = summary.displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, closeCd)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.action_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Sources section
// ---------------------------------------------------------------------------

@Composable
private fun SourcesSection(
    sources: ImmutableList<SearchSource>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(Res.string.action_sources_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.take(5).forEach { source ->
                SourceBadge(source)
            }
        }
    }
}

@Composable
private fun SourceBadge(
    source: SearchSource,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.faviconLetter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = source.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tool action list with timeline
// ---------------------------------------------------------------------------

@Composable
private fun ToolActionList(
    actions: ImmutableList<ToolAction>,
    onDrillDown: (ToolAction) -> Unit,
) {
    val errorCd = stringResource(Res.string.action_summary_error_indicator)

    Column {
        actions.forEachIndexed { index, action ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Timeline column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp),
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        if (action.isError) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = errorCd,
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (index < actions.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }

                // Action content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDrillDown(action) }
                        .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = action.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val preview = action.result?.take(80)?.let {
                        it + if (action.result.length > 80) "\u2026" else ""
                    }
                    if (!preview.isNullOrEmpty()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Level 2: Detail drill-down content
// ---------------------------------------------------------------------------

@Composable
private fun DetailContent(
    action: ToolAction,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val backCd = stringResource(Res.string.action_detail_back)
    val noResultCd = stringResource(Res.string.action_no_result)

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header: back arrow + title
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, backCd)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = action.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Full result content
        val result = action.result
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = result ?: noResultCd,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
