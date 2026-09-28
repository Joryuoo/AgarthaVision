package com.agarthavision.ui.components

import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The barangay picker's regression net, where the picker actually lives.
 *
 * These cases used to sit in `SessionPickerViewModelTest`, because the New Session sheet was
 * the only host. The sheet no longer asks for a barangay — it moved to the patient, which is
 * the unit surveillance aggregates on — so the behaviour is pinned here, against the delegate
 * both hosts share, rather than being deleted with the sheet's picker.
 *
 * The real [SearchBarangaysUseCase] runs over a mocked repository so its minimum-query-length
 * rule and `RESULT_LIMIT` are exercised rather than stubbed away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BarangayPickerDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val psgcRepository: PsgcRepository = mock()
    private val searchBarangaysUseCase = SearchBarangaysUseCase(psgcRepository)

    /**
     * Stands in for the host's `viewModelScope`: on the test dispatcher, so the scheduler is
     * shared and `advanceUntilIdle` stays deterministic, but not a child of the test
     * coroutine, which would leave `runTest` waiting on a pipeline that collects forever.
     */
    private val pickerScope = CoroutineScope(mainDispatcherRule.testDispatcher)

    @After
    fun tearDown() = pickerScope.cancel()

    @Test
    fun `a burst of keystrokes debounces into one search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val delegate = BarangayPickerDelegate(searchBarangaysUseCase)
            delegate.start(pickerScope)

            delegate.onQueryChanged("l")
            delegate.onQueryChanged("la")
            delegate.onQueryChanged("lah")
            delegate.onQueryChanged("lahu")
            advanceUntilIdle()

            verify(psgcRepository).searchBarangays(query = eq("lahu"), limit = any())
        }

    @Test
    fun `selecting a barangay clears the query and the results`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val delegate = BarangayPickerDelegate(searchBarangaysUseCase)
            delegate.start(pickerScope)

            delegate.onQueryChanged("lahug")
            advanceUntilIdle()
            delegate.onSelected(LAHUG.code)
            advanceUntilIdle()

            val state = delegate.state.value
            assertEquals(LAHUG, state.selected)
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
        }

    @Test
    fun `clearing drops the selection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val delegate = BarangayPickerDelegate(searchBarangaysUseCase)
            delegate.start(pickerScope)

            delegate.onQueryChanged("lahug")
            advanceUntilIdle()
            delegate.onSelected(LAHUG.code)
            advanceUntilIdle()

            delegate.onCleared()
            advanceUntilIdle()

            assertNull(delegate.state.value.selected)
        }

    @Test
    fun `a code that matches nothing in the current results is ignored`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val delegate = BarangayPickerDelegate(searchBarangaysUseCase)
            delegate.start(pickerScope)

            delegate.onQueryChanged("lahug")
            advanceUntilIdle()
            // Only reachable if the results changed under a tap already in flight.
            delegate.onSelected("0000000000")
            advanceUntilIdle()

            assertNull(delegate.state.value.selected)
        }

    @Test
    fun `preselect seeds an edit form without going through a search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val delegate = BarangayPickerDelegate(searchBarangaysUseCase)
            delegate.start(pickerScope)

            delegate.preselect(LAHUG)
            advanceUntilIdle()

            assertEquals(LAHUG, delegate.state.value.selected)
            verify(psgcRepository, never()).searchBarangays(any(), any())
        }

    private companion object {
        private val LAHUG = PsgcBarangay(
            code = "0730600051",
            name = "Lahug",
            cityMuniName = "City of Cebu",
            provinceName = null,
            regionName = "Region VII (Central Visayas)",
        )
    }
}
