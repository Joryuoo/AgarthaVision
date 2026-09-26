package com.agarthavision.ui.dashboard.coverage

import app.cash.turbine.test
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MyCoverageCardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")),
            )
        }
    private val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
    private val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()

    private fun viewModel() = MyCoverageCardViewModel(
        clock = clock,
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        observeMyCoverageUseCase = observeMyCoverageUseCase,
        loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
    )

    private fun emptyCoverage(period: HomePeriod) = MyCoverage(
        period = period,
        totals = AreaCount(0, 0),
        unlocatedSmears = 0,
        provinces = emptyList(),
        islandGroupCounts = emptyMap(),
        framing = CoverageFraming.Empty,
    )

    @Test
    fun `setPeriod triggers a re-query with the new period's window`() = runTest {
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenAnswer { invocation ->
                val period = invocation.arguments[1] as HomePeriod
                flowOf(Result.success(emptyCoverage(period)))
            }

        val vm = viewModel()
        vm.uiState.test {
            skipDefaultsUntilEmptyFor(HomePeriod.TODAY)

            vm.setPeriod(HomePeriod.LAST_7_DAYS)
            var state = awaitItem()
            while (state !is MyCoverageCardUiState.Empty || state.period != HomePeriod.LAST_7_DAYS) {
                state = awaitItem()
            }
            assertEquals(HomePeriod.LAST_7_DAYS, (state as MyCoverageCardUiState.Empty).period)
        }

        verify(observeMyCoverageUseCase).invoke(eq("user-1"), eq(HomePeriod.LAST_7_DAYS), any())
    }

    @Test
    fun `a Result failure from the use case surfaces as Error state`() = runTest {
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.failure(IllegalStateException("boom"))))

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state !is MyCoverageCardUiState.Error) {
                state = awaitItem()
            }
            assertTrue(state is MyCoverageCardUiState.Error)
        }
    }

    @Test
    fun `an empty MyCoverage surfaces as Empty`() = runTest {
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.success(emptyCoverage(HomePeriod.TODAY))))

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state !is MyCoverageCardUiState.Empty) {
                state = awaitItem()
            }
            assertTrue(state is MyCoverageCardUiState.Empty)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<MyCoverageCardUiState>.skipDefaultsUntilEmptyFor(
        period: HomePeriod,
    ) {
        var state = awaitItem()
        while (state !is MyCoverageCardUiState.Empty || state.period != period) {
            state = awaitItem()
        }
    }
}
