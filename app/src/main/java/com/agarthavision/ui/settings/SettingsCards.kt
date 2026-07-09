package com.agarthavision.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.AgarthaButtonVariant
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)) {
        Text(
            text = title.uppercase(),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        content()
    }
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(Spacing.lg),
    ) {
        content()
    }
}

@Composable
internal fun AccountCard(
    identity: LocalIdentity?,
    isOffline: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    SettingsCard {
        if (identity != null) {
            Text(
                text = stringResource(R.string.settings_signed_in_as, identity.email),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.md))
            AgarthaButton(
                onClick = onSignOutClick,
                variant = AgarthaButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sign_out))
            }
        } else {
            Text(
                text = stringResource(R.string.settings_not_signed_in),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_not_signed_in_body),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(Spacing.md))
            AgarthaButton(
                onClick = onSignInClick,
                enabled = !isOffline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sign_in))
            }
        }
    }
}

/** Snapshot [SyncCard] renders from — bundled to keep the composable's parameter list small. */
internal data class SyncCardState(
    val isSignedIn: Boolean,
    val isOffline: Boolean,
    val counts: PendingSyncCounts,
    val isSyncing: Boolean,
    val canSyncNow: Boolean,
)

@Composable
internal fun SyncCard(state: SyncCardState, onSyncNowClick: () -> Unit) {
    val colors = AgarthaTheme.colors
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    if (state.isOffline) {
                        R.string.settings_connectivity_offline
                    } else {
                        R.string.settings_connectivity_online
                    },
                ),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            SyncStatusBadge(counts = state.counts)
        }
        if (state.isSignedIn) {
            SyncCounts(counts = state.counts)
            Spacer(Modifier.height(Spacing.md))
            AgarthaButton(
                onClick = onSyncNowClick,
                enabled = state.canSyncNow,
                variant = AgarthaButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSyncing) {
                        stringResource(R.string.settings_sync_syncing)
                    } else {
                        stringResource(R.string.settings_sync_now)
                    },
                )
            }
        }
    }
}

@Composable
private fun SyncCounts(counts: PendingSyncCounts) {
    val colors = AgarthaTheme.colors
    Spacer(Modifier.height(Spacing.md))
    SyncCountRow(label = stringResource(R.string.settings_sync_sessions), count = counts.pendingSessions)
    SyncCountRow(label = stringResource(R.string.settings_sync_samples), count = counts.pendingSamples)
    SyncCountRow(label = stringResource(R.string.settings_sync_reports), count = counts.pendingReports)
    if (counts.failed > 0) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.settings_sync_failed, counts.failed),
            color = colors.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SyncCountRow(label: String, count: Int) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
        Text(text = count.toString(), color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SyncStatusBadge(counts: PendingSyncCounts) {
    val colors = AgarthaTheme.colors
    val (bg, fg, text) = when {
        counts.failed > 0 -> Triple(
            colors.dangerTint,
            colors.dangerText,
            stringResource(R.string.settings_sync_failed, counts.failed),
        )
        counts.totalPending > 0 -> Triple(
            colors.warningTint,
            colors.warningText,
            stringResource(R.string.settings_sync_pending, counts.totalPending),
        )
        else -> Triple(colors.successTint, colors.successText, stringResource(R.string.settings_sync_all_synced))
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

