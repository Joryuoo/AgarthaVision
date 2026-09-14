package com.agarthavision.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgarthaTheme.colors.background)
            // Inset here rather than on the header item so a scrolled dashboard never
            // slides its content under the phone's status bar.
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            // 1. App Header (with sync action)
            item {
                com.agarthavision.ui.components.AppHeader(
                    isSyncing = state.isSyncing,
                    needsSync = state.isSignedIn && state.pendingUploadCount > 0 && !state.isSyncing,
                    onSync = {
                        val message = when {
                            !state.isSignedIn -> R.string.dashboard_sync_toast_signed_out
                            state.isOffline -> R.string.dashboard_sync_toast_offline
                            state.isSyncing -> R.string.dashboard_syncing
                            state.pendingUploadCount == 0 -> R.string.dashboard_all_synced
                            else -> R.string.dashboard_sync_toast_started
                        }
                        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                        viewModel.onSyncNow()
                    }
                )
            }

            // 2. Recent Session Hero (only when a session is active/recent)
            state.activeSession?.let { session ->
                item {
                    ActiveSessionHero(
                        sessionId    = session.label,
                        elapsed      = session.updatedAtAgo,
                        frameCount   = session.totalFrames.toIntOrNull() ?: 0,
                        onResume     = { onNavigate(Screen.Capture.route) },
                        modifier     = Modifier.padding(horizontal = Spacing.xl)
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 3. Today's Activity KPI Grid
            if (state.activeSession == null) {
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
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

        }
    }
}

// ─── Screen chrome ───────────────────────────────────────────────────────────

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
