package com.agarthavision.ui.sessions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.components.recordedByText
import com.agarthavision.ui.theme.AgarthaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A session card's meta line: date and time, then whose it is when it is a colleague's. */
internal fun sessionMeta(date: String, time: String, recordedBy: String?): String =
    if (recordedBy == null) "$date · $time" else "$date · $time · $recordedBy"

/**
 * A colleague's row opens Session Detail to read; the medtech's own row resumes Capture.
 * A colleague's session is read-only (14zcqntjph6), so capturing into it would add to someone
 * else's record.
 */
internal fun sessionRowClick(isColleagueSession: Boolean, openDetail: () -> Unit, resume: () -> Unit): () -> Unit =
    if (isColleagueSession) openDetail else resume

/** Callbacks [SessionCard] (and its long-press Rename menu) dispatch back to the caller. */
internal data class SessionCardActions(
    val onClick: () -> Unit,
    val onVerifyClick: () -> Unit = {},
    val onViewReportClick: () -> Unit = {},
    val onRenameClick: () -> Unit = {},
)

internal enum class SessionQueueBadge { NO_ITEMS, ALL_VERIFIED, PENDING }

internal fun sessionQueueBadge(totalSamples: Int, unverified: Int): SessionQueueBadge = when {
    totalSamples == 0 -> SessionQueueBadge.NO_ITEMS
    unverified == 0 -> SessionQueueBadge.ALL_VERIFIED
    else -> SessionQueueBadge.PENDING
}

/** Loading placeholder for [SessionCard], modeled on RecordsScreen's `ReportCardSkeleton`. */
@Composable
internal fun SessionCardSkeleton(modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.width(140.dp).height(19.dp))
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.width(100.dp).height(13.dp))
        }
        SkeletonBox(modifier = Modifier.width(48.dp).height(24.dp))
    }
}

@Composable
internal fun LiveDot() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(AgarthaTheme.colors.onAccentTint.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun SessionCardBadge(
    sessionData: SessionWithStats,
    isActive: Boolean,
    actions: SessionCardActions,
) {
    val colors = AgarthaTheme.colors
    if (isActive) {
        val unverified = sessionData.unverifiedSamples
        val queueBadge = sessionQueueBadge(sessionData.totalSamples, unverified)
        val hasPending = unverified > 0
        val (badgeBg, badgeTextColor) = if (hasPending) {
            colors.accentTint to colors.onAccentTint
        } else {
            colors.surfaceMuted to colors.textSecondary
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(badgeBg)
                .then(
                    when (queueBadge) {
                        SessionQueueBadge.PENDING -> Modifier.clickable { actions.onVerifyClick() }
                        SessionQueueBadge.ALL_VERIFIED -> Modifier.clickable { actions.onViewReportClick() }
                        SessionQueueBadge.NO_ITEMS -> Modifier
                    },
                )
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (hasPending) LiveDot()
            Text(
                text = when (queueBadge) {
                    SessionQueueBadge.NO_ITEMS ->
                        stringResource(R.string.session_no_items_yet)
                    SessionQueueBadge.ALL_VERIFIED ->
                        stringResource(R.string.session_all_verified)
                    SessionQueueBadge.PENDING ->
                        pluralStringResource(
                            R.plurals.session_unverified_count,
                            unverified,
                            unverified,
                        )
                },
                color = badgeTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        val eggs = sessionData.totalEggs
        val badgeBg = if (eggs > 0) colors.successTint else colors.surfaceMuted
        val badgeColor = if (eggs > 0) colors.successText else colors.textSecondary
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(badgeBg, CircleShape)
                .clickable { actions.onViewReportClick() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.sessions_eggs_count, eggs, eggs),
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun SessionCard(
    sessionData: SessionWithStats,
    isActive: Boolean,
    actions: SessionCardActions,
    colleagueAuthor: String? = null,
    isColleagueSession: Boolean = false,
) {
    val colors = AgarthaTheme.colors
    val session = sessionData.session
    val date = formatDate(session.startedAt)
    val time = formatTime(session.startedAt)
    val recordedBy = recordedByText(colleagueAuthor)
    val meta = sessionMeta(date, time, recordedBy.takeIf { isColleagueSession })

    val (bgColor, borderColor) = if (isActive) {
        colors.accentTint2 to colors.accentTint
    } else {
        colors.surface to colors.border
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.sessions_rename_session_action)
    val longPressLabel = stringResource(R.string.session_card_long_press_label)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = actions.onClick,
                    onLongClick = if (isColleagueSession) null else ({ menuExpanded = true }),
                    onLongClickLabel = if (isColleagueSession) null else longPressLabel,
                )
                .semantics {
                    if (!isColleagueSession) {
                        customActions = listOf(
                            CustomAccessibilityAction(renameLabel) {
                                actions.onRenameClick()
                                true
                            },
                        )
                    }
                }
                .testTag("sessionCard_${session.id}")
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.label ?: "Session ${session.id.take(8)}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    letterSpacing = (-0.015).em,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = meta,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SessionCardBadge(
                sessionData = sessionData,
                isActive = isActive,
                actions = actions,
            )
        }
        if (!isColleagueSession) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sessions_rename_action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        actions.onRenameClick()
                    },
                )
            }
        }
    }
}

internal fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
