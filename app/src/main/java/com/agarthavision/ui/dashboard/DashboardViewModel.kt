package com.agarthavision.ui.dashboard

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.domain.model.AgreementBreakdown
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.model.windows
import com.agarthavision.domain.usecase.home.NeedsAttention
import com.agarthavision.domain.usecase.home.ObserveHomeKpisUseCase
import com.agarthavision.domain.usecase.home.ObserveNeedsAttentionUseCase
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val activeSession: ActiveSessionState? = null,
    val kpis: KpiState = KpiState(),
    val kpiTiles: List<KpiTileUi> = emptyList(),
    val aiBreakdown: AgreementBreakdown = AgreementBreakdown(0, 0, 0, 0),
    val topSpecies: List<SpeciesData> = emptyList(),
    val pendingReviewCount: Int = 0,
    val oldestPendingAt: Long? = null,
    val allSynced: Boolean = true,
    val lastSyncedAt: Long? = null,
    val syncedSamplesCount: Int = 0,
    val isDarkMode: Boolean = false,
    val isSignedIn: Boolean = false,
    val isOffline: Boolean = false,
    val pendingUploadCount: Int = 0,
    val isSyncing: Boolean = false,
    val period: HomePeriod = HomePeriod.TODAY,
    val needsAttention: NeedsAttention = NeedsAttention(0, 0, 0),
) {
    /** Sync-now is available only to a signed-in medtech with an online connection. */
    val canSyncNow: Boolean
        get() = isSignedIn && !isOffline && !isSyncing
}

data class ActiveSessionState(
    val sessionId: String,
    val label: String,
    val lastActivityAt: Long,
    val totalFrames: Int,
    val pendingFrames: Int,
)

data class KpiState(
    val patientsCount: String = "0",
    val sessionsCount: String = "0",
    val samplesCount: String = "0",
    val pendingCount: String = "0",
)

data class SpeciesData(
    val name: String,
    val ratio: Float,
    val formattedPercentage: String,
)

data class PendingAndSync(
    val pendingCount: Int,
    val oldestPendingAt: Long?,
    val allSynced: Boolean,
    val lastSyncedAt: Long?,
    val syncedSamplesCount: Int,
    val initialFetchDone: Boolean = true,
)

private data class ContentBundle(
    val kpis: KpiState,
    val tiles: List<KpiTileUi>,
    val aiBreakdown: AgreementBreakdown,
    val topSpecies: List<SpeciesData>,
    val selectedPeriod: HomePeriod,
)

private data class SessionAndSyncBundle(
    val pendingSync: PendingAndSync,
    val activeSession: ActiveSessionState?,
    val accountSync: Triple<Boolean, Boolean, Boolean>,
    val needsAttention: NeedsAttention,
)

@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock,
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase,
    private val initialFetchStateStore: InitialFetchStateStore,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val sampleRepository: SampleRepository,
    private val patientRepository: PatientRepository,
    private val detectionRepository: DetectionRepository,
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    observeNeedsAttentionUseCase: ObserveNeedsAttentionUseCase,
    private val observeHomeKpisUseCase: ObserveHomeKpisUseCase,
) : ViewModel() {

    private val themeModeFlow = observeThemeModeUseCase()

    private val period = savedStateHandle.getStateFlow(KEY_PERIOD, HomePeriod.TODAY)

    fun onPeriodSelected(p: HomePeriod) {
        savedStateHandle[KEY_PERIOD] = p
    }

    private val localIdentityFlow = observeLocalIdentityUseCase()
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val userIdFlow = localIdentityFlow
        .map { it?.userId }
        .distinctUntilChanged()

    private val kpiStateFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(KpiState())
        } else {
            combine(
                patientRepository.observePatientCount(userId, ""),
                sessionRepository.observeAllSessions(userId),
                sampleRepository.observeAllSamples(userId),
                sampleRepository.observePendingCount(userId),
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

    private val pendingAndSyncFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(
                PendingAndSync(
                    pendingCount = 0,
                    oldestPendingAt = null,
                    allSynced = true,
                    lastSyncedAt = null,
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
                val oldestPendingMs = pendingSamples.minOfOrNull { it.timestamp }

                val unsyncedCount = allSamples.count {
                    it.status == com.agarthavision.domain.model.SampleStatus.VERIFIED
                }
                val syncedSamples = allSamples.count {
                    it.status == com.agarthavision.domain.model.SampleStatus.SYNCED
                }
                val allSynced = initialFetchDone && unsyncedCount == 0

                val lastSyncedMs = allSamples
                    .filter { it.status == com.agarthavision.domain.model.SampleStatus.SYNCED }
                    .maxOfOrNull { it.verifiedAt }

                PendingAndSync(
                    pendingCount = pendingCount,
                    oldestPendingAt = oldestPendingMs,
                    allSynced = allSynced,
                    lastSyncedAt = lastSyncedMs,
                    syncedSamplesCount = syncedSamples,
                    initialFetchDone = initialFetchDone,
                )
            }
        }
    }

    private val activeSessionStateFlow = sessionManager.state.flatMapLatest { state ->
        when (state) {
            is SessionState.Idle -> flowOf(null)
            is SessionState.Active -> {
                val userId = state.session.userId ?: ""
                sampleRepository.observeSamplesForSession(state.session.sessionId, userId)
                    .map { samples ->
                        val lastActivityMs = samples.maxOfOrNull { maxOf(it.timestamp, it.verifiedAt) }
                            ?: state.startedAt.toEpochMilli()

                        val totalFrames = samples.size
                        val verifiedFrames = samples.count { it.verifiedAt > 0 }
                        val pendingFrames = totalFrames - verifiedFrames

                        ActiveSessionState(
                            sessionId = state.session.sessionId,
                            label = state.session.label ?: "Active Session",
                            lastActivityAt = lastActivityMs,
                            totalFrames = totalFrames,
                            pendingFrames = pendingFrames,
                        )
                    }
            }
        }
    }

    private val topSpeciesFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(emptyList())
        } else {
            val sevenDaysAgo = clock.instant()
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

    private val isSyncingFlow = MutableStateFlow(false)

    private val needsAttentionFlow = combine(
        userIdFlow,
        sessionManager.state,
    ) { userId, sessionState ->
        userId to (sessionState as? SessionState.Active)?.session?.sessionId
    }.flatMapLatest { (userId, activeSessionId) ->
        if (userId == null) {
            flowOf(NeedsAttention(0, 0, 0))
        } else {
            observeNeedsAttentionUseCase(userId, activeSessionId)
        }
    }

    private val accountSyncFlow = combine(
        localIdentityFlow,
        connectivityObserver.isOnline,
        isSyncingFlow,
    ) { identity, online, syncing ->
        Triple(identity != null, !online, syncing)
    }

    private val homeKpisFlow = combine(
        userIdFlow,
        period,
    ) { userId, p ->
        userId to p
    }.flatMapLatest { (userId, p) ->
        if (userId == null) {
            flowOf(null)
        } else {
            val windows = p.windows(clock.instant(), CLINICAL_ZONE)
            observeHomeKpisUseCase(userId, windows)
        }
    }

    private val kpiTilesFlow = combine(
        homeKpisFlow,
        period,
    ) { kpis, p ->
        if (kpis == null) {
            emptyList()
        } else {
            listOf(
                KpiTileUi(
                    kind = KpiKind.SESSIONS,
                    label = "Sessions",
                    value = kpis.sessions.current.toString(),
                    subtitle = if (kpis.patientsInSessions == 1) {
                        "1 patient"
                    } else {
                        "${kpis.patientsInSessions} patients"
                    },
                    spokenDescription = "Sessions, ${kpis.sessions.current}, " +
                        "${kpis.patientsInSessions} patients. Opens sessions.",
                ),
                KpiTileUi(
                    kind = KpiKind.POSITIVE_RATE,
                    label = "Positive rate",
                    value = if (kpis.positiveRate.current.denominator == 0) {
                        "—"
                    } else {
                        "${(kpis.positiveRate.current.value!! * 100).toInt()}%"
                    },
                    subtitle = if (kpis.positiveRate.current.denominator == 0) {
                        "No smears examined yet"
                    } else {
                        "${kpis.positiveRate.current.numerator} of ${kpis.positiveRate.current.denominator} smears"
                    },
                    spokenDescription = if (kpis.positiveRate.current.denominator == 0) {
                        "Positive rate, no smears examined yet. Opens examined smears."
                    } else {
                        val pct = (kpis.positiveRate.current.value!! * 100).toInt()
                        val num = kpis.positiveRate.current.numerator
                        val den = kpis.positiveRate.current.denominator
                        "Positive rate, $pct percent, $num of $den smears. Opens examined smears."
                    },
                ),
                KpiTileUi(
                    kind = KpiKind.TO_REVIEW,
                    label = "To review",
                    value = kpis.toReview.current.toString(),
                    subtitle = if (p == HomePeriod.TODAY) {
                        "${kpis.verifiedInPeriod} verified today"
                    } else {
                        "${kpis.verifiedInPeriod} verified in period"
                    },
                    spokenDescription = "To review, ${kpis.toReview.current} frames. Opens frames to review.",
                ),
                KpiTileUi(
                    kind = KpiKind.AI_AGREEMENT,
                    label = "AI agreement",
                    value = if (kpis.aiAgreement.current.denominator == 0) {
                        "—"
                    } else {
                        "${(kpis.aiAgreement.current.value!! * 100).toInt()}%"
                    },
                    subtitle = if (kpis.aiAgreement.current.denominator == 0) {
                        "No AI results reviewed yet"
                    } else {
                        "${kpis.aiBreakdown.corrected} of ${kpis.aiAgreement.current.denominator} corrected by you"
                    },
                    spokenDescription = if (kpis.aiAgreement.current.denominator == 0) {
                        "AI agreement, no AI results reviewed yet. Opens AI agreement details."
                    } else {
                        val pct = (kpis.aiAgreement.current.value!! * 100).toInt()
                        val corrected = kpis.aiBreakdown.corrected
                        val den = kpis.aiAgreement.current.denominator
                        "AI agreement, $pct percent, $corrected of $den corrected by you. " +
                            "Opens AI agreement details."
                    },
                ),
            )
        }
    }

    private val contentFlow = combine(
        kpiStateFlow,
        kpiTilesFlow,
        homeKpisFlow,
        topSpeciesFlow,
        period,
    ) { kpis, tiles, homeKpis, topSpecies, selectedPeriod ->
        ContentBundle(
            kpis = kpis,
            tiles = tiles,
            aiBreakdown = homeKpis?.aiBreakdown ?: AgreementBreakdown(0, 0, 0, 0),
            topSpecies = topSpecies,
            selectedPeriod = selectedPeriod,
        )
    }

    private val sessionAndSyncFlow = combine(
        pendingAndSyncFlow,
        activeSessionStateFlow,
        accountSyncFlow,
        needsAttentionFlow,
    ) { pendingSync, activeSession, accountSync, needsAttention ->
        SessionAndSyncBundle(pendingSync, activeSession, accountSync, needsAttention)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        contentFlow,
        sessionAndSyncFlow,
        themeModeFlow,
    ) { content, sessionAndSync, themeMode ->
        val pendingSync = sessionAndSync.pendingSync
        val activeSession = sessionAndSync.activeSession
        val accountSync = sessionAndSync.accountSync
        val needsAttention = sessionAndSync.needsAttention
        val (isSignedIn, isOffline, isSyncing) = accountSync
        DashboardUiState(
            isLoading = false,
            kpis = content.kpis,
            kpiTiles = content.tiles,
            aiBreakdown = content.aiBreakdown,
            pendingReviewCount = pendingSync.pendingCount,
            oldestPendingAt = pendingSync.oldestPendingAt,
            allSynced = pendingSync.allSynced,
            lastSyncedAt = pendingSync.lastSyncedAt,
            syncedSamplesCount = pendingSync.syncedSamplesCount,
            activeSession = activeSession,
            topSpecies = content.topSpecies,
            isDarkMode = themeMode == ThemeMode.DARK,
            isSignedIn = isSignedIn,
            isOffline = isOffline,
            pendingUploadCount = pendingSync.pendingCount,
            isSyncing = isSyncing,
            period = content.selectedPeriod,
            needsAttention = needsAttention,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun onToggleTheme() {
        val target = if (uiState.value.isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            setThemeModeUseCase(target).onFailure { error ->
                Log.e(TAG, "Failed to persist theme mode $target", error)
            }
        }
    }

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
        const val HISTORICAL_DAYS = 7
        const val TOP_SPECIES_COUNT = 3
        const val KEY_PERIOD = "dashboard_period"
    }
}
