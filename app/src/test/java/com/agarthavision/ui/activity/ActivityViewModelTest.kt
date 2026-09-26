package com.agarthavision.ui.activity

import app.cash.turbine.test
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.home.ObserveRecentActivityUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")),
            )
        }
    private val observeRecentActivityUseCase: ObserveRecentActivityUseCase = mock()

    private fun viewModel() = ActivityViewModel(
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        observeRecentActivityUseCase = observeRecentActivityUseCase,
    )

    private fun activityItem(occurredAt: Long) = ActivityItem.PatientAdded(
        patientId = "p-$occurredAt",
        maskedName = "R.*, J*.",
        occurredAt = occurredAt,
    )

    @Test
    fun `loads items from the use case`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val items = listOf(activityItem(1_000L), activityItem(2_000L))
        whenever(observeRecentActivityUseCase.invoke(eq("user-1"), any())).thenReturn(flowOf(items))

        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertEquals(items, snapshot.items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onLoadMore increases the limit and re-queries`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        whenever(observeRecentActivityUseCase.invoke(eq("user-1"), any())).thenReturn(
            flowOf((1..50).map { activityItem(it.toLong()) }),
        )

        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertTrue(snapshot.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }

        vm.onLoadMore()
        advanceUntilIdle()

        verify(observeRecentActivityUseCase).invoke("user-1", 70)
    }

    @Test
    fun `onLoadMore actually increases the number of items shown, not just an internal counter`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Limit-dependent stub: unlike a single flowOf(...) for any limit, this proves the
            // observed item count truly grows when the use case is re-queried with a bigger limit.
            whenever(observeRecentActivityUseCase.invoke(eq("user-1"), any())).thenAnswer { invocation ->
                val limit = invocation.getArgument<Int>(1)
                flowOf((1..limit).map { activityItem(it.toLong()) })
            }

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertEquals(50, snapshot.items.size)
                assertTrue(snapshot.canLoadMore)

                vm.onLoadMore()
                snapshot = awaitItem()
                while (snapshot.items.size == 50) {
                    snapshot = awaitItem()
                }
                assertEquals(70, snapshot.items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canLoadMore is false once the use case returns fewer items than the current limit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Only 3 items total ever exist, well under the initial limit of 50.
            whenever(observeRecentActivityUseCase.invoke(eq("user-1"), any())).thenReturn(
                flowOf(listOf(activityItem(1L), activityItem(2L), activityItem(3L))),
            )

            val vm = viewModel()
            vm.uiState.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                assertEquals(3, snapshot.items.size)
                assertTrue("fewer items than the limit must mean nothing more to load", !snapshot.canLoadMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `null identity gives an empty list`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        whenever(observeLocalIdentityUseCase.invoke()).thenReturn(flowOf(null))

        val vm = viewModel()
        vm.uiState.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertTrue(snapshot.items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
