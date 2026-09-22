package com.agarthavision.ui.patients

import app.cash.turbine.test
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.patients.ObservePatientsUseCase
import com.agarthavision.domain.usecase.patients.PatientSort
import com.agarthavision.domain.usecase.patients.PatientsQuery
import com.agarthavision.domain.usecase.patients.PatientsResult
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PatientsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mock()
    private val psgcRepository: PsgcRepository = mock()
    private val observePatientsUseCase: ObservePatientsUseCase = mock()
    private val searchBarangaysUseCase = SearchBarangaysUseCase(psgcRepository)

    @Before
    fun setUp() {
        whenever(authRepository.observeLocalIdentity())
            .thenReturn(flowOf(LocalIdentity(userId = USER_ID, email = "user@test.org")))
        whenever(observePatientsUseCase(anyOrNull(), any(), any()))
            .thenReturn(flowOf(PatientsResult(items = emptyList(), total = 0)))
    }

    private fun createViewModel() = PatientsViewModel(
        observePatientsUseCase = observePatientsUseCase,
        observeLocalIdentityUseCase = ObserveLocalIdentityUseCase(authRepository),
        searchBarangaysUseCase = searchBarangaysUseCase,
    )

    @Test
    fun `initial state has defaults and is not narrowed`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals("", state.searchQuery)
            assertEquals(PatientSort.RECENT, state.sort)
            assertNull(state.selectedSex)
            assertNull(state.barangayPickerState.selected)
            assertNull(state.minAge)
            assertNull(state.maxAge)
            assertFalse(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query updates and marks state as narrowed`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSearchQueryChanged("Cruz")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals("Cruz", state.searchQuery)
            assertTrue(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting sex updates state and marks state as narrowed`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSexSelected(Sex.FEMALE)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(Sex.FEMALE, state.selectedSex)
            assertTrue(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting barangay updates state and marks state as narrowed`() = runTest {
        whenever(psgcRepository.searchBarangays("Lahug", SearchBarangaysUseCase.RESULT_LIMIT))
            .thenReturn(listOf(lahug()))
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onBarangayQueryChanged("Lahug")
            advanceUntilIdle()
            vm.onBarangaySelected(LAHUG)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(LAHUG, state.barangayPickerState.selected?.code)
            assertTrue(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting age bounds updates state and marks state as narrowed`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onMinAgeChanged(10)
            vm.onMaxAgeChanged(60)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(10, state.minAge)
            assertEquals(60, state.maxAge)
            assertTrue(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear filters resets sex, barangay, and age bounds`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSexSelected(Sex.MALE)
            vm.onMinAgeChanged(5)
            vm.onMaxAgeChanged(15)
            advanceUntilIdle()

            var state = expectMostRecentItem()
            assertTrue(state.isNarrowed)

            vm.onClearFilters()
            advanceUntilIdle()

            state = expectMostRecentItem()
            assertNull(state.selectedSex)
            assertNull(state.barangayPickerState.selected)
            assertNull(state.minAge)
            assertNull(state.maxAge)
            assertFalse(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort updates independently without narrowing`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSortSelected(PatientSort.LAST_NAME)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(PatientSort.LAST_NAME, state.sort)
            assertFalse(state.isNarrowed)
            assertEquals(1, state.activeFilterCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onApplyFilters updates all filter fields and calculates activeFilterCount`() = runTest {
        whenever(psgcRepository.searchBarangays("Lahug", SearchBarangaysUseCase.RESULT_LIMIT))
            .thenReturn(listOf(lahug()))
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            assertEquals(0, expectMostRecentItem().activeFilterCount)

            vm.onApplyFilters(
                sort = PatientSort.FIRST_NAME,
                sex = Sex.MALE,
                barangay = lahug(),
                minAge = 18,
                maxAge = 65,
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(PatientSort.FIRST_NAME, state.sort)
            assertEquals(Sex.MALE, state.selectedSex)
            assertEquals(LAHUG, state.barangayPickerState.selected?.code)
            assertEquals(18, state.minAge)
            assertEquals(65, state.maxAge)
            assertEquals(4, state.activeFilterCount)
            assertTrue(state.isNarrowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation events emit correctly`() = runTest {
        val vm = createViewModel()

        vm.events.test {
            vm.onPatientSelected("p-1")
            assertEquals(PatientsEvent.OpenPatient("p-1"), awaitItem())

            vm.onEditPatient("p-2")
            assertEquals(PatientsEvent.EditPatient("p-2"), awaitItem())

            vm.onCreatePatient()
            assertEquals(PatientsEvent.CreatePatient, awaitItem())
        }
    }

    @Test
    fun `load more increments query limit by 10`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            advanceUntilIdle()
            verify(observePatientsUseCase).invoke(
                userId = eq(USER_ID),
                query = eq(PatientsQuery(limit = 10)),
                asOf = any(),
            )

            vm.onLoadMore()
            advanceUntilIdle()

            verify(observePatientsUseCase).invoke(
                userId = eq(USER_ID),
                query = eq(PatientsQuery(limit = 20)),
                asOf = any(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun lahug() =
        PsgcBarangay(LAHUG, "Lahug", "City of Cebu", null, "Region VII (Central Visayas)")

    private companion object {
        const val USER_ID = "user-123"
        const val LAHUG = "0723017001"
    }
}
