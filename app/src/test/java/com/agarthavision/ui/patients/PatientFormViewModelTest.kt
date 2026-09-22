package com.agarthavision.ui.patients

import androidx.lifecycle.SavedStateHandle
import com.agarthavision.domain.model.CLINICAL_ZONE
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
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
        // Default: no duplicates found. Existing tests that call onSave() rely on this so
        // duplicate dialogs don't surface where they aren't being tested. Individual tests
        // can re-stub to return specific duplicates.
        // findDuplicates is a suspend function, so the stub must run inside a coroutine context.
        runBlocking {
            whenever(
                patientRepository.findDuplicates(
                    userId = any(),
                    lastname = any(),
                    firstname = any(),
                    middleName = anyOrNull(),
                    birthdate = any(),
                    sex = any(),
                    excludingId = any(),
                ),
            ).thenReturn(emptyList())
        }
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
    fun `an empty form does not save and reports missing clinical fields`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()

        vm.onSave()
        advanceUntilIdle()

        val errors = vm.state.value.errors
        // Both names blank falls back to codename mode, so name errors are not added
        assertTrue(PatientFormError.LASTNAME_REQUIRED !in errors)
        assertTrue(PatientFormError.FIRSTNAME_REQUIRED !in errors)
        assertTrue(PatientFormError.SEX_REQUIRED in errors)
        assertTrue(PatientFormError.BIRTHDATE_REQUIRED in errors)
        assertTrue(PatientFormError.BARANGAY_REQUIRED in errors)
        verify(patientRepository, never()).insert(any())
    }

    @Test
    fun `partial name requires the missing name field`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        vm.onLastnameChanged("Cruz")

        vm.onSave()
        advanceUntilIdle()

        val errors = vm.state.value.errors
        assertTrue(PatientFormError.FIRSTNAME_REQUIRED in errors)
        assertTrue(PatientFormError.LASTNAME_REQUIRED !in errors)
    }

    @Test
    fun `saving with blank names generates codename`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        vm.onSexSelected(Sex.MALE)
        val birthdate = LocalDate.now(CLINICAL_ZONE).minusYears(24)
        vm.onBirthdateSelected(birthdate)
        whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(lahug()))
        whenever(patientRepository.getExistingCodenamesByPrefix(any(), any())).thenReturn(emptyList())
        vm.onBarangayQueryChanged("Lahug")
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        vm.onSave()
        advanceUntilIdle()

        val captor = argumentCaptor<Patient>()
        verify(patientRepository).insert(captor.capture())
        val saved = captor.firstValue
        assertTrue(saved.isCodename)
        assertTrue(
            "Expected lastname to end with -M24 (word-based codename), got: ${saved.lastname}",
            saved.lastname.endsWith("-M24"),
        )
        assertEquals("", saved.firstname)
    }

    @Test
    fun `saving with custom codename persists custom identifier`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        vm.onUseCustomCodenameToggled(true)
        vm.onCustomCodenameChanged("CUSTOM-001")
        vm.onSexSelected(Sex.FEMALE)
        val birthdate = LocalDate.now(CLINICAL_ZONE).minusYears(20)
        vm.onBirthdateSelected(birthdate)
        whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(lahug()))
        vm.onBarangayQueryChanged("Lahug")
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        vm.onSave()
        advanceUntilIdle()

        val captor = argumentCaptor<Patient>()
        verify(patientRepository).insert(captor.capture())
        val saved = captor.firstValue
        assertEquals("CUSTOM-001", saved.lastname)
        assertEquals("", saved.firstname)
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
    fun `a future birthdate is rejected, not quietly clamped to today`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)
        vm.onBirthdateSelected(LocalDate.now(CLINICAL_ZONE).plusDays(1))

        vm.onSave()
        advanceUntilIdle()

        // This used to route through sanitizeDateRange, which coerces to today — so a future
        // date saved silently as a patient born this morning. Wrong age, on a clinical
        // record, with nothing on screen saying anything happened.
        assertTrue(PatientFormError.BIRTHDATE_IN_FUTURE in vm.state.value.errors)
        verify(patientRepository, never()).insert(any())
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

    // ── duplicate detection ───────────────────────────────────────────────────

    @Test
    fun `onSave sets pendingSameBarangayDuplicate for a same-barangay identity match`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        val duplicate = existingPatient() // psgcBarangayCode == LAHUG — same barangay
        whenever(
            patientRepository.findDuplicates(any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(listOf(duplicate))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        vm.onSave()
        advanceUntilIdle()

        assertNotNull(vm.state.value.pendingSameBarangayDuplicate)
        assertTrue(vm.state.value.pendingDifferentBarangayDuplicates.isEmpty())
        verify(patientRepository, never()).insert(any())
    }

    @Test
    fun `onSave sets pendingDifferentBarangayDuplicates for a different-barangay identity match`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        // Patient lives in a different barangay than the form's selected one (LAHUG)
        val duplicate = existingPatient().copy(psgcBarangayCode = TALAMBAN)
        whenever(
            patientRepository.findDuplicates(any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(listOf(duplicate))
        whenever(psgcRepository.getBarangay(TALAMBAN))
            .thenReturn(PsgcBarangay(TALAMBAN, "Talamban", "City of Cebu", null, "Region VII"))

        vm.onSave()
        advanceUntilIdle()

        assertNull(vm.state.value.pendingSameBarangayDuplicate)
        assertEquals(1, vm.state.value.pendingDifferentBarangayDuplicates.size)
        assertEquals(TALAMBAN, vm.state.value.pendingDifferentBarangayDuplicates.first().patient.psgcBarangayCode)
        verify(patientRepository, never()).insert(any())
    }

    @Test
    fun `onSave passes excludingId equal to the patient being edited so it is not a self-duplicate`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val existing = existingPatient()
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existing)
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()

            // Trigger save without changing anything — all fields are already valid from load
            vm.onSave()
            advanceUntilIdle()

            val captor = argumentCaptor<String>()
            verify(patientRepository).findDuplicates(
                userId = any(),
                lastname = any(),
                firstname = any(),
                middleName = anyOrNull(),
                birthdate = any(),
                sex = any(),
                excludingId = captor.capture(),
            )
            assertEquals(
                "The excludingId passed to findDuplicates must be the patient's own id",
                PATIENT_ID,
                captor.firstValue,
            )
        }

    @Test
    fun `onSave passes middleName as null when the field is blank so duplicate check matches persist coercion`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            fillValid(vm)
            advanceUntilIdle()
            vm.onBarangaySelected(LAHUG)
            vm.onMiddleNameChanged("   ") // whitespace only — must be coerced to null

            vm.onSave()
            advanceUntilIdle()

            // isNull() matcher confirms findDuplicates receives null for a blank middle name,
            // mirroring the trim().ifBlank { null } coercion used at persist time. A mismatch
            // here would cause false negatives in the duplicate check.
            verify(patientRepository).findDuplicates(
                userId = any(),
                lastname = any(),
                firstname = any(),
                middleName = isNull(),
                birthdate = any(),
                sex = any(),
                excludingId = any(),
            )
        }

    // ── post-duplicate callbacks ──────────────────────────────────────────────

    @Test
    fun `onProceedAsNewPatient clears duplicate state and persists the patient`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        // Trigger the same-barangay duplicate dialog
        whenever(
            patientRepository.findDuplicates(any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(listOf(existingPatient()))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        vm.onSave()
        advanceUntilIdle()
        assertNotNull(vm.state.value.pendingSameBarangayDuplicate)

        vm.onProceedAsNewPatient()
        advanceUntilIdle()

        assertNull(vm.state.value.pendingSameBarangayDuplicate)
        assertTrue(vm.state.value.pendingDifferentBarangayDuplicates.isEmpty())
        verify(patientRepository).insert(any())
    }

    @Test
    fun `onDismissDuplicate clears both duplicate states without saving`() = runTest(
        mainDispatcherRule.testDispatcher.scheduler,
    ) {
        val vm = viewModel()
        fillValid(vm)
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG)

        whenever(
            patientRepository.findDuplicates(any(), any(), any(), anyOrNull(), any(), any(), any()),
        ).thenReturn(listOf(existingPatient()))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        vm.onSave()
        advanceUntilIdle()
        assertNotNull(vm.state.value.pendingSameBarangayDuplicate)

        vm.onDismissDuplicate()
        advanceUntilIdle()

        assertNull(vm.state.value.pendingSameBarangayDuplicate)
        assertTrue(vm.state.value.pendingDifferentBarangayDuplicates.isEmpty())
        verify(patientRepository, never()).insert(any())
    }

    @Test
    fun `onGoToExistingPatient emits OpenExisting with the correct id and clears duplicate state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onGoToExistingPatient(PATIENT_ID)
            advanceUntilIdle()

            assertEquals(listOf(PatientFormEvent.OpenExisting(PATIENT_ID)), events)
            assertNull(vm.state.value.pendingSameBarangayDuplicate)
            assertTrue(vm.state.value.pendingDifferentBarangayDuplicates.isEmpty())
            job.cancel()
        }

    // ── dirty check / discard guard ───────────────────────────────────────────

    @Test
    fun `onCancel on a pristine form emits Cancelled immediately without showing the discard dialog`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onCancel()
            advanceUntilIdle()

            assertEquals(listOf(PatientFormEvent.Cancelled), events)
            assertFalse(vm.state.value.showDiscardConfirm)
            job.cancel()
        }

    @Test
    fun `onCancel after typing any field shows the discard dialog instead of emitting Cancelled`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onLastnameChanged("A")

            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onCancel()
            advanceUntilIdle()

            assertTrue(vm.state.value.showDiscardConfirm)
            assertTrue(events.isEmpty())
            job.cancel()
        }

    @Test
    fun `onDiscardConfirmed emits Cancelled and resets form state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onLastnameChanged("A")
            vm.onCancel()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDiscardConfirm)
            assertTrue(vm.state.value.isDirty)

            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onDiscardConfirmed()
            advanceUntilIdle()

            assertEquals(listOf(PatientFormEvent.Cancelled), events)
            assertFalse(vm.state.value.showDiscardConfirm)
            assertFalse(vm.state.value.isDirty)
            assertEquals("", vm.state.value.lastname)
            job.cancel()
        }

    @Test
    fun `onDiscardConfirmed when editing resets form to original patient values`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = existingPatient()
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(patient)
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())
            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()
            assertEquals("Cruz", vm.state.value.lastname)
            assertFalse(vm.state.value.isDirty)

            vm.onLastnameChanged("Modified")
            advanceUntilIdle()
            assertTrue(vm.state.value.isDirty)

            vm.onCancel()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDiscardConfirm)

            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onDiscardConfirmed()
            advanceUntilIdle()

            assertEquals(listOf(PatientFormEvent.Cancelled), events)
            assertFalse(vm.state.value.showDiscardConfirm)
            assertFalse(vm.state.value.isDirty)
            assertEquals("Cruz", vm.state.value.lastname)
            job.cancel()
        }

    @Test
    fun `onDiscardDismissed clears showDiscardConfirm without emitting any event`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.onLastnameChanged("A")
            vm.onCancel()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDiscardConfirm)

            val events = mutableListOf<PatientFormEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.onDiscardDismissed()
            advanceUntilIdle()

            assertFalse(vm.state.value.showDiscardConfirm)
            assertTrue(events.isEmpty())
            job.cancel()
        }

    @Test
    fun `isDirty is false initially for new patient and true after field change`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            assertFalse(vm.state.value.isDirty)
            vm.onLastnameChanged("Reyes")
            advanceUntilIdle()
            assertTrue(vm.state.value.isDirty)
        }

    @Test
    fun `isDirty is false after existing patient loads and true after field edit`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existingPatient())
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()

            assertFalse(vm.state.value.isDirty)
            vm.onFirstnameChanged("Different")
            advanceUntilIdle()
            assertTrue(vm.state.value.isDirty)
        }

    // ── loadPatient (sheet-reuse API) ─────────────────────────────────────────

    @Test
    fun `loadPatient null resets VM to blank new-patient form`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existingPatient())
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())
            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()
            assertTrue(vm.state.value.isEditing)
            assertEquals("Cruz", vm.state.value.lastname)

            vm.loadPatient(null)
            advanceUntilIdle()

            assertFalse(vm.state.value.isEditing)
            assertEquals("", vm.state.value.lastname)
            assertNull(vm.state.value.barangay.selected)
            assertFalse(vm.state.value.isDirty)
        }

    @Test
    fun `loadPatient with same id and loaded set is a no-op and does not re-query the repository`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(existingPatient())
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())
            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()

            // First loadPatient call with same id — should skip re-load
            vm.loadPatient(PATIENT_ID)
            advanceUntilIdle()

            // getPatientById must have been called exactly once (from init's loadExisting, not again)
            org.mockito.kotlin.verify(patientRepository, org.mockito.kotlin.times(1))
                .getPatientById(PATIENT_ID)
        }

    @Test
    fun `loadPatient with a different id loads the new patient`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patientA = existingPatient()
            val patientB = existingPatient().copy(id = "p-2", lastname = "Santos", firstname = "Liza")
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(patientA)
            whenever(patientRepository.getPatientById("p-2")).thenReturn(patientB)
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()
            assertEquals("Cruz", vm.state.value.lastname)

            vm.loadPatient("p-2")
            advanceUntilIdle()

            assertEquals("Santos", vm.state.value.lastname)
            assertEquals("Liza", vm.state.value.firstname)
        }

    @Test
    fun `loadPatient after successful save reloads patient from DB — sheet re-open must not show blank form`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = existingPatient()
            whenever(patientRepository.getPatientById(PATIENT_ID)).thenReturn(patient)
            whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

            val vm = viewModel(PATIENT_ID)
            advanceUntilIdle()
            assertTrue(vm.state.value.isEditing)
            assertEquals("Cruz", vm.state.value.lastname)

            // Save the patient (all required fields are already loaded — no validation error).
            vm.onSave()
            advanceUntilIdle()
            // After save the fields StateFlow is cleared.
            assertFalse(vm.state.value.isEditing)
            assertEquals("", vm.state.value.lastname)

            // Sheet dismissed then re-opened for the same patient: loadPatient is called again.
            vm.loadPatient(PATIENT_ID)
            advanceUntilIdle()

            // The form must show the patient's data, not the blank cleared state.
            // Fails today because loadPatient hits the early-return guard
            // (patientId == PATIENT_ID && loaded != null) and never calls loadExisting again.
            assertTrue(
                "isEditing must be true after re-opening existing patient post-save",
                vm.state.value.isEditing,
            )
            assertEquals(
                "lastname must be restored to 'Cruz' after re-opening post-save",
                "Cruz",
                vm.state.value.lastname,
            )
        }

    @Test
    fun `isDirty reflects sex and birthdate changes for new patient`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            assertFalse(vm.state.value.isDirty)

            vm.onSexSelected(Sex.FEMALE)
            advanceUntilIdle()
            assertTrue(vm.state.value.isDirty)
        }

    @Test
    fun `isDirty is false immediately after successful save`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            fillValid(vm)
            advanceUntilIdle()
            vm.onBarangaySelected(LAHUG)
            assertTrue(vm.state.value.isDirty)

            vm.onSave()
            advanceUntilIdle()

            assertFalse(vm.state.value.isDirty)
        }

    @Test
    fun `onDiscardConfirmed for new patient resets isDirty and clears barangay`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            fillValid(vm)
            advanceUntilIdle()
            vm.onBarangaySelected(LAHUG)
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(lahug()))
            assertTrue(vm.state.value.isDirty)

            vm.onCancel()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDiscardConfirm)

            vm.onDiscardConfirmed()
            advanceUntilIdle()

            assertFalse(vm.state.value.isDirty)
            assertFalse(vm.state.value.isEditing)
            assertNull(vm.state.value.barangay.selected)
            assertEquals("", vm.state.value.lastname)
        }

    @Test
    fun `new patient form defaults to useCustomCodename true and not dirty`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            assertTrue(vm.state.value.useCustomCodename)
            assertFalse(vm.state.value.isDirty)
        }

    @Test
    fun `toggling useCustomCodename updates state and dirty tracking`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            assertTrue(vm.state.value.useCustomCodename)
            assertFalse(vm.state.value.isDirty)

            vm.onUseCustomCodenameToggled(false)
            advanceUntilIdle()
            assertFalse(vm.state.value.useCustomCodename)
            assertTrue(vm.state.value.isDirty)

            vm.onUseCustomCodenameToggled(true)
            advanceUntilIdle()
            assertTrue(vm.state.value.useCustomCodename)
            assertFalse(vm.state.value.isDirty)
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
        const val TALAMBAN = "0723017002"
    }
}
