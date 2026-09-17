package com.agarthavision.ui.patients

import androidx.lifecycle.SavedStateHandle
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PatientFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val patientRepository: PatientRepository = mock()
    private val psgcRepository: PsgcRepository = mock()
    private val authRepository: AuthRepository = mock()
    private val searchBarangaysUseCase = SearchBarangaysUseCase(psgcRepository)

    @Before
    fun setUp() {
        whenever(authRepository.observeLocalIdentity())
            .thenReturn(flowOf(LocalIdentity(userId = USER_ID, email = "m@example.org")))
    }

    private fun viewModel(patientId: String? = null) = PatientFormViewModel(
        patientRepository = patientRepository,
        psgcRepository = psgcRepository,
        observeLocalIdentityUseCase = ObserveLocalIdentityUseCase(authRepository),
        searchBarangaysUseCase = searchBarangaysUseCase,
        savedStateHandle = SavedStateHandle(
            if (patientId == null) emptyMap() else mapOf("patientId" to patientId),
        ),
    )

    private suspend fun fillValid(vm: PatientFormViewModel) {
        vm.onLastnameChanged("Cruz")
        vm.onFirstnameChanged("Gerald")
        vm.onSexSelected(Sex.MALE)
        vm.onBirthdateSelected(LocalDate.of(1998, 7, 30))
        whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(lahug()))
        vm.onBarangayQueryChanged("Lahug")
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    fun `an empty form does not save and reports every missing field`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()

        vm.onSave()
        advanceUntilIdle()

        val errors = vm.state.value.errors
        assertTrue(PatientFormError.LASTNAME_REQUIRED in errors)
        assertTrue(PatientFormError.FIRSTNAME_REQUIRED in errors)
        assertTrue(PatientFormError.SEX_REQUIRED in errors)
        assertTrue(PatientFormError.BIRTHDATE_REQUIRED in errors)
        assertTrue(PatientFormError.BARANGAY_REQUIRED in errors)
        verify(patientRepository, never()).insert(any())
    }

    @Test
    fun `a middle name is not required`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()

        vm.onSave()
        advanceUntilIdle()

        // Many patients do not supply one; a required field here would collect junk.
        assertTrue(vm.state.value.errors.none { it.name.contains("MIDDLE") })
    }

    @Test
    fun `a future birthdate is clamped rather than stored`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()

        vm.onBirthdateSelected(LocalDate.now().plusYears(2))
        advanceUntilIdle()

        val stored = vm.state.value.birthdate
        assertTrue("a birthdate must never be in the future", stored!! <= LocalDate.now())
    }

    @Test
    fun `a blank middle name saves as null rather than an empty string`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)
        vm.onMiddleNameChanged("   ")

        vm.onSave()
        advanceUntilIdle()

        val captor = argumentCaptor<Patient>()
        verify(patientRepository).insert(captor.capture())
        // displayName keys off null to decide whether an initial belongs in the name.
        assertNull(captor.firstValue.middleName)
        assertEquals("Cruz, Gerald", captor.firstValue.displayName)
    }

    @Test
    fun `a valid new patient is inserted with the signed-in medtech as creator`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        vm.onSave()
        advanceUntilIdle()

        val captor = argumentCaptor<Patient>()
        verify(patientRepository).insert(captor.capture())
        assertEquals(USER_ID, captor.firstValue.createdBy)
        assertEquals(LAHUG, captor.firstValue.psgcBarangayCode)
        assertEquals(Sex.MALE, captor.firstValue.sex)
    }

    // ── editing ───────────────────────────────────────────────────────────────

    @Test
    fun `editing updates rather than inserts, and keeps the original creator and createdAt`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val existing = existingPatient()
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existing)
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()
            vm.onLastnameChanged("Cruz-Reyes")

            vm.onSave()
            advanceUntilIdle()

            val captor = argumentCaptor<Patient>()
            verify(patientRepository).update(captor.capture())
            verify(patientRepository, never()).insert(any())
            // created_by is provenance and must survive an edit; createdAt likewise.
            assertEquals(existing.createdBy, captor.firstValue.createdBy)
            assertEquals(existing.createdAt, captor.firstValue.createdAt)
            assertEquals("Cruz-Reyes", captor.firstValue.lastname)
        }

    @Test
    fun `opening on an existing patient preselects its barangay`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existingPatient())
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        val vm = viewModel(PATIENT_ID)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEditing)
        assertEquals("Lahug", vm.state.value.barangay.selected?.name)
    }

    private fun existingPatient() = Patient(
        id = PATIENT_ID,
        lastname = "Cruz",
        firstname = "Gerald",
        middleName = "Mendoza",
        sex = Sex.MALE,
        birthdate = LocalDate.of(1998, 7, 30),
        psgcBarangayCode = LAHUG,
        createdBy = "someone-else",
        createdAt = Instant.ofEpochMilli(1_600_000_000_000),
        updatedAt = Instant.ofEpochMilli(1_600_000_000_000),
    )

    private fun lahug() =
        PsgcBarangay(LAHUG, "Lahug", "City of Cebu", null, "Region VII (Central Visayas)")

    private companion object {
        const val USER_ID = "user-a"
        const val PATIENT_ID = "p-1"
        const val LAHUG = "0723017001"
    }
}
