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
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyCoverageCardUiState.Loading,
    )

    private fun toReadyOrEmptyFlow(
        userId: String,
        windows: PeriodWindows,
        coverage: MyCoverage,
    ): Flow<MyCoverageCardUiState> = flow {
        if (coverage.totals.smears == 0) {
            emit(MyCoverageCardUiState.Empty(coverage.period))
            return@flow
        }
        val provincesResult = loadProvinceBoundariesUseCase()
        val provinces = provincesResult.getOrNull()
        if (provinces == null) {
            val err = provincesResult.exceptionOrNull()?.message ?: "Couldn't load the map"
            emit(MyCoverageCardUiState.Error(err))
            return@flow
        }
        val fitBounds = resolveCoverageFitBounds(coverage, provinces)
        val framing = coverage.framing
        if (framing is CoverageFraming.SingleProvince) {
            val towns = runCatching { loadTownBoundariesUseCase(framing.code).getOrNull() }.getOrNull()
            emit(
                MyCoverageCardUiState.Ready(
                    coverage = coverage,
                    provinces = provinces,
                    fitBounds = fitBounds,
                    singleProvinceTowns = towns,
                    species = emptyList(),
                ),
            )
            val findingsFlow = runCatching { observeFindingsUseCase(userId, windows.current, null) }.getOrNull()
            if (findingsFlow != null) {
                emitAll(
                    findingsFlow.map { findings ->
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
