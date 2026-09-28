package com.agarthavision.ui.coverage

import androidx.lifecycle.SavedStateHandle
import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.geo.ProvinceRef
import com.agarthavision.domain.geo.TownRef
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.FindingsResult
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadAreaDirectoryUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.LoadTownBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MyCoverageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedInstant = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, CLINICAL_ZONE)

    private val cebuShape = AreaShape(
        code = "CEB",
        name = "Cebu",
        parentCode = "",
        bounds = GeoBounds(0f, 0f, 1f, 1f),
        labelX = 0.5f,
        labelY = 0.5f,
        rings = listOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)),
    )
    private val davaoShape = AreaShape(
        code = "DAV",
        name = "Davao del Sur",
        parentCode = "",
        bounds = GeoBounds(5f, 5f, 6f, 6f),
        labelX = 5.5f,
        labelY = 5.5f,
        rings = listOf(floatArrayOf(5f, 5f, 6f, 5f, 6f, 6f, 5f, 6f)),
    )
    private val provinces = BoundarySet(
        areas = listOf(cebuShape, davaoShape),
        bounds = GeoBounds(0f, 0f, 6f, 6f),
    )

    private val directory = AreaDirectory(
        towns = mapOf(
            "TOWN-CEB-1" to TownRef("TOWN-CEB-1", "Cebu City", "CEB", true),
        ),
        provinces = mapOf(
            "CEB" to ProvinceRef("CEB", "Cebu", "0700000000", IslandGroup.VISAYAS),
            "DAV" to ProvinceRef("DAV", "Davao del Sur", "1100000000", IslandGroup.MINDANAO),
        ),
    )

    private fun identityUseCase(userId: String? = "user-1") = mock<ObserveLocalIdentityUseCase>().also {
        whenever(it.invoke()).thenReturn(
            flowOf(userId?.let { id -> LocalIdentity(userId = id, email = "user@example.com") }),
        )
    }

    private fun coverage(
        provincesCoverage: List<ProvinceCoverage> = emptyList(),
        framing: CoverageFraming = CoverageFraming.Empty,
        townCounts: Map<String, AreaCount> = emptyMap(),
    ) = MyCoverage(
        period = HomePeriod.TODAY,
        totals = AreaCount(smears = provincesCoverage.sumOf { it.count.smears }, positives = 0),
        unlocatedSmears = 0,
        provinces = provincesCoverage,
        islandGroupCounts = provincesCoverage.groupingBy { it.islandGroup }.eachCount(),
        framing = framing,
        townCounts = townCounts,
    )

    @Suppress("LongParameterList") // Every parameter is a distinct, independently-overridable fixture default.
    private fun buildViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        identity: ObserveLocalIdentityUseCase = identityUseCase(),
        observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
            whenever(it.invoke(any(), any(), any())).thenReturn(flowOf(Result.success(coverage())))
        },
        loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock<LoadProvinceBoundariesUseCase>().also {
            kotlinx.coroutines.runBlocking { whenever(it.invoke()).thenReturn(Result.success(provinces)) }
        },
        loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock(),
        loadAreaDirectoryUseCase: LoadAreaDirectoryUseCase = mock<LoadAreaDirectoryUseCase>().also {
            kotlinx.coroutines.runBlocking { whenever(it.invoke()).thenReturn(Result.success(directory)) }
        },
        observeFindingsUseCase: ObserveFindingsUseCase = mock<ObserveFindingsUseCase>().also {
            whenever(it.invoke(any(), any(), any())).thenReturn(flowOf(FindingsResult(emptyList(), 0)))
        },
    ) = MyCoverageViewModel(
        savedStateHandle = savedStateHandle,
        clock = clock,
        observeLocalIdentityUseCase = identity,
        observeMyCoverageUseCase = observeMyCoverageUseCase,
        loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
        loadTownBoundariesUseCase = loadTownBoundariesUseCase,
        loadAreaDirectoryUseCase = loadAreaDirectoryUseCase,
        observeFindingsUseCase = observeFindingsUseCase,
    )

    @Test
    fun `valid period from SavedStateHandle is used`() = runTest {
        val vm = buildViewModel(savedStateHandle = SavedStateHandle(mapOf("period" to "LAST_7_DAYS")))
        assertEquals(HomePeriod.LAST_7_DAYS, vm.uiState.value.period)
    }

    @Test
    fun `invalid period string falls back to TODAY`() = runTest {
        val vm = buildViewModel(savedStateHandle = SavedStateHandle(mapOf("period" to "NOT_A_PERIOD")))
        assertEquals(HomePeriod.TODAY, vm.uiState.value.period)
    }

    @Test
    fun `missing period arg falls back to TODAY`() = runTest {
        val vm = buildViewModel(savedStateHandle = SavedStateHandle())
        assertEquals(HomePeriod.TODAY, vm.uiState.value.period)
    }

    @Test
    fun `onIslandFilter with a group that has data targets that group's union bounds`() = runTest {
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val vm = buildViewModel(
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
                )
            },
        )
        advanceUntilIdle()

        vm.onIslandFilter(IslandGroup.VISAYAS)

        assertEquals(cebuShape.bounds, vm.uiState.value.cameraTarget)
        assertEquals(IslandGroup.VISAYAS, vm.uiState.value.islandFilter)
    }

    @Test
    fun `onIslandFilter with a group that has no data falls back to the country bounds`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onIslandFilter(IslandGroup.MINDANAO)

        assertEquals(provinces.bounds, vm.uiState.value.cameraTarget)
    }

    // --- Bug 1: "All" must return to the data-fitted initial fit, not always the whole country ---

    @Test
    fun `initialFit is data-fitted to provinces with data, not the whole country`() = runTest {
        val wideCountryBounds = GeoBounds(0f, 0f, 50f, 50f)
        val wideProvinces = BoundarySet(areas = listOf(cebuShape, davaoShape), bounds = wideCountryBounds)
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase.invoke()).thenReturn(Result.success(wideProvinces))
        }
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val vm = buildViewModel(
            loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
                )
            },
        )
        advanceUntilIdle()

        assertEquals(cebuShape.bounds, vm.uiState.value.initialFit)
        assertNotEquals(wideCountryBounds, vm.uiState.value.initialFit)
    }

    @Test
    fun `initialFit falls back to the whole country when no province has data`() = runTest {
        val wideCountryBounds = GeoBounds(0f, 0f, 50f, 50f)
        val wideProvinces = BoundarySet(areas = listOf(cebuShape, davaoShape), bounds = wideCountryBounds)
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase.invoke()).thenReturn(Result.success(wideProvinces))
        }
        val vm = buildViewModel(loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase)
        advanceUntilIdle()

        assertEquals(wideCountryBounds, vm.uiState.value.initialFit)
    }

    @Test
    fun `onIslandFilter null after a group filter restores the original initialFit, not the whole country`() = runTest {
        val wideCountryBounds = GeoBounds(0f, 0f, 50f, 50f)
        val wideProvinces = BoundarySet(areas = listOf(cebuShape, davaoShape), bounds = wideCountryBounds)
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase.invoke()).thenReturn(Result.success(wideProvinces))
        }
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val vm = buildViewModel(
            loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
                )
            },
        )
        advanceUntilIdle()

        val originalInitialFit = vm.uiState.value.initialFit
        assertEquals(cebuShape.bounds, originalInitialFit)

        vm.onIslandFilter(IslandGroup.VISAYAS)
        assertEquals(cebuShape.bounds, vm.uiState.value.cameraTarget)

        vm.onIslandFilter(null)

        assertNull(vm.uiState.value.islandFilter)
        assertEquals(originalInitialFit, vm.uiState.value.cameraTarget)
        assertNotEquals(wideCountryBounds, vm.uiState.value.cameraTarget)
    }

    @Test
    fun `onMapTap on a real province hit selects it`() = runTest {
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(eq("CEB"))).thenReturn(Result.success(null))
        }
        val vm = buildViewModel(
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
                )
            },
            loadTownBoundariesUseCase = loadTownBoundariesUseCase,
        )
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f)
        advanceUntilIdle()

        val selected = vm.uiState.value.selected
        assertNotNull(selected)
        assertEquals("CEB", selected?.code)
        assertEquals(cebCoverage, selected?.coverage)
        assertTrue(selected?.towns is TownGeometry.Unavailable)
    }

    @Test
    fun `onMapTap on a miss does not change existing selection`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f) // hits Cebu
        advanceUntilIdle()
        val afterFirstTap = vm.uiState.value.selected

        vm.onMapTap(100f, 100f) // misses everything
        advanceUntilIdle()

        assertEquals(afterFirstTap, vm.uiState.value.selected)
    }

    @Test
    fun `onMapTap failure loading town boundaries surfaces as Unavailable`() = runTest {
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(any())).thenReturn(Result.failure(RuntimeException("boom")))
        }
        val vm = buildViewModel(loadTownBoundariesUseCase = loadTownBoundariesUseCase)
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f)
        advanceUntilIdle()

        assertEquals(TownGeometry.Unavailable, vm.uiState.value.selected?.towns)
    }

    @Test
    fun `onMapTap with available town geometry surfaces Available`() = runTest {
        val townShape = AreaShape(
            code = "TOWN-CEB-1",
            name = "Cebu City",
            parentCode = "CEB",
            bounds = cebuShape.bounds,
            labelX = 0.5f,
            labelY = 0.5f,
            rings = cebuShape.rings,
        )
        val townSet = BoundarySet(areas = listOf(townShape), bounds = cebuShape.bounds)
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(eq("CEB"))).thenReturn(Result.success(townSet))
        }
        val vm = buildViewModel(loadTownBoundariesUseCase = loadTownBoundariesUseCase)
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f)
        advanceUntilIdle()

        val towns = vm.uiState.value.selected?.towns
        assertTrue(towns is TownGeometry.Available)
    }

    @Test
    fun `period change re-queries coverage and updates initialFit`() = runTest {
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase.invoke(any(), eq(HomePeriod.TODAY), any())).thenReturn(
            flowOf(Result.success(coverage(emptyList(), CoverageFraming.Empty))),
        )
        whenever(observeMyCoverageUseCase.invoke(any(), eq(HomePeriod.LAST_30_DAYS), any())).thenReturn(
            flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
        )
        val vm = buildViewModel(observeMyCoverageUseCase = observeMyCoverageUseCase)
        advanceUntilIdle()
        assertEquals(provinces.bounds, vm.uiState.value.initialFit)

        vm.onPeriodChange(HomePeriod.LAST_30_DAYS)
        advanceUntilIdle()

        assertEquals(HomePeriod.LAST_30_DAYS, vm.uiState.value.period)
        assertEquals(cebuShape.bounds, vm.uiState.value.initialFit)
    }

    @Test
    fun `period change with All filter nudges cameraTarget to the new data fit, not the whole country`() = runTest {
        // Regression guard for the withCoverage(newFit) refactor: MyCoverageViewModel.withCoverage
        // now takes `newFit` as an explicit parameter instead of computing it internally, and both
        // call sites (the init{} coverage collector and onPeriodChange) must keep computing it the
        // same way. If onPeriodChange's copy of that computation ever drifts from the collector's,
        // this is the "All resetting to the whole country instead of the data-fitted view" bug the
        // withCoverage doc comment describes.
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase.invoke(any(), eq(HomePeriod.TODAY), any())).thenReturn(
            flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
        )
        val davCoverage = ProvinceCoverage("DAV", "Davao del Sur", IslandGroup.MINDANAO, AreaCount(5, 1))
        whenever(observeMyCoverageUseCase.invoke(any(), eq(HomePeriod.LAST_30_DAYS), any())).thenReturn(
            flowOf(Result.success(coverage(listOf(davCoverage), CoverageFraming.SingleProvince("DAV")))),
        )
        val vm = buildViewModel(observeMyCoverageUseCase = observeMyCoverageUseCase)
        advanceUntilIdle()
        assertEquals(cebuShape.bounds, vm.uiState.value.initialFit)
        assertNull(vm.uiState.value.islandFilter)

        vm.onPeriodChange(HomePeriod.LAST_30_DAYS)
        advanceUntilIdle()

        assertEquals(davaoShape.bounds, vm.uiState.value.initialFit)
        assertEquals(davaoShape.bounds, vm.uiState.value.cameraTarget)
        assertNotEquals(provinces.bounds, vm.uiState.value.cameraTarget)
    }

    @Test
    fun `onMapTap only surfaces town counts belonging to the tapped province`() = runTest {
        // Four towns across three provinces mixed into one coverage.townCounts map — tapping
        // Cebu must only show Cebu's towns, not Davao's or Bohol's.
        val mixedDirectory = AreaDirectory(
            towns = mapOf(
                "TOWN-CEB-1" to TownRef("TOWN-CEB-1", "Cebu City", "CEB", true),
                "TOWN-CEB-2" to TownRef("TOWN-CEB-2", "Mandaue", "CEB", true),
                "TOWN-DAV-1" to TownRef("TOWN-DAV-1", "Digos", "DAV", true),
                "TOWN-BOH-1" to TownRef("TOWN-BOH-1", "Tagbilaran", "BOH", true),
            ),
            provinces = mapOf(
                "CEB" to ProvinceRef("CEB", "Cebu", "0700000000", IslandGroup.VISAYAS),
                "DAV" to ProvinceRef("DAV", "Davao del Sur", "1100000000", IslandGroup.MINDANAO),
                "BOH" to ProvinceRef("BOH", "Bohol", "0700000001", IslandGroup.VISAYAS),
            ),
        )
        val loadAreaDirectoryUseCase: LoadAreaDirectoryUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadAreaDirectoryUseCase.invoke()).thenReturn(Result.success(mixedDirectory))
        }
        val mixedTownCounts = mapOf(
            "TOWN-CEB-1" to AreaCount(10, 4),
            "TOWN-CEB-2" to AreaCount(8, 1),
            "TOWN-DAV-1" to AreaCount(20, 9),
            "TOWN-BOH-1" to AreaCount(6, 2),
        )
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(eq("CEB"))).thenReturn(Result.success(null))
        }
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(18, 5))
        val vm = buildViewModel(
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(
                        Result.success(
                            coverage(
                                listOf(cebCoverage),
                                CoverageFraming.SingleProvince("CEB"),
                                mixedTownCounts,
                            ),
                        ),
                    ),
                )
            },
            loadAreaDirectoryUseCase = loadAreaDirectoryUseCase,
            loadTownBoundariesUseCase = loadTownBoundariesUseCase,
        )
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f) // hits Cebu
        advanceUntilIdle()

        val townCounts = vm.uiState.value.selected?.townCounts
        assertEquals(setOf("TOWN-CEB-1", "TOWN-CEB-2"), townCounts?.keys)
        assertEquals(AreaCount(10, 4), townCounts?.get("TOWN-CEB-1"))
        assertEquals(AreaCount(8, 1), townCounts?.get("TOWN-CEB-2"))
    }

    @Test
    fun `onShowAllTowns and onDismissSheet transition state as expected`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.selected)

        vm.onShowAllTowns(true)
        assertTrue(vm.uiState.value.showAllTowns)

        vm.onDismissSheet()
        assertNull(vm.uiState.value.selected)
        assertEquals(false, vm.uiState.value.showAllTowns)
    }

    @Test
    fun `tapping an already loaded province a second time does not refetch cached species`() = runTest {
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(eq("CEB"))).thenReturn(Result.success(null))
        }
        val resolvedSpecies = listOf(SpeciesFinding("Aedes aegypti", 4, 1f, "100%"))
        val observeFindingsUseCase: ObserveFindingsUseCase = mock()
        whenever(observeFindingsUseCase.invoke(any(), any(), any())).thenReturn(
            flowOf(FindingsResult(resolvedSpecies, 4)),
        )
        val vm = buildViewModel(
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage), CoverageFraming.SingleProvince("CEB")))),
                )
            },
            loadTownBoundariesUseCase = loadTownBoundariesUseCase,
            observeFindingsUseCase = observeFindingsUseCase,
        )
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f) // hits Cebu, triggers a species load (stat is Reported)
        advanceUntilIdle()
        val firstSpecies = vm.uiState.value.selected?.species
        assertEquals(resolvedSpecies, firstSpecies)

        vm.onDismissSheet()
        vm.onMapTap(0.5f, 0.5f) // hits Cebu again — species should come straight from the cache
        advanceUntilIdle()

        assertEquals(resolvedSpecies, vm.uiState.value.selected?.species)
        verify(observeFindingsUseCase, times(1)).invoke(any(), any(), any())
    }

    @Test
    fun `species cache is populated even after navigating away before the load finishes`() = runTest {
        val cebCoverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4))
        val davCoverage = ProvinceCoverage("DAV", "Davao del Sur", IslandGroup.MINDANAO, AreaCount(20, 9))
        val deferred = CompletableDeferred<FindingsResult>()
        val observeFindingsUseCase: ObserveFindingsUseCase = mock()
        whenever(observeFindingsUseCase.invoke(any(), any(), any())).thenReturn(
            flow { emit(deferred.await()) },
        )
        val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadTownBoundariesUseCase.invoke(any())).thenReturn(Result.success(null))
        }
        val vm = buildViewModel(
            observeMyCoverageUseCase = mock<ObserveMyCoverageUseCase>().also {
                whenever(it.invoke(any(), any(), any())).thenReturn(
                    flowOf(Result.success(coverage(listOf(cebCoverage, davCoverage), CoverageFraming.Country))),
                )
            },
            loadTownBoundariesUseCase = loadTownBoundariesUseCase,
            observeFindingsUseCase = observeFindingsUseCase,
        )
        advanceUntilIdle()

        vm.onMapTap(0.5f, 0.5f) // hits Cebu, kicks off a species load that never resolves yet
        advanceUntilIdle()
        assertEquals("CEB", vm.uiState.value.selected?.code)
        assertTrue(vm.uiState.value.selected?.species.isNullOrEmpty())

        vm.onMapTap(5.5f, 5.5f) // navigate away before Cebu's load completes — hits Davao
        advanceUntilIdle()
        assertEquals("DAV", vm.uiState.value.selected?.code)

        val resolvedSpecies = listOf(SpeciesFinding("Aedes aegypti", 4, 1f, "100%"))
        deferred.complete(FindingsResult(resolvedSpecies, 4))
        advanceUntilIdle()

        // Cebu's late completion must not clobber the current (Davao) selection.
        assertEquals("DAV", vm.uiState.value.selected?.code)

        vm.onMapTap(0.5f, 0.5f) // tap Cebu again — should read from cache, not be stuck empty
        advanceUntilIdle()

        assertEquals(resolvedSpecies, vm.uiState.value.selected?.species)
    }
}
