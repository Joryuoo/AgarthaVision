package com.agarthavision.domain.usecase.coverage

import app.cash.turbine.test
import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.model.PeriodWindows
import com.agarthavision.domain.model.TownCoverage
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.repository.CoverageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMyCoverageUseCaseTest {

    private val coverageRepository: CoverageRepository = mock()
    private val loadAreaDirectoryUseCase: LoadAreaDirectoryUseCase = mock()

    private val useCase = ObserveMyCoverageUseCase(coverageRepository, loadAreaDirectoryUseCase)

    private val windows = PeriodWindows(
        current = TimeWindow(0L, 1_000L),
        previous = TimeWindow(-1_000L, 0L),
        buckets = List(7) { TimeWindow(0L, 1L) },
    )

    @Test
    fun `repository emission flows through to an aggregated Result success`() = runTest {
        val directory = AreaDirectory(towns = emptyMap(), provinces = emptyMap())
        whenever(loadAreaDirectoryUseCase()).thenReturn(Result.success(directory))
        whenever(coverageRepository.observeTownCoverage("user-1", windows.current))
            .thenReturn(flowOf(listOf(TownCoverage(null, 3, 1))))

        useCase("user-1", HomePeriod.TODAY, windows).test {
            val result = awaitItem()
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().unlocatedSmears == 3)
            awaitComplete()
        }
    }

    @Test
    fun `a repository exception becomes Result failure, not a crash`() = runTest {
        val directory = AreaDirectory(towns = emptyMap(), provinces = emptyMap())
        whenever(loadAreaDirectoryUseCase()).thenReturn(Result.success(directory))
        whenever(coverageRepository.observeTownCoverage("user-1", windows.current))
            .thenReturn(
                flow<List<TownCoverage>> { throw IllegalStateException("boom") },
            )

        useCase("user-1", HomePeriod.TODAY, windows).test {
            val result = awaitItem()
            assertTrue(result.isFailure)
            awaitComplete()
        }
    }

    @Test
    fun `cancellation is not swallowed into a Result failure`() = runTest {
        val directory = AreaDirectory(towns = emptyMap(), provinces = emptyMap())
        whenever(loadAreaDirectoryUseCase()).thenReturn(Result.success(directory))
        whenever(coverageRepository.observeTownCoverage("user-1", windows.current))
            .thenReturn(flow { awaitCancellation() })

        val results = mutableListOf<Result<MyCoverage>>()
        val job = launch {
            useCase("user-1", HomePeriod.TODAY, windows).collect { results.add(it) }
        }
        advanceUntilIdle()
        job.cancelAndJoin()

        assertTrue("cancellation must not surface as Result.failure", results.none { it.isFailure })
    }
}
