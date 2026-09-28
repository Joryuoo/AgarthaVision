package com.agarthavision.ui.dashboard.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.PeriodWindows
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.domain.model.windows
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.LoadTownBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.coverage.resolveCoverageFitBounds
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

sealed interface MyCoverageCardUiState {
    data object Loading : MyCoverageCardUiState
    data class Empty(val period: HomePeriod) : MyCoverageCardUiState
    data class Error(val message: String) : MyCoverageCardUiState
    data class Ready(
        val coverage: MyCoverage,
        val provinces: BoundarySet,
        val fitBounds: GeoBounds,
        val singleProvinceTowns: BoundarySet? = null,
        val species: List<SpeciesFinding> = emptyList(),
    ) : MyCoverageCardUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyCoverageCardViewModel @Inject constructor(
    private val clock: Clock,
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val observeMyCoverageUseCase: ObserveMyCoverageUseCase,
    private val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase,
    private val loadTownBoundariesUseCase: LoadTownBoundariesUseCase,
    private val observeFindingsUseCase: ObserveFindingsUseCase,
) : ViewModel() {

    private val periodFlow = MutableStateFlow(HomePeriod.TODAY)

    private val userIdFlow = observeLocalIdentityUseCase()
        .map { it?.userId }
        .distinctUntilChanged()

    private var cachedProvinces: BoundarySet? = loadProvinceBoundariesUseCase.cachedOrNull()
    private val townsCache = mutableMapOf<String, BoundarySet?>()
    private val findingsCache = mutableMapOf<Pair<String, HomePeriod>, List<SpeciesFinding>>()
    private val stateCache = mutableMapOf<Pair<String, HomePeriod>, MyCoverageCardUiState>()

    fun setPeriod(period: HomePeriod) {
        periodFlow.value = period
    }

    val uiState: StateFlow<MyCoverageCardUiState> = userIdFlow.flatMapLatest { userId ->
        periodFlow.flatMapLatest { period ->
            if (userId == null) {
                flowOf(MyCoverageCardUiState.Empty(period) as MyCoverageCardUiState)
            } else {
                val windows = period.windows(clock.instant(), CLINICAL_ZONE)
                observeMyCoverageUseCase(userId, period, windows).flatMapLatest { coverageResult ->
                    coverageResult.fold(
                        onSuccess = { coverage -> toReadyOrEmptyFlow(userId, windows, coverage) },
                        onFailure = { error ->
                            flowOf(
                                MyCoverageCardUiState.Error(
                                    error.message ?: "Couldn't load coverage",
                                ) as MyCoverageCardUiState,
                            )
                        },
                    )
                }
                    .onStart { emit(stateCache[userId to period] ?: MyCoverageCardUiState.Loading) }
                    .onEach {
                        if (it is MyCoverageCardUiState.Ready || it is MyCoverageCardUiState.Empty) {
                            stateCache[userId to period] = it
                        }
                    }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        // stateIn keeps the last emitted value across resubscribe (so the card re-renders
        // instantly on return), and the 5s stop timeout lets period time-windows recompute
        // when the upstream flow restarts after being backgrounded.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyCoverageCardUiState.Loading,
    )

    private suspend fun getProvinces(): BoundarySet? {
        val cached = cachedProvinces ?: loadProvinceBoundariesUseCase.cachedOrNull()
        if (cached != null) {
            cachedProvinces = cached
            return cached
        }
        return loadProvinceBoundariesUseCase().getOrNull()?.also { cachedProvinces = it }
    }

    private suspend fun getTowns(code: String): BoundarySet? {
        if (townsCache.containsKey(code)) return townsCache[code]
        val cached = loadTownBoundariesUseCase.cachedOrNull(code)
        val result = cached ?: runCatching { loadTownBoundariesUseCase(code).getOrNull() }.getOrNull()
        townsCache[code] = result
        return result
    }

    private fun toReadyOrEmptyFlow(
        userId: String,
        windows: PeriodWindows,
        coverage: MyCoverage,
    ): Flow<MyCoverageCardUiState> = flow {
        if (coverage.totals.smears == 0) {
            emit(MyCoverageCardUiState.Empty(coverage.period))
            return@flow
        }
        val provinces = getProvinces()
        if (provinces == null) {
            emit(MyCoverageCardUiState.Error("Couldn't load the map"))
            return@flow
        }
        val fitBounds = resolveCoverageFitBounds(coverage, provinces)
        val framing = coverage.framing
        if (framing is CoverageFraming.SingleProvince) {
            val towns = getTowns(framing.code)
            val cachedSpecies = findingsCache[userId to coverage.period] ?: emptyList()
            emit(
                MyCoverageCardUiState.Ready(
                    coverage = coverage,
                    provinces = provinces,
                    fitBounds = fitBounds,
                    singleProvinceTowns = towns,
                    species = cachedSpecies,
                ),
            )
            val findingsFlow = runCatching { observeFindingsUseCase(userId, windows.current, null) }.getOrNull()
            if (findingsFlow != null) {
                emitAll(
                    findingsFlow.map { findings ->
                        findingsCache[userId to coverage.period] = findings.species
                        MyCoverageCardUiState.Ready(
                            coverage = coverage,
                            provinces = provinces,
                            fitBounds = fitBounds,
                            singleProvinceTowns = towns,
                            species = findings.species,
                        )
                    },
                )
            }
        } else {
            emit(
                MyCoverageCardUiState.Ready(
                    coverage = coverage,
                    provinces = provinces,
                    fitBounds = fitBounds,
                ),
            )
        }
    }
}
