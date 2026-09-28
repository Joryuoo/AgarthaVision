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
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadAreaDirectoryUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.LoadTownBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.coverage.resolveCoverageFitBounds
import com.agarthavision.domain.usecase.coverage.resolveDataFitBounds
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
     * [TownGeometry.Unavailable]. Sourced from [MyCoverage.townCounts] (already loaded for the
     * whole period), filtered to this province via the area directory — reusing data already in
     * memory rather than issuing a new DAO query.
     */
    val townCounts: Map<String, AreaCount> = emptyMap(),
)

data class MyCoverageUiState(
    val period: HomePeriod,
    val isLoading: Boolean = true,
    val error: String? = null,
    val provinces: BoundarySet? = null,
    val coverage: MyCoverage? = null,
    /**
     * The union of every province with data (or the whole country if none has data) — see
     * [resolveDataFitBounds]. Unlike `MyCoverageCardViewModel`'s static card fit (which also
     * frames a single province or island group via [resolveCoverageFitBounds]), the full-screen
     * map always starts data-fitted so "All" can return to it after other filters.
     */
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
) : ViewModel() {

    private val initialPeriod: HomePeriod = savedStateHandle.get<String>("period")?.let { raw ->
        runCatching { HomePeriod.valueOf(raw) }.getOrNull()
    } ?: HomePeriod.TODAY

    private val periodFlow = MutableStateFlow(initialPeriod)

    private val userIdFlow = observeLocalIdentityUseCase()
        .map { it?.userId }
        .distinctUntilChanged()

    private val initialProvinces = loadProvinceBoundariesUseCase.cachedOrNull()
    private val provincesState = MutableStateFlow<BoundarySet?>(initialProvinces)
    private val coverageResultState = MutableStateFlow<Result<MyCoverage>?>(null)

    private val _uiState = MutableStateFlow(
        MyCoverageUiState(
            period = initialPeriod,
            provinces = initialProvinces,
        ),
    )
    val uiState: StateFlow<MyCoverageUiState> = _uiState.asStateFlow()

    private var directory: AreaDirectory? = loadAreaDirectoryUseCase.cachedOrNull()
        set(value) {
            field = value
            townCodesByProvince = null
        }
    private var currentUserId: String? = null

    private val coverageCache = mutableMapOf<HomePeriod, MyCoverage>()

    /** Completed, successful species lookups only — never a failure or a partial/loading state. */
    private val speciesCache = mutableMapOf<String, List<SpeciesFinding>>()

    /** Lazily built from [directory], reset whenever [directory] is (re)assigned. */
    private var townCodesByProvince: Map<String, Set<String>>? = null

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
                            coverageCache[period] = coverage
                            if (coverage != current.coverage) speciesCache.clear()
                            current.withCoverage(coverage, provinces, period)
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

    /**
     * Applies a newly (re)loaded [coverage] to this state, recomputing [MyCoverageUiState.initialFit]
     * via [resolveDataFitBounds]. If the fit changed after the very first load and the current
     * filter is "All", also nudges [MyCoverageUiState.cameraTarget] back to the new fit so the map
     * doesn't stay parked on a stale one (bug: "All" resetting to the whole country instead of the
     * data-fitted view). Skipped on the first fit (`initialFit == null`) to avoid racing the
     * screen's initial camera snap.
     */
    private fun MyCoverageUiState.withCoverage(
        coverage: MyCoverage,
        provinces: BoundarySet?,
        period: HomePeriod,
    ): MyCoverageUiState {
        val newFit = provinces?.let { resolveDataFitBounds(coverage, it) }
        val updated = copy(
            period = period,
            isLoading = false,
            error = null,
            provinces = provinces,
            coverage = coverage,
            initialFit = newFit ?: initialFit,
        )
        return if (initialFit != null && newFit != null && newFit != initialFit) {
            updated.copy(cameraTarget = computeCameraTarget(updated.islandFilter, updated))
        } else {
            updated
        }
    }

    fun onPeriodChange(period: HomePeriod) {
        // Local to this screen only — does not write back to Home's period (D13).
        periodFlow.value = period
        speciesCache.clear()
        coverageCache[period]?.let { cachedCoverage ->
            _uiState.update { current -> current.withCoverage(cachedCoverage, current.provinces, period) }
        }
    }

    fun onIslandFilter(group: IslandGroup?) {
        val state = _uiState.value
        val target = computeCameraTarget(group, state)
        _uiState.update { it.copy(islandFilter = group, cameraTarget = target) }
    }

    @Suppress("ReturnCount") // Two simple guard/fallback returns read more clearly than nesting.
    private fun computeCameraTarget(group: IslandGroup?, state: MyCoverageUiState): GeoBounds? {
        val provinces = state.provinces ?: return null
        if (group == null) return state.initialFit ?: provinces.bounds
        val codes = state.coverage?.provinces?.filter { it.islandGroup == group }?.map { it.code } ?: emptyList()
        val bounds = codes.mapNotNull { provinces.byCode[it]?.bounds }
        return bounds.reduceOrNull(GeoBounds::union) ?: provinces.bounds
    }

    fun onCameraTargetConsumed() {
        _uiState.update { it.copy(cameraTarget = null) }
    }

    fun onMapTap(mapX: Float, mapY: Float) {
        val state = _uiState.value
        val provinces = state.provinces ?: return
        val hitArea = hitTest(provinces, mapX, mapY) ?: return

        val coverage = state.coverage
        val provinceCoverage = coverage?.provinces?.firstOrNull { it.code == hitArea.code }
        val townCodeIndex = townCodesByProvince ?: buildTownCodeIndex(directory).also { townCodesByProvince = it }
        val townCodes = townCodeIndex[hitArea.code].orEmpty()
        val townCounts = coverage?.townCounts?.filterKeys { it in townCodes }.orEmpty()
        val cachedSpecies = speciesCache[hitArea.code]

        val cachedTowns = loadTownBoundariesUseCase.cachedOrNull(hitArea.code)
        val initialGeometry = if (cachedTowns != null) {
            TownGeometry.Available(cachedTowns)
        } else {
            TownGeometry.Loading
        }

        _uiState.update {
            it.copy(
                selected = SelectedProvince(
                    code = hitArea.code,
                    name = hitArea.name,
                    coverage = provinceCoverage,
                    species = cachedSpecies.orEmpty(),
                    towns = initialGeometry,
                    townCounts = townCounts,
                ),
            )
        }

        val needsSpecies = cachedSpecies == null && provinceCoverage?.count?.stat is AreaStat.Reported
        val needsGeometry = cachedTowns == null
        if (needsSpecies || needsGeometry) {
            loadSelectedProvinceDetails(hitArea.code, needsSpecies, needsGeometry, townCodes)
        }
    }

    private fun loadSelectedProvinceDetails(
        provinceCode: String,
        needsSpecies: Boolean,
        needsGeometry: Boolean,
        townCodes: Set<String>,
    ) {
        val userId = currentUserId
        val period = _uiState.value.period
        val windows = period.windows(clock.instant(), CLINICAL_ZONE)

        if (needsSpecies && userId != null) {
            viewModelScope.launch {
                runCatching { observeFindingsUseCase(userId, windows.current, townCodes).first().species }
                    .onSuccess { species ->
                        speciesCache[provinceCode] = species
                        updateSelected(provinceCode) { it.copy(species = species) }
                    }
            }
        }

        if (needsGeometry) {
            viewModelScope.launch {
                val townGeometry = loadTownBoundariesUseCase(provinceCode).fold(
                    onSuccess = { set -> if (set != null) TownGeometry.Available(set) else TownGeometry.Unavailable },
                    onFailure = { TownGeometry.Unavailable },
                )
                updateSelected(provinceCode) { it.copy(towns = townGeometry) }
            }
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

/** Every town code grouped by its parent province code, from [directory]'s flat town list. */
private fun buildTownCodeIndex(directory: AreaDirectory?): Map<String, Set<String>> =
    directory?.towns?.values
        ?.groupBy({ it.provinceKey }, { it.code })
        ?.mapValues { it.value.toSet() }
        .orEmpty()
