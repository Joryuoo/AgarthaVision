package com.agarthavision.ui.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/** Minimum gap between accepted sync-button taps, to stop spamming the sync action. */
private const val SYNC_TAP_COOLDOWN_MS = 3_000L

/** Toast shown for a sync-button tap, chosen from the current account/sync state. */
private fun syncToastMessage(state: DashboardUiState): Int = when {
    !state.isSignedIn -> R.string.dashboard_sync_toast_signed_out
    state.isOffline -> R.string.dashboard_sync_toast_offline
    state.isSyncing -> R.string.dashboard_syncing
    state.pendingUploadCount == 0 -> R.string.dashboard_all_synced
    else -> R.string.dashboard_sync_toast_started
}

/** Shows a toast, cancelling any still-visible one first so rapid taps can't stack them. */
private fun showSyncToast(context: Context, holder: Array<Toast?>, messageRes: Int) {
    holder[0]?.cancel()
    holder[0] = Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT)
        .also { it.show() }
}

/**
 * Handles a sync-button tap: inside the [SYNC_TAP_COOLDOWN_MS] window it only warns that a sync
 * just ran ([lastTapMs] is a single-slot timestamp holder); otherwise it toasts the sync status
 * and kicks off a sync. [toastHolder] dedupes toasts so spamming the button never stacks them.
 */
private fun handleSyncTap(
    context: Context,
    state: DashboardUiState,
    lastTapMs: LongArray,
    toastHolder: Array<Toast?>,
    onSyncNow: () -> Unit,
) {
    val now = System.currentTimeMillis()
    if (now - lastTapMs[0] < SYNC_TAP_COOLDOWN_MS) {
        showSyncToast(context, toastHolder, R.string.dashboard_sync_toast_rate_limited)
        return
    }
    lastTapMs[0] = now
    showSyncToast(context, toastHolder, syncToastMessage(state))
    onSyncNow()
}

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Rate-limit the sync button so rapid taps can't fire a burst of sync passes (or toasts).
    val lastSyncTapMs = remember { longArrayOf(0L) }
    val syncToastHolder = remember { arrayOfNulls<Toast>(1) }

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
                        handleSyncTap(context, state, lastSyncTapMs, syncToastHolder) {
                            viewModel.onSyncNow()
                        }
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

            // 4. Species mix card
            //
            // The seven-day sparkline that stood here is gone (PB-23). Its delta was the string
            // literal "+38%" - it had never reflected any data - and a seven-day trend of egg
            // counts across different patients is not a meaningful aggregate to rebuild it
            // from. The species mix below is a real group-by and stays.
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
