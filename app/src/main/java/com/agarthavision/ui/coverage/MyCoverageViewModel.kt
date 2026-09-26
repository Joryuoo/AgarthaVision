package com.agarthavision.ui.coverage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.geo.hitTest
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.domain.model.windows
import com.agarthavision.domain.repository.CoverageRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadAreaDirectoryUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.LoadTownBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.coverage.resolveCoverageFitBounds
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** One province's town outlines, independent of whether they finished loading. */
sealed interface TownGeometry {
    data object Loading : TownGeometry
    data class Available(val set: BoundarySet) : TownGeometry
    data object Unavailable : TownGeometry
}

data class SelectedProvince(
    val code: String,
    val name: String,
    /** null = this province has no data in the current period. */
    val coverage: ProvinceCoverage?,
    /**
     * From [ObserveFindingsUseCase], filtered to this province's town codes; empty until loaded
     * or if [coverage] isn't Reported.
     */
    val species: List<SpeciesFinding> = emptyList(),
    val towns: TownGeometry = TownGeometry.Loading,
    /**
     * Per-town smear/positive counts within this province, keyed by town code — not part of the
     * original sketch's [TownGeometry] (which is geometry-only), but the sheet's "towns ranked
     * by positive rate" list needs counts even for a province whose town *geometry* is
     * [TownGeometry.Unavailable]. Sourced from [CoverageRepository.observeTownCoverage], filtered
     * to this province via the area directory — reusing data Phase 9 already loads rather than
     * adding a new DAO query.
     */
    val townCounts: Map<String, AreaCount> = emptyMap(),
)

data class MyCoverageUiState(
    val period: HomePeriod,
    val isLoading: Boolean = true,
    val error: String? = null,
    val provinces: BoundarySet? = null,
    val coverage: MyCoverage? = null,
    /** Same rule as `MyCoverageCardViewModel.resolveFitBounds` — see [resolveCoverageFitBounds]. */
    val initialFit: GeoBounds? = null,
    /** null = "All". */
    val islandFilter: IslandGroup? = null,
    /** The screen requests the map animate to this; consumed/cleared once applied via [onCameraTargetConsumed]. */
    val cameraTarget: GeoBounds? = null,
    val selected: SelectedProvince? = null,
    val showAllTowns: Boolean = false,
)

@Suppress("LongParameterList") // Every parameter is a distinct, independently-injected dependency.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyCoverageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clock: Clock,
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val observeMyCoverageUseCase: ObserveMyCoverageUseCase,
    private val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase,
    private val loadTownBoundariesUseCase: LoadTownBoundariesUseCase,
    private val loadAreaDirectoryUseCase: LoadAreaDirectoryUseCase,
    private val observeFindingsUseCase: ObserveFindingsUseCase,
    private val coverageRepository: CoverageRepository,
) : ViewModel() {

    private val initialPeriod: HomePeriod = savedStateHandle.get<String>("period")?.let { raw ->
        runCatching { HomePeriod.valueOf(raw) }.getOrNull()
    } ?: HomePeriod.TODAY

    private val periodFlow = MutableStateFlow(initialPeriod)

    private val userIdFlow = observeLocalIdentityUseCase()
        .map { it?.userId }
        .distinctUntilChanged()

    private val provincesState = MutableStateFlow<BoundarySet?>(null)
    private val coverageResultState = MutableStateFlow<Result<MyCoverage>?>(null)

    private val _uiState = MutableStateFlow(MyCoverageUiState(period = initialPeriod))
    val uiState: StateFlow<MyCoverageUiState> = _uiState.asStateFlow()

    private var directory: AreaDirectory? = null
    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            userIdFlow.collect { currentUserId = it }
        }
        viewModelScope.launch {
            loadProvinceBoundariesUseCase().fold(
                onSuccess = { provincesState.value = it },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Couldn't load the map") }
                },
            )
        }
        viewModelScope.launch {
            loadAreaDirectoryUseCase().onSuccess { directory = it }
        }
        viewModelScope.launch {
            userIdFlow.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    periodFlow.flatMapLatest { period ->
                        val windows = period.windows(clock.instant(), CLINICAL_ZONE)
                        observeMyCoverageUseCase(userId, period, windows)
                    }
                }
            }.collect { result -> coverageResultState.value = result }
        }
        viewModelScope.launch {
            combine(provincesState, coverageResultState, periodFlow) { provinces, coverageResult, period ->
                Triple(provinces, coverageResult, period)
            }.collect { (provinces, coverageResult, period) ->
                _uiState.update { current ->
                    when {
                        coverageResult == null -> current.copy(period = period, provinces = provinces)
                        coverageResult.isSuccess -> {
                            val coverage = coverageResult.getOrThrow()
                            current.copy(
                                period = period,
                                isLoading = false,
                                error = null,
                                provinces = provinces,
                                coverage = coverage,
                                initialFit = provinces?.let { resolveCoverageFitBounds(coverage, it) },
                            )
                        }
                        else -> current.copy(
                            period = period,
                            isLoading = false,
                            provinces = provinces,
                            error = coverageResult.exceptionOrNull()?.message ?: "Couldn't load coverage",
                        )
                    }
                }
            }
        }
    }

    fun onPeriodChange(period: HomePeriod) {
        // Local to this screen only — does not write back to Home's period (D13).
        periodFlow.value = period
    }

    fun onIslandFilter(group: IslandGroup?) {
        val state = _uiState.value
        val target = computeCameraTarget(group, state.provinces, state.coverage)
        _uiState.update { it.copy(islandFilter = group, cameraTarget = target) }
    }

    @Suppress("ReturnCount") // Three simple guard/fallback returns read more clearly than nesting.
    private fun computeCameraTarget(
        group: IslandGroup?,
        provinces: BoundarySet?,
        coverage: MyCoverage?,
    ): GeoBounds? {
        if (provinces == null) return null
        if (group == null) return provinces.bounds
        val codes = coverage?.provinces?.filter { it.islandGroup == group }?.map { it.code } ?: emptyList()
        val bounds = codes.mapNotNull { provinces.byCode[it]?.bounds }
        return bounds.reduceOrNull(GeoBounds::union) ?: provinces.bounds
    }

    fun onCameraTargetConsumed() {
        _uiState.update { it.copy(cameraTarget = null) }
    }

    fun onMapTap(mapX: Float, mapY: Float) {
        val provinces = _uiState.value.provinces ?: return
        val hitArea = hitTest(provinces, mapX, mapY) ?: return

        val coverage = _uiState.value.coverage
        val provinceCoverage = coverage?.provinces?.firstOrNull { it.code == hitArea.code }
        val townCodes = directory?.towns
            ?.filterValues { it.provinceKey == hitArea.code }
            ?.keys
            ?: emptySet()

        _uiState.update {
            it.copy(
                selected = SelectedProvince(
                    code = hitArea.code,
                    name = hitArea.name,
                    coverage = provinceCoverage,
                ),
            )
        }

        loadSelectedProvinceDetails(hitArea.code, provinceCoverage, townCodes)
    }

    private fun loadSelectedProvinceDetails(
        provinceCode: String,
        provinceCoverage: ProvinceCoverage?,
        townCodes: Set<String>,
    ) {
        val userId = currentUserId
        val period = _uiState.value.period
        val windows = period.windows(clock.instant(), CLINICAL_ZONE)

        viewModelScope.launch {
            val species = if (userId != null && provinceCoverage?.count?.stat is AreaStat.Reported) {
                runCatching { observeFindingsUseCase(userId, windows.current, townCodes).first().species }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val townCounts = if (userId != null) {
                runCatching {
                    coverageRepository.observeTownCoverage(userId, windows.current).first()
                        .filter { it.townCode != null && it.townCode in townCodes }
                        .groupBy { it.townCode }
                        .mapNotNull { (townCode, rows) ->
                            townCode?.let {
                                it to AreaCount(
                                    smears = rows.sumOf { row -> row.smearCount },
                                    positives = rows.sumOf { row -> row.positiveCount },
                                )
                            }
                        }
                        .toMap()
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            updateSelected(provinceCode) { it.copy(species = species, townCounts = townCounts) }
        }

        viewModelScope.launch {
            val townGeometry = loadTownBoundariesUseCase(provinceCode).fold(
                onSuccess = { set -> if (set != null) TownGeometry.Available(set) else TownGeometry.Unavailable },
                onFailure = { TownGeometry.Unavailable },
            )
            updateSelected(provinceCode) { it.copy(towns = townGeometry) }
        }
    }

    private fun updateSelected(provinceCode: String, transform: (SelectedProvince) -> SelectedProvince) {
        _uiState.update { state ->
            val selected = state.selected
            if (selected != null && selected.code == provinceCode) {
                state.copy(selected = transform(selected))
            } else {
                state
            }
        }
    }

    fun onDismissSheet() {
        _uiState.update { it.copy(selected = null, showAllTowns = false) }
    }

    fun onShowAllTowns(show: Boolean) {
        _uiState.update { it.copy(showAllTowns = show) }
    }
}
