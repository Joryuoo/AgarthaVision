package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgarthaTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            // 1. App Header (with theme toggle)
            item {
                com.agarthavision.ui.components.AppHeader(
                    isDarkMode = state.isDarkMode,
                    onToggleTheme = viewModel::onToggleTheme
                )
            }

            // 1b. Account / sync banner (ADR-007 offline access)
            item {
                Spacer(Modifier.height(Spacing.sm))
                AccountSyncBanner(
                    state = state,
                    onSignIn = { onNavigate(Screen.Login.route) },
                    onSyncNow = viewModel::onSyncNow,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }

            // 2. Active Session Hero (only when active)
            state.activeSession?.let { session ->
                item {
                    ActiveSessionHero(
                        sessionId    = session.label,
                        elapsed      = session.startedAtAgo,
                        frameCount   = session.totalFrames.toIntOrNull() ?: 0,
                        onResume     = { onNavigate(Screen.Capture.route) },
                        modifier     = Modifier.padding(horizontal = Spacing.xl)
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 3. Today's Activity KPI Grid
            item {
                SectionLabel(
                    "Today's activity",
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
            item {
                Spacer(Modifier.height(Spacing.sm))
                KpiGrid(
                    kpis = state.kpis,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }

            // 4. Sparkline card
            item {
                Spacer(Modifier.height(Spacing.lg))
                SparklineCard(
                    values = if (state.epgSparklineData.size == 7) state.epgSparklineData
                             else List(7) { 0f },
                    delta  = "+38%",
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }

            // 5. Species mix card
            if (state.topSpecies.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    SpeciesMixCard(
                        speciesData = state.topSpecies,
                        modifier    = Modifier.padding(horizontal = Spacing.xl)
                    )
                }
            }

            // 6. Verify alert row
            if (state.pendingReviewCount > 0) {
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    VerifyAlertRow(
                        pendingCount = state.pendingReviewCount,
                        oldestAgo    = state.oldestPendingAgo,
                        onClick      = { onNavigate(Screen.VerificationQueue.route) },
                        modifier     = Modifier.padding(horizontal = Spacing.xl)
                    )
                }
            }

            // 7. Sync status row
            item {
                Spacer(Modifier.height(Spacing.md))
                SyncStatusRow(
                    allSynced      = state.allSynced,
                    lastSyncLabel  = state.lastSyncLabel,
                    samplesSynced  = state.syncedSamplesCount,
                    modifier       = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        }
    }
}

// ─── Screen chrome ───────────────────────────────────────────────────────────

/**
 * Account / sync banner (ADR-007). Signed-out shows a "Sign in" CTA; signed-in shows
 * pending-upload state with a "Sync now" action (disabled while offline or syncing).
 */
@Composable
private fun AccountSyncBanner(
    state: DashboardUiState,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val hasPending = state.pendingUploadCount > 0
    // Neutral surface when signed-out; amber when items await upload; quiet green when clear.
    val (bg, border) = when {
        !state.isSignedIn -> colors.surface to colors.border
        hasPending -> colors.warningTint to colors.warning
        else -> colors.successTint to colors.success
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusText = when {
            !state.isSignedIn && hasPending ->
                stringResource(R.string.dashboard_pending_upload, state.pendingUploadCount)
            !state.isSignedIn -> stringResource(R.string.dashboard_signed_out)
            state.isSyncing -> stringResource(R.string.dashboard_syncing)
            hasPending -> stringResource(R.string.dashboard_pending_upload, state.pendingUploadCount)
            else -> stringResource(R.string.dashboard_all_synced)
        }
        Text(
            text = statusText,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!state.isSignedIn) {
            BannerAction(text = stringResource(R.string.dashboard_sign_in), enabled = true, onClick = onSignIn)
        } else {
            BannerAction(
                text = if (state.isSyncing) stringResource(R.string.dashboard_syncing)
                       else stringResource(R.string.dashboard_sync_now),
                enabled = state.canSyncNow,
                onClick = onSyncNow,
            )
        }
    }
}

@Composable
private fun BannerAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AgarthaTheme.colors
    Text(
        text = text,
        color = if (enabled) colors.accent else colors.textTertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textSecondary,
        letterSpacing = 1.1.sp,
        modifier = modifier
    )
}
