package com.agarthavision.ui.dashboard.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.windows
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.coverage.resolveCoverageFitBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    ) : MyCoverageCardUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyCoverageCardViewModel @Inject constructor(
    private val clock: Clock,
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val observeMyCoverageUseCase: ObserveMyCoverageUseCase,
    private val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase,
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
                        onSuccess = { coverage -> toReadyOrEmptyFlow(coverage) },
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

    private suspend fun toReadyOrEmptyFlow(
        coverage: MyCoverage,
    ) = flowOf(
        run {
            if (coverage.totals.smears == 0) {
                MyCoverageCardUiState.Empty(coverage.period) as MyCoverageCardUiState
            } else {
                loadProvinceBoundariesUseCase().fold(
                    onSuccess = { provinces ->
                        MyCoverageCardUiState.Ready(
                            coverage = coverage,
                            provinces = provinces,
                            fitBounds = resolveCoverageFitBounds(coverage, provinces),
                        )
                    },
                    onFailure = { error ->
                        MyCoverageCardUiState.Error(error.message ?: "Couldn't load the map")
                    },
                )
            }
        },
    )
}
