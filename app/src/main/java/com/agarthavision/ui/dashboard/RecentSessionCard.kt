package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.ui.sessions.SessionQueueBadge
import com.agarthavision.ui.sessions.sessionQueueBadge
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
internal fun RecentSessionCard(
    sessionSummary: SessionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val session = sessionSummary.session
    val label = session.label ?: "Session ${session.id.take(8)}"
    val queueBadge = sessionQueueBadge(sessionSummary.totalFrames, sessionSummary.framesToReview)
    val now = remember { System.currentTimeMillis() }
    val relativeTime = shortRelativeTimeText(sessionSummary.lastActivityAt, now)

    val badgeText = when (queueBadge) {
        SessionQueueBadge.NO_ITEMS -> "No frames"
        SessionQueueBadge.ALL_VERIFIED -> "Verified"
        SessionQueueBadge.PENDING -> "To review"
    }

    val (badgeBg, badgeColor) = when (queueBadge) {
        SessionQueueBadge.NO_ITEMS -> colors.surfaceVariant to colors.textSecondary
        SessionQueueBadge.ALL_VERIFIED -> colors.successTint to colors.successText
        SessionQueueBadge.PENDING -> colors.goldTint to colors.goldText
    }

    val shape = RoundedCornerShape(16.dp)
    val frameCountText = pluralStringResource(
        R.plurals.dashboard_frame_count,
        sessionSummary.totalFrames,
        sessionSummary.totalFrames,
    )
    val subtitleText = "$frameCountText · $relativeTime"

    val patientName = sessionSummary.patient.maskedDisplayName
    val cardDescription = "$label, $patientName, $subtitleText, $badgeText. Opens session details."

    Box(
        modifier = modifier
            .testTag("recentSessionCard_${session.id}")
            .width(156.dp)
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = cardDescription
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = TextStyle(fontFeatureSettings = "tnum"),
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = patientName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitleText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = TextStyle(fontFeatureSettings = "tnum"),
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                )
            }
        }
    }
}
