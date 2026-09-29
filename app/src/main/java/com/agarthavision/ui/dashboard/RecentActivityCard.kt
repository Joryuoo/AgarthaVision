package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.PlayArrow
import com.agarthavision.ui.icons.Verified
import com.agarthavision.ui.theme.AppColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
internal fun RecentActivityCard(
    items: List<ActivityItem>,
    nowMillis: Long,
    onItemClick: (ActivityItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val shape = RoundedCornerShape(12.dp)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RECENT ACTIVITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = "See all",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier
                    .clickable(onClick = onSeeAllClick)
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape),
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.activity_empty),
                    body = "Your recent actions will show up here.",
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.take(MAX_ROWS).forEachIndexed { index, item ->
                        ActivityRow(
                            item = item,
                            nowMillis = nowMillis,
                            onClick = { onItemClick(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("activityRow_$index"),
                        )
                    }
                }
            }
        }
    }
}

/** Icon shown for [item]'s type, tinted inside a small circle. */
private fun activityIconFor(item: ActivityItem): ImageVector = when (item) {
    is ActivityItem.FramesVerified -> AgarthaIcons.Verified
    is ActivityItem.FramesCaptured -> Icons.Outlined.CameraAlt
    is ActivityItem.PatientAdded -> Icons.Outlined.PersonAdd
    is ActivityItem.SessionStarted -> AgarthaIcons.PlayArrow
    is ActivityItem.SyncFinished -> Icons.Outlined.CloudDone
}

@Composable
private fun activityTitleFor(item: ActivityItem): String = when (item) {
    is ActivityItem.FramesVerified -> pluralStringResource(
        R.plurals.activity_verified_frames,
        item.count,
        item.count,
    )
    is ActivityItem.FramesCaptured -> pluralStringResource(
        R.plurals.activity_captured_frames,
        item.count,
        item.count,
    )
    is ActivityItem.PatientAdded -> stringResource(R.string.activity_added_patient, item.maskedName)
    is ActivityItem.SessionStarted -> stringResource(R.string.activity_started_session)
    is ActivityItem.SyncFinished -> pluralStringResource(
        R.plurals.activity_synced_items,
        item.itemCount,
        item.itemCount,
    )
}

@Composable
private fun activitySubtitleFor(item: ActivityItem, nowMillis: Long): String = when (item) {
    is ActivityItem.FramesVerified ->
        "${item.sessionLabel ?: "Session"} · ${relativeTimeText(item.occurredAt, nowMillis)}"
    is ActivityItem.FramesCaptured ->
        "${item.sessionLabel ?: "Session"} · ${relativeTimeText(item.occurredAt, nowMillis)}"
    is ActivityItem.SessionStarted ->
        "${item.sessionLabel ?: "Session"} · ${relativeTimeText(item.occurredAt, nowMillis)}"
    is ActivityItem.PatientAdded -> relativeTimeText(item.occurredAt, nowMillis)
    is ActivityItem.SyncFinished -> relativeTimeText(item.occurredAt, nowMillis)
}

/**
 * Single activity row: a tinted icon circle, a title line and a subtitle line. Shared between
 * [RecentActivityCard] (home) and the full [com.agarthavision.ui.activity.ActivityScreen].
 */
@Composable
internal fun ActivityRow(
    item: ActivityItem,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val title = activityTitleFor(item)
    val subtitle = activitySubtitleFor(item, nowMillis)
    val description = "$title, $subtitle."

    val (iconBg, iconTint) = when (item) {
        is ActivityItem.SessionStarted -> colors.gold to AppColors.White
        is ActivityItem.FramesCaptured -> colors.accent to AppColors.White
        is ActivityItem.FramesVerified -> colors.success to AppColors.White
        is ActivityItem.PatientAdded -> colors.surfaceMuted to colors.accent
        is ActivityItem.SyncFinished -> colors.surfaceMuted to colors.accent
    }

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = activityIconFor(item),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private const val MAX_ROWS = 5
