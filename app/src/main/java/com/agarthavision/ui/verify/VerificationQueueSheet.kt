@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agarthavision.R
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.capture.QueueFilter
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.themes.styles
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationQueueSheet(
    frames: List<FlaggedFrame>,
    selectedFilter: QueueFilter,
    onFilterSelected: (QueueFilter) -> Unit,
    onRowClick: (FlaggedFrame) -> Unit,
    onRowDelete: (FlaggedFrame) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val filteredFrames = remember(frames, selectedFilter) {
        filterQueueFrames(frames, selectedFilter)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.styles.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeightDp * 0.95f)
                .padding(horizontal = AgarthaSpacing.screenEdge)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.queue_sheet_title, filteredFrames.size),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.styles.foreground,
                modifier = Modifier.padding(vertical = AgarthaSpacing.md),
            )

            QueueFilterChips(
                selected = selectedFilter,
                onSelected = onFilterSelected,
                modifier = Modifier.padding(bottom = AgarthaSpacing.sm),
            )

            if (filteredFrames.isEmpty()) {
                QueueEmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs),
                    modifier = Modifier.padding(bottom = AgarthaSpacing.md),
                ) {
                    items(
                        items = filteredFrames,
                        key = { frame -> frame.capturedAt.toEpochMilli() },
                    ) { frame ->
                        VerificationQueueRow(
                            frame = frame,
                            onClick = { onRowClick(frame) },
                            onDelete = { onRowDelete(frame) },
                        )
                    }
                }
            }
        }
    }
}

internal fun filterQueueFrames(
    frames: List<FlaggedFrame>,
    filter: QueueFilter,
): List<FlaggedFrame> = when (filter) {
    QueueFilter.ALL -> frames
    QueueFilter.FLAGGED -> frames.filter { it.source == FrameSource.MODEL }
    QueueFilter.MANUAL -> frames.filter { it.source == FrameSource.MANUAL }
    QueueFilter.REPEAT -> frames.filter { it.markedAsRepeat }
}

@Composable
private fun QueueFilterChips(
    selected: QueueFilter,
    onSelected: (QueueFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs),
    ) {
        QueueFilterChip(
            label = stringResource(R.string.queue_filter_all),
            selected = selected == QueueFilter.ALL,
            onClick = { onSelected(QueueFilter.ALL) },
        )
        QueueFilterChip(
            label = stringResource(R.string.queue_filter_flagged),
            selected = selected == QueueFilter.FLAGGED,
            onClick = { onSelected(QueueFilter.FLAGGED) },
        )
        QueueFilterChip(
            label = stringResource(R.string.queue_filter_manual),
            selected = selected == QueueFilter.MANUAL,
            onClick = { onSelected(QueueFilter.MANUAL) },
        )
        QueueFilterChip(
            label = stringResource(R.string.queue_filter_repeat),
            selected = selected == QueueFilter.REPEAT,
            onClick = { onSelected(QueueFilter.REPEAT) },
        )
    }
}

@Composable
private fun QueueFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    KomoBadge(
        variant = if (selected) BadgeVariant.Default else BadgeVariant.Outline,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(text = label)
    }
}

@Composable
private fun QueueEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgarthaSpacing.xxl),
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.styles.mutedForeground,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.padding(AgarthaSpacing.xxs))
        Text(
            text = stringResource(R.string.queue_sheet_empty_state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.styles.mutedForeground,
        )
    }
}

@Composable
private fun VerificationQueueRow(
    frame: FlaggedFrame,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val top = frame.predictions.firstOrNull()
    val timeText = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }
    val title = when {
        frame.source == FrameSource.MANUAL -> stringResource(R.string.queue_row_manual_title)
        top != null -> top.classLabel
        else -> stringResource(R.string.queue_row_unknown_class)
    }
    val subtitle = if (frame.source == FrameSource.MANUAL) {
        stringResource(R.string.queue_row_manual_subtitle, timeText)
    } else {
        stringResource(
            R.string.queue_row_subtitle,
            "%.0f".format((top?.confidence ?: 0f) * 100f),
            frame.predictions.size,
            timeText,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.styles.muted.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(AgarthaSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.styles.background),
        ) {
            AsyncImage(
                model = frame.jpegBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(modifier = Modifier.padding(AgarthaSpacing.xxs))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AgarthaSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.styles.foreground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs),
                modifier = Modifier.padding(top = AgarthaSpacing.xxs),
            ) {
                KomoBadge(variant = BadgeVariant.Outline) {
                    Text(
                        text = if (frame.source == FrameSource.MANUAL) {
                            stringResource(R.string.queue_row_source_manual)
                        } else {
                            stringResource(R.string.queue_row_source_ai)
                        },
                        color = MaterialTheme.styles.mutedForeground,
                    )
                }
                if (frame.markedAsRepeat) {
                    KomoBadge(variant = BadgeVariant.Outline) {
                        Text(
                            text = stringResource(R.string.verify_repeat_badge),
                            color = MaterialTheme.styles.mutedForeground,
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.queue_row_delete),
                tint = MaterialTheme.styles.mutedForeground,
            )
        }
    }
}

