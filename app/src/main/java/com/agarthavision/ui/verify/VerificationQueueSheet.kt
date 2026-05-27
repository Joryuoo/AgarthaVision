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
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.themes.styles
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationQueueSheet(
    frames: List<FlaggedFrame>,
    onRowClick: (FlaggedFrame) -> Unit,
    onRowDelete: (FlaggedFrame) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

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
                text = stringResource(R.string.queue_sheet_title, frames.size),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.styles.foreground,
                modifier = Modifier.padding(vertical = AgarthaSpacing.md),
            )

            if (frames.isEmpty()) {
                QueueEmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs),
                    modifier = Modifier.padding(bottom = AgarthaSpacing.md),
                ) {
                    items(
                        items = frames,
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
        Column(modifier = Modifier
            .weight(1f)
            .padding(horizontal = AgarthaSpacing.sm)) {
            Text(
                text = top?.classLabel ?: stringResource(R.string.queue_row_unknown_class),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.styles.foreground,
            )
            Text(
                text = stringResource(
                    R.string.queue_row_subtitle,
                    "%.0f".format((top?.confidence ?: 0f) * 100f),
                    frame.predictions.size,
                    timeText,
                ),
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

