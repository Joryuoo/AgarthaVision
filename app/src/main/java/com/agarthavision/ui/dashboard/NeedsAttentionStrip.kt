package com.agarthavision.ui.dashboard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.usecase.home.NeedsAttention
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
fun NeedsAttentionStrip(
    needsAttention: NeedsAttention,
    onNavigateToTab: (String) -> Unit,
    onOpenSessionList: (SessionListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (needsAttention.isEmpty) return

    val colors = AgarthaTheme.colors
    val titleText = stringResource(R.string.attention_title)

    val parts = buildList {
        if (needsAttention.unsyncedItems > 0) {
            add(
                pluralStringResource(
                    R.plurals.attention_unsynced_summary,
                    needsAttention.unsyncedItems,
                    needsAttention.unsyncedItems,
                )
            )
        }
        if (needsAttention.framesToReview > 0) {
            add(
                pluralStringResource(
                    R.plurals.attention_to_review_summary,
                    needsAttention.framesToReview,
                    needsAttention.framesToReview,
                )
            )
        }
        if (needsAttention.emptySessions > 0) {
            add(
                pluralStringResource(
                    R.plurals.attention_empty_sessions,
                    needsAttention.emptySessions,
                    needsAttention.emptySessions,
                )
            )
        }
    }
    val summaryText = parts.joinToString(" · ")

    val onCardClick = {
        when {
            needsAttention.framesToReview > 0 -> onOpenSessionList(SessionListFilter.TO_REVIEW)
            needsAttention.unsyncedItems > 0 -> onNavigateToTab(Screen.Settings.route)
            needsAttention.emptySessions > 0 -> onOpenSessionList(SessionListFilter.NO_FRAMES)
            else -> Unit
        }
    }

    val a11yActions = buildList {
        if (needsAttention.framesToReview > 0) {
            add(
                CustomAccessibilityAction("Open frames to review") {
                    onOpenSessionList(SessionListFilter.TO_REVIEW)
                    true
                }
            )
        }
        if (needsAttention.unsyncedItems > 0) {
            add(
                CustomAccessibilityAction("Open settings to sync") {
                    onNavigateToTab(Screen.Settings.route)
                    true
                }
            )
        }
        if (needsAttention.emptySessions > 0) {
            add(
                CustomAccessibilityAction("Open sessions with no frames") {
                    onOpenSessionList(SessionListFilter.NO_FRAMES)
                    true
                }
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.goldTint)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$titleText: $summaryText"
                customActions = a11yActions
            }
            .clickable(onClick = onCardClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = colors.onGold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = titleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.goldText,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = summaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.goldText,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = AgarthaIcons.ChevronRight,
            contentDescription = null,
            tint = colors.goldText,
            modifier = Modifier.size(16.dp),
        )
    }
}
