package com.agarthavision.ui.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.AgarthaButtonVariant
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Science
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
    onNavigateToTab: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAiAgreementSheet by rememberSaveable { mutableStateOf(false) }
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

            // 1b. Needs Attention strip
            if (!state.needsAttention.isEmpty) {
                item {
                    Spacer(Modifier.height(Spacing.xs))
                    NeedsAttentionStrip(
                        needsAttention = state.needsAttention,
                        onNavigateToTab = onNavigateToTab,
                        onOpenSessionList = { filter ->
                            onNavigate(Screen.SessionList.createRoute(filter))
                        },
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            // 2. Recent Sessions section
            recentSessionsSection(state, onNavigate, onNavigateToTab)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Activity")
                    PeriodToggle(
                        selected = state.period,
                        onSelect = viewModel::onPeriodSelected,
                    )
                }
            }
            item {
                Spacer(Modifier.height(Spacing.sm))
                KpiPager(
                    tiles = state.kpiTiles,
                    isLoading = state.isLoading,
                    period = state.period,
                    onTileClick = { kind ->
                        when (kind) {
                            KpiKind.SESSIONS -> onNavigate(
                                Screen.SessionList.createRoute(SessionListFilter.ALL, state.period)
                            )
                            KpiKind.POSITIVE_RATE -> onNavigate(
                                Screen.SessionList.createRoute(SessionListFilter.EXAMINED, state.period)
                            )
                            KpiKind.TO_REVIEW -> onNavigate(
                                Screen.SessionList.createRoute(SessionListFilter.TO_REVIEW, state.period)
                            )
                            KpiKind.AI_AGREEMENT -> {
                                showAiAgreementSheet = true
                            }
                        }
                    },
                    onOpenCoverage = { period -> onNavigate(Screen.MyCoverage.createRoute(period)) },
                    modifier = Modifier.padding(horizontal = Spacing.xl),
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
                        title = state.findingsTitle,
                        positiveSmearsCount = state.positiveSmearsCount,
                        speciesData = state.topSpecies,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
            }

            // 5. Recent activity feed
            item {
                Spacer(Modifier.height(Spacing.lg))
                RecentActivityCard(
                    items = state.recentActivity,
                    nowMillis = remember { System.currentTimeMillis() },
                    onItemClick = { activityItem -> onActivityItemClick(activityItem, onNavigate, onNavigateToTab) },
                    onSeeAllClick = { onNavigate(Screen.Activity.route) },
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        }

        if (showAiAgreementSheet) {
            AiAgreementSheet(
                breakdown = state.aiBreakdown,
                onDismiss = { showAiAgreementSheet = false },
            )
        }
    }
}

/** Routes a recent-activity row tap to whatever screen best explains that item. */
internal fun onActivityItemClick(
    activityItem: ActivityItem,
    onNavigate: (String) -> Unit,
    onNavigateToTab: (String) -> Unit,
) {
    when (activityItem) {
        is ActivityItem.FramesVerified -> onNavigate(Screen.SessionDetail.createRoute(activityItem.sessionId))
        is ActivityItem.FramesCaptured -> onNavigate(Screen.SessionDetail.createRoute(activityItem.sessionId))
        is ActivityItem.SessionStarted -> onNavigate(Screen.SessionDetail.createRoute(activityItem.sessionId))
        is ActivityItem.PatientAdded -> onNavigate(Screen.PatientSessions.createRoute(activityItem.patientId))
        is ActivityItem.SyncFinished -> onNavigateToTab(Screen.Settings.route)
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

private fun LazyListScope.recentSessionsSection(
    state: DashboardUiState,
    onNavigate: (String) -> Unit,
    onNavigateToTab: (String) -> Unit,
) {
    item {
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Recent sessions")
            if (state.hasAnySession || state.activeSession != null) {
                Text(
                    text = "See all",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgarthaTheme.colors.accent,
                    modifier = Modifier
                        .clickable {
                            onNavigate(Screen.SessionList.createRoute(SessionListFilter.ALL))
                        }
                        .padding(vertical = Spacing.xs),
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }

    if (!state.hasAnySession && state.activeSession == null) {
        item {
            EmptyState(
                icon = AgarthaIcons.Science,
                title = "No sessions yet",
                body = "Start a session from a patient's page",
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                action = {
                    AgarthaButton(
                        onClick = { onNavigateToTab("patients") },
                        variant = AgarthaButtonVariant.Primary,
                    ) {
                        Text("Go to Patients")
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    } else {
        renderActiveAndRecentSessions(state, onNavigate)
    }
}

private fun LazyListScope.renderActiveAndRecentSessions(
    state: DashboardUiState,
    onNavigate: (String) -> Unit,
) {
    state.activeSession?.let { session ->
        item {
            val nowMillis = remember { System.currentTimeMillis() }
            ActiveSessionHero(
                sessionId = session.label,
                elapsed = relativeTimeText(session.lastActivityAt, nowMillis),
                frameCount = session.totalFrames,
                onResume = { onNavigate(Screen.Capture.route) },
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (state.recentSessions.isNotEmpty()) {
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(
                    items = state.recentSessions,
                    key = { it.session.id },
                ) { sessionSummary ->
                    RecentSessionCard(
                        sessionSummary = sessionSummary,
                        onClick = {
                            onNavigate(Screen.SessionDetail.createRoute(sessionSummary.session.id))
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    } else if (state.activeSession != null) {
        item {
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
