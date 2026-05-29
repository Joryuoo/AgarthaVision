package com.agarthavision.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val userName: String = "M. Santos",
    val dateString: String = "Wednesday, May 28 · Day 12", // mocked date for now
    val activeSession: ActiveSessionState? = null,
    val kpis: KpiState = KpiState(),
    val epgSparklineData: List<Float> = emptyList(), // Array of exactly 7 items
    val topSpecies: List<SpeciesData> = emptyList(),
    val pendingReviewCount: Int = 0,
    val oldestPendingAgo: String = "",
    val allSynced: Boolean = true,
    val lastSyncLabel: String = "—",
    val syncedSamplesCount: Int = 0,
)

data class ActiveSessionState(
    val label: String,
    val startedAtAgo: String,
    val isRecording: Boolean,
    val totalFrames: String,
    val verifiedFrames: String,
    val totalEpg: String,
    val pendingFrames: String,
)

data class KpiState(
    val sessionsCount: String = "0",
    val samplesCount: String = "0",
    val verifiedRatio: String = "0%",
    val epgAvgStatus: String = "Normal"
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
    val syncedSamplesCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val detectionRepository: DetectionRepository,
) : ViewModel() {

    private val userIdFlow = flow {
        val uid = authRepository.getCurrentUserId()
        if (uid != null) emit(uid)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // KPI State
    private val kpiStateFlow = userIdFlow.filterNotNull().flatMapLatest { userId ->
        combine(
            sessionRepository.observeAllSessions(userId),
            sampleRepository.observeAllSamples(userId)
        ) { sessions, verifiedSamples ->
            val totalSessions = sessions.size
            // Since observeAllSamples only gives verified samples based on its doc,
            // wait, observeAllSamples docs say: "Observes all verified samples for the given user"
            val totalSamples = verifiedSamples.size // Rough approximation for now
            val verifiedRatio = if (totalSamples > 0) "100%" else "0%" // Mock calculation

            KpiState(
                sessionsCount = totalSessions.toString(),
                samplesCount = totalSamples.toString(),
                verifiedRatio = verifiedRatio,
                epgAvgStatus = if (totalSamples > 100) "Heavy" else "Light"
            )
        }
    }

    // Pending Reviews + Sync Status
    private val pendingAndSyncFlow = userIdFlow.filterNotNull().flatMapLatest { userId ->
        combine(
            flow { emit(sampleRepository.getSamplesPendingSync(userId)) },
            sampleRepository.observeAllSamples(userId)
        ) { pendingSamples, allSamples ->
            val pendingCount = pendingSamples.size

            // Oldest pending: find the earliest timestamp among unverified flagged samples
            val oldestPendingMs = pendingSamples.minOfOrNull { it.timestamp }
            val oldestPendingAgo = if (oldestPendingMs != null) {
                val diffMs = System.currentTimeMillis() - oldestPendingMs
                when {
                    diffMs < 60_000L             -> "${diffMs / 1_000}s ago"
                    diffMs < 3_600_000L          -> "${diffMs / 60_000}m ago"
                    diffMs < 86_400_000L         -> "${diffMs / 3_600_000}h ago"
                    else                         -> "${diffMs / 86_400_000}d ago"
                }
            } else ""

            // Sync status: all synced when no VERIFIED (unsynced) samples exist
            val unsyncedCount = allSamples.count {
                it.status == com.agarthavision.domain.model.SampleStatus.VERIFIED
            }
            val syncedSamples = allSamples.count {
                it.status == com.agarthavision.domain.model.SampleStatus.SYNCED
            }
            val allSynced = unsyncedCount == 0

            // Last sync label: time since the most recently synced sample
            val lastSyncedMs = allSamples
                .filter { it.status == com.agarthavision.domain.model.SampleStatus.SYNCED }
                .maxOfOrNull { it.verifiedAt }
            val lastSyncLabel = if (lastSyncedMs != null) {
                val diffMs = System.currentTimeMillis() - lastSyncedMs
                when {
                    diffMs < 60_000L    -> "just now"
                    diffMs < 3_600_000L -> "${diffMs / 60_000}m ago"
                    diffMs < 86_400_000L -> "${diffMs / 3_600_000}h ago"
                    else                -> "${diffMs / 86_400_000}d ago"
                }
            } else "never"

            PendingAndSync(
                pendingCount    = pendingCount,
                oldestPendingAgo = oldestPendingAgo,
                allSynced       = allSynced,
                lastSyncLabel   = lastSyncLabel,
                syncedSamplesCount = syncedSamples
            )
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
                        val duration = Duration.between(state.startedAt, now)
                        val minutes = duration.toMinutes()

                        // Calculate stats
                        val totalFrames = samples.size
                        val verifiedFrames = samples.count { it.verifiedAt > 0 }
                        val pendingFrames = totalFrames - verifiedFrames
                        val totalEpg = 0 // Mocked for now, requires deeper join

                        ActiveSessionState(
                            label = state.session.label ?: "Active Session",
                            startedAtAgo = "Started $minutes min ago",
                            isRecording = state.isInferenceRunning,
                            totalFrames = totalFrames.toString(),
                            verifiedFrames = verifiedFrames.toString(),
                            totalEpg = totalEpg.toString(), // Mocked
                            pendingFrames = pendingFrames.toString()
                        )
                    }
            }
        }
    }

    // Historical charts
    private val historicalDataFlow = userIdFlow.filterNotNull().flatMapLatest { userId ->
        val sevenDaysAgo = Instant.now().minus(Duration.ofDays(7)).toEpochMilli()
        combine(
            detectionRepository.observeConfirmedEggCountsSince(userId, sevenDaysAgo),
            detectionRepository.observeDailyEggCountsSince(userId, sevenDaysAgo)
        ) { eggCounts, dailyCounts ->
            // Process Species
            val totalEggs = eggCounts.sumOf { it.count }.coerceAtLeast(1)
            val topSpecies = eggCounts.take(3).map {
                val ratio = it.count.toFloat() / totalEggs
                SpeciesData(
                    name = it.species,
                    ratio = ratio,
                    formattedPercentage = "${(ratio * 100).toInt()}%"
                )
            }

            // Process Sparkline (7 days)
            val sparkline = MutableList(7) { 0f }
            val startOfDay = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayMs = 24 * 60 * 60 * 1000L

            for (daily in dailyCounts) {
                // Determine which of the last 7 days this timestamp belongs to (0 = oldest, 6 = today)
                val diffMs = startOfDay - daily.timestamp
                val daysAgo = if (diffMs < 0) 0 else (diffMs / dayMs).toInt()
                val index = 6 - daysAgo
                if (index in 0..6) {
                    sparkline[index] += daily.count.toFloat()
                }
            }

            // Normalize sparkline data (max = 1f, min = 0f) for the UI Canvas
            val maxCount = sparkline.maxOrNull() ?: 1f
            val normalizedSparkline = sparkline.map { if (maxCount > 0) it / maxCount else 0f }

            Pair(topSpecies, normalizedSparkline)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        kpiStateFlow,
        pendingAndSyncFlow,
        activeSessionStateFlow,
        historicalDataFlow
    ) { kpis, pendingSync, activeSession, (topSpecies, sparkline) ->
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
            epgSparklineData    = sparkline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )
}
