package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.agarthavision.ui.icons.Warning
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeedsAttentionStrip(
    needsAttention: NeedsAttention,
    onNavigateToTab: (String) -> Unit,
    onOpenSessionList: (SessionListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (needsAttention.isEmpty) return

    val colors = AgarthaTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.goldTint)
            .border(1.dp, colors.gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AgarthaIcons.Warning,
                    contentDescription = null,
                    tint = colors.onGold,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "NEEDS ATTENTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.goldText,
                letterSpacing = 1.sp,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (needsAttention.unsyncedItems > 0) {
                val label = pluralStringResource(
                    R.plurals.attention_unsynced,
                    needsAttention.unsyncedItems,
                    needsAttention.unsyncedItems,
                )
                AttentionChip(
                    text = label,
                    spokenDescription = "$label. Opens settings.",
                    onClick = { onNavigateToTab(Screen.Settings.route) },
                )
            }

            if (needsAttention.framesToReview > 0) {
                val label = pluralStringResource(
                    R.plurals.attention_to_review,
                    needsAttention.framesToReview,
                    needsAttention.framesToReview,
                )
                AttentionChip(
                    text = label,
                    spokenDescription = "$label. Opens frames to review.",
                    onClick = { onOpenSessionList(SessionListFilter.TO_REVIEW) },
                )
            }

            if (needsAttention.emptySessions > 0) {
                val label = pluralStringResource(
                    R.plurals.attention_empty_sessions,
                    needsAttention.emptySessions,
                    needsAttention.emptySessions,
                )
                AttentionChip(
                    text = label,
                    spokenDescription = "$label. Opens sessions with no frames.",
                    onClick = { onOpenSessionList(SessionListFilter.NO_FRAMES) },
                )
            }
        }
    }
}

@Composable
private fun AttentionChip(
    text: String,
    spokenDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = spokenDescription
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
        Icon(
            imageVector = AgarthaIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}
