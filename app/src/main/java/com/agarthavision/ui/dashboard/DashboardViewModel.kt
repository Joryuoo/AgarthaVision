package com.agarthavision.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * `userName` and `dateString` are gone. Neither was ever assigned and neither was read by any
 * composable - they were leftovers that would mislead the next person who greps for where the
 * dashboard gets its user name, which is `ObserveLocalIdentityUseCase`.
 *
 * `eggsSparklineData` is gone with the card it fed. A seven-day trend of egg counts across
 * different patients is not a meaningful aggregate, and the delta printed beside it was the
 * string literal "+38%" - it had never reflected any data at all.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val activeSession: ActiveSessionState? = null,
    val kpis: KpiState = KpiState(),
    val topSpecies: List<SpeciesData> = emptyList(),
    val pendingReviewCount: Int = 0,
    val oldestPendingAgo: String = "",
    val allSynced: Boolean = true,
    val lastSyncLabel: String = "—",
    val syncedSamplesCount: Int = 0,
    val isDarkMode: Boolean = false,
    // ADR-007 offline-access account/sync banner state.
    val isSignedIn: Boolean = false,
    val isOffline: Boolean = false,
    val pendingUploadCount: Int = 0,
    val isSyncing: Boolean = false,
) {
    /** Sync-now is available only to a signed-in medtech with an online connection. */
    val canSyncNow: Boolean
        get() = isSignedIn && !isOffline && !isSyncing
}

data class ActiveSessionState(
    val label: String,
    val updatedAtAgo: String,
    val totalFrames: String,
    val verifiedFrames: String,
    val totalEggs: String,
    val pendingFrames: String,
)

/**
 * The Home tab's counts. **Every one of these is a row count from the database.**
 *
 * Two fields went rather than being re-pointed. `verifiedRatio` returned "100%" whenever any
 * sample existed and "0%" otherwise - a constant wearing a percent sign. `eggsAvgStatus`
 * returned "Elevated" or "Baseline" from `totalSamples > 100`: a sample *count* dressed as a
 * clinical intensity, and it read as one on a screen used during validation.
 *
 * The bar for anything added here: if a tile cannot be explained by pointing at a row, it does
 * not ship.
 */
data class KpiState(
    val patientsCount: String = "0",
    val sessionsCount: String = "0",
    val samplesCount: String = "0",
    val pendingCount: String = "0",
)

data class SpeciesData(
    val name: String,
    val ratio: Float,
    val formattedPercentage: String
)

data class PendingAndSync(
    val pendingCount: Int,
    val oldestPendingAgo: String,
    val allSynced: Boolean,
    val lastSyncLabel: String,
    val syncedSamplesCount: Int,
    val initialFetchDone: Boolean = true,
)

@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase,
    private val initialFetchStateStore: InitialFetchStateStore,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val sampleDao: SampleDao,
    private val patientRepository: PatientRepository,
    private val detectionRepository: DetectionRepository,
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
) : ViewModel() {

    private val themeModeFlow = observeThemeModeUseCase()

    // Per ADR-007, drive identity from the cached local identity (survives offline cold
    // starts) rather than the live Supabase session, so the dashboard renders signed-out.
    private val localIdentityFlow = observeLocalIdentityUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val userIdFlow = localIdentityFlow
        .map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // KPI State — tolerates a null identity (signed-out / offline) by showing empty stats
    // instead of stalling the dashboard. Per ADR-007.
    private val kpiStateFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(KpiState())
        } else {
            combine(
                patientRepository.observePatientCount(userId, ""),
                sessionRepository.observeAllSessions(userId),
                // Verified samples only - that is what this query returns, and the tile is
                // labelled for it rather than calling them "samples" and rounding the meaning.
                sampleRepository.observeAllSamples(userId),
                sampleDao.observePendingCount(userId),
            ) { patients, sessions, verifiedSamples, pending ->
                KpiState(
                    patientsCount = patients.toString(),
                    sessionsCount = sessions.size.toString(),
                    samplesCount = verifiedSamples.size.toString(),
                    pendingCount = pending.toString(),
                )
            }
        }
    }

    // Pending Reviews + Sync Status — null identity yields an empty (all-synced) state.
    private val pendingAndSyncFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(
                PendingAndSync(
                    0,
                    "",
                    allSynced = true,
                    lastSyncLabel = "never",
                    syncedSamplesCount = 0,
                    initialFetchDone = true,
                ),
            )
        } else {
        combine(
            flow { emit(sampleRepository.getSamplesPendingSyncIncludingDeleted(userId)) },
            sampleRepository.observeAllSamples(userId),
            initialFetchStateStore.observeCompleted(userId),
        ) { pendingSamples, allSamples, initialFetchDone ->
            val pendingCount = pendingSamples.size

            // Oldest pending: find the earliest timestamp among unverified flagged samples
            val oldestPendingMs = pendingSamples.minOfOrNull { it.timestamp }
            val oldestPendingAgo = if (oldestPendingMs != null) {
                val diffMs = System.currentTimeMillis() - oldestPendingMs
                when {
                    diffMs < MILLIS_PER_MINUTE -> "${diffMs / MILLIS_PER_SECOND}s ago"
                    diffMs < MILLIS_PER_HOUR -> "${diffMs / MILLIS_PER_MINUTE}m ago"
                    diffMs < MILLIS_PER_DAY -> "${diffMs / MILLIS_PER_HOUR}h ago"
                    else -> "${diffMs / MILLIS_PER_DAY}d ago"
                }
            } else ""

            // Sync status: all synced when no VERIFIED (unsynced) samples exist AND initial
            // fetch has completed (per 3d: initialFetchDone && unsyncedCount == 0)
            val unsyncedCount = allSamples.count {
                it.status == com.agarthavision.domain.model.SampleStatus.VERIFIED
            }
            val syncedSamples = allSamples.count {
                it.status == com.agarthavision.domain.model.SampleStatus.SYNCED
            }
            val allSynced = initialFetchDone && unsyncedCount == 0

            // Last sync label: time since the most recently synced sample
            val lastSyncedMs = allSamples
                .filter { it.status == com.agarthavision.domain.model.SampleStatus.SYNCED }
                .maxOfOrNull { it.verifiedAt }
            val lastSyncLabel = if (lastSyncedMs != null) {
                val diffMs = System.currentTimeMillis() - lastSyncedMs
                when {
                    diffMs < MILLIS_PER_MINUTE -> "just now"
                    diffMs < MILLIS_PER_HOUR -> "${diffMs / MILLIS_PER_MINUTE}m ago"
                    diffMs < MILLIS_PER_DAY -> "${diffMs / MILLIS_PER_HOUR}h ago"
                    else -> "${diffMs / MILLIS_PER_DAY}d ago"
                }
            } else "never"

            PendingAndSync(
                pendingCount     = pendingCount,
                oldestPendingAgo = oldestPendingAgo,
                allSynced        = allSynced,
                lastSyncLabel    = lastSyncLabel,
                syncedSamplesCount = syncedSamples,
                initialFetchDone = initialFetchDone,
            )
        }
        }
    }

    // Active Session
    private val activeSessionStateFlow = sessionManager.state.flatMapLatest { state ->
        when (state) {
            is SessionState.Idle -> flowOf(null)
            is SessionState.Active -> {
                val userId = state.session.userId ?: ""
                sampleRepository.observeSamplesForSession(state.session.sessionId, userId)
                    .map { samples ->
                        val now = Instant.now()

                        // "Updated" tracks the session's most recent activity - the latest
                        // frame capture or verification - not when it started, since the card
                        // now surfaces recent (not live) sessions. Falls back to the start time
                        // when a session has no samples yet.
                        val lastActivityMs = samples.maxOfOrNull { maxOf(it.timestamp, it.verifiedAt) }
                        val lastUpdated = lastActivityMs?.let { Instant.ofEpochMilli(it) }
                            ?: state.startedAt

                        // Calculate stats
                        val totalFrames = samples.size
                        val verifiedFrames = samples.count { it.verifiedAt > 0 }
                        val pendingFrames = totalFrames - verifiedFrames
                        val totalEggsCount = 0 // Mocked for now, requires deeper join

                        ActiveSessionState(
                            label = state.session.label ?: "Active Session",
                            updatedAtAgo = updatedAgoLabel(lastUpdated, now),
                            totalFrames = totalFrames.toString(),
                            verifiedFrames = verifiedFrames.toString(),
                            totalEggs = totalEggsCount.toString(), // Mocked
                            pendingFrames = pendingFrames.toString()
                        )
                    }
            }
        }
    }

    /**
     * The species mix across the last seven days. Real: a group-by over confirmed egg counts.
     *
     * The sparkline that used to share this flow is gone. A seven-day trend of egg counts
     * across different patients is not a meaningful aggregate, and inventing one repeats the
     * mistake PB-16 removed.
     */
    private val topSpeciesFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(emptyList())
        } else {
            val sevenDaysAgo = Instant.now()
                .minus(Duration.ofDays(HISTORICAL_DAYS.toLong()))
                .toEpochMilli()
            detectionRepository.observeConfirmedEggCountsSince(userId, sevenDaysAgo).map { eggCounts ->
                val totalEggs = eggCounts.sumOf { it.count }.coerceAtLeast(1)
                eggCounts.take(TOP_SPECIES_COUNT).map {
                    val ratio = it.count.toFloat() / totalEggs
                    SpeciesData(
                        name = it.species,
                        ratio = ratio,
                        formattedPercentage = "${(ratio * 100).toInt()}%",
                    )
                }
            }
        }
    }

    /** True while a manual "Sync now" pass is running. */
    private val isSyncingFlow = MutableStateFlow(false)

    // ADR-007 account/sync banner: signed-in derives from cached identity; offline from
    // the connectivity observer; syncing from the manual sync-now action.
    private val accountSyncFlow = combine(
        localIdentityFlow,
        connectivityObserver.isOnline,
        isSyncingFlow,
    ) { identity, online, syncing ->
        Triple(identity != null, !online, syncing)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        kpiStateFlow,
        pendingAndSyncFlow,
        activeSessionStateFlow,
        topSpeciesFlow,
        themeModeFlow,
        accountSyncFlow,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val kpis = flows[0] as KpiState
        @Suppress("UNCHECKED_CAST")
        val pendingSync = flows[1] as PendingAndSync
        val activeSession = flows[2] as ActiveSessionState?
        @Suppress("UNCHECKED_CAST")
        val topSpecies = flows[TOP_SPECIES_FLOW_INDEX] as List<SpeciesData>
        val themeMode = flows[THEME_MODE_FLOW_INDEX] as ThemeMode
        @Suppress("UNCHECKED_CAST")
        val accountSync = flows[ACCOUNT_SYNC_FLOW_INDEX] as Triple<Boolean, Boolean, Boolean>
        val (isSignedIn, isOffline, isSyncing) = accountSync
        DashboardUiState(
            isLoading           = false,
            kpis                = kpis,
            pendingReviewCount  = pendingSync.pendingCount,
            oldestPendingAgo    = pendingSync.oldestPendingAgo,
            allSynced           = pendingSync.allSynced,
            lastSyncLabel       = pendingSync.lastSyncLabel,
            syncedSamplesCount  = pendingSync.syncedSamplesCount,
            activeSession       = activeSession,
            topSpecies          = topSpecies,
            isDarkMode          = themeMode == ThemeMode.DARK,
            isSignedIn          = isSignedIn,
            isOffline           = isOffline,
            pendingUploadCount  = pendingSync.pendingCount,
            isSyncing           = isSyncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    /** Flips the persisted theme between light and dark. */
    fun onToggleTheme() {
        val target = if (uiState.value.isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            setThemeModeUseCase(target).onFailure { error ->
                Log.e(TAG, "Failed to persist theme mode $target", error)
            }
        }
    }

    /**
     * Relative "Updated ..." label for the recent-session card: minutes under an hour, whole
     * hours under a day, whole days beyond that.
     */
    private fun updatedAgoLabel(lastUpdated: Instant, now: Instant): String {
        val elapsed = Duration.between(lastUpdated, now)
        val minutes = elapsed.toMinutes()
        val hours = elapsed.toHours()
        val days = elapsed.toDays()
        return when {
            minutes < 1 -> "Updated just now"
            minutes < MINUTES_PER_HOUR -> "Updated $minutes min ago"
            hours < HOURS_PER_DAY -> "Updated $hours ${if (hours == 1L) "hr" else "hrs"} ago"
            else -> "Updated $days ${if (days == 1L) "day" else "days"} ago"
        }
    }

    /** Runs a manual pending-sync pass (push + pull). Per ADR-007. */
    fun onSyncNow() {
        if (!uiState.value.canSyncNow) return
        viewModelScope.launch {
            isSyncingFlow.value = true
            syncPendingDataUseCase().onFailure { error ->
                Log.e(TAG, "Manual sync (push) failed", error)
            }
            fetchRemoteDataUseCase().onFailure { error ->
                Log.e(TAG, "Manual sync (fetch) failed", error)
            }
            isSyncingFlow.value = false
        }
    }

    private companion object {
        const val TAG = "DashboardViewModel"
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val MILLIS_PER_DAY = 86_400_000L
        const val MINUTES_PER_HOUR = 60L
        const val HOURS_PER_DAY = 24L
        const val HISTORICAL_DAYS = 7
        const val TOP_SPECIES_COUNT = 3

        // Positional indices into the combine(...) `flows` array above (kpiStateFlow=0,
        // pendingAndSyncFlow=1, activeSessionStateFlow=2, topSpeciesFlow=3,
        // themeModeFlow=4, accountSyncFlow=5).
        const val TOP_SPECIES_FLOW_INDEX = 3
        const val THEME_MODE_FLOW_INDEX = 4
        const val ACCOUNT_SYNC_FLOW_INDEX = 5
    }
}
