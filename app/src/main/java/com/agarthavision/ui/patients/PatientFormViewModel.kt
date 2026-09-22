package com.agarthavision.ui.patients

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.patient.CodenameGenerator
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.BarangayPickerDelegate
import com.agarthavision.ui.components.BarangayPickerState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which field failed validation. Resolved to copy by the screen, per C11. */
enum class PatientFormError {
    LASTNAME_REQUIRED,
    FIRSTNAME_REQUIRED,
    SEX_REQUIRED,
    BIRTHDATE_REQUIRED,
    BIRTHDATE_IN_FUTURE,
    BARANGAY_REQUIRED,
    SAVE_FAILED,
}

/**
 * A duplicate patient that was detected before saving, paired with the resolved name of the
 * barangay they are registered in (resolved at detection time so the dialog composable does
 * not need suspend access to the PSGC repository).
 */
data class PatientDuplicate(
    val patient: Patient,
    val barangayName: String,
)

/** Editable form state. */
data class PatientFormState(
    val lastname: String = "",
    val firstname: String = "",
    val middleName: String = "",
    val useCustomCodename: Boolean = false,
    val customCodename: String = "",
    val sex: Sex? = null,
    val birthdate: LocalDate? = null,
    val barangay: BarangayPickerState = BarangayPickerState(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val errors: Set<PatientFormError> = emptySet(),
    /** The picker must not offer a future date; this is today in [CLINICAL_ZONE]. */
    val today: LocalDate = LocalDate.now(CLINICAL_ZONE),
    /**
     * Non-null when a same-barangay identity match was found. The screen shows a stronger
     * "this exact patient already exists" dialog when this is set.
     */
    val pendingSameBarangayDuplicate: PatientDuplicate? = null,
    /**
     * Non-empty when one or more identity matches in a different barangay were found. The
     * screen shows a softer "possible duplicate" dialog when this is set.
     */
    val pendingDifferentBarangayDuplicates: List<PatientDuplicate> = emptyList(),
    /** True while the discard-changes confirmation dialog is open. */
    val showDiscardConfirm: Boolean = false,
    /** True when any field differs from its initial value. */
    val isDirty: Boolean = false,
)

sealed interface PatientFormEvent {
    data object Saved : PatientFormEvent
    data object Cancelled : PatientFormEvent
    /** Navigate to the sessions list of an existing patient (e.g. from a duplicate dialog). */
    data class OpenExisting(val patientId: String) : PatientFormEvent
}

/**
 * Backs the New / Edit Patient form.
 *
 * **Duplicate detection** runs before the save commits. An exact identity match in the same
 * barangay (same name, sex, birthdate) surfaces a stronger confirmation dialog; matches in a
 * different barangay surface a softer "possible duplicate" dialog. Both let the medtech
 * proceed, navigate to the existing patient, or cancel.
 *
 * **No delete.** Removing a patient is admin-side.
 *
 * Editing does not retroactively change anything already derived from a patient: session
 * labels keep the initials and barangay they were minted with (PB-10) and generated reports
 * keep the details they were generated with (PB-20). The screen says so where the edit is
 * confirmed, because otherwise the first medtech to correct a misspelled name and see the
 * old one still on a report will file it as a bug.
 *
 * `TooManyFunctions` is suppressed for the same reason [com.agarthavision.ui.sessions.SessionsViewModel]
 * suppresses it: one screen's callbacks belong to one ViewModel, and a form has one per
 * field by construction. Splitting them to satisfy a count would be inconsistent with every
 * other ViewModel here for no functional benefit.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class PatientFormViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    searchBarangaysUseCase: SearchBarangaysUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null for a blank form; set when opened on an existing patient. */
    private var patientId: String? = savedStateHandle["patientId"]

    // Shared with the session sheet rather than copied (PB-07b).
    private val barangayPicker = BarangayPickerDelegate(searchBarangaysUseCase)

    private val fields = MutableStateFlow(PatientFormState(isEditing = patientId != null))

    private val eventFlow = MutableSharedFlow<PatientFormEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PatientFormEvent> = eventFlow.asSharedFlow()

    /**
     * Only [createdAt] and [createdBy] survive an edit unchanged — they are provenance, not
     * details. Null until an existing patient loads.
     */
    private var loaded: Patient? = null

    /**
     * Shared [SharingStarted.Eagerly], not `WhileSubscribed`.
     *
     * The list screens use `WhileSubscribed` to cancel an expensive upstream Room query when
     * they leave composition. There is no upstream here — just two in-memory flows — so the
     * only thing `WhileSubscribed` would buy is a window in which `state.value` silently
     * reports the initial form while [loadExisting] has already filled `fields`. A form that
     * lies about what the medtech typed is worse than a combine that runs while nobody looks.
     */
    val state: StateFlow<PatientFormState> =
        combine(fields, barangayPicker.state) { form, barangay ->
            form.copy(
                barangay = barangay,
                isDirty = isDirty(form, barangay.selected),
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = PatientFormState(isEditing = patientId != null),
            )

    init {
        barangayPicker.start(viewModelScope)
        patientId?.let { loadExisting(it) }
    }

    /**
     * Initializes the form for a new patient or loads an existing patient by [id].
     * Allows the ViewModel to be reused when presented inside a bottom sheet.
     */
    fun loadPatient(id: String?) {
        if (id == null) {
            patientId = null
            loaded = null
            fields.value = PatientFormState(isEditing = false)
            barangayPicker.onCleared()
            return
        }
        if (id == patientId && loaded != null) return
        patientId = id
        loadExisting(id)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(id) ?: return@launch
            loaded = patient
            fields.update {
                it.copy(
                    lastname = patient.lastname,
                    firstname = patient.firstname,
                    middleName = patient.middleName.orEmpty(),
                    sex = patient.sex,
                    birthdate = patient.birthdate,
                    isEditing = true,
                )
            }
            barangayPicker.preselect(psgcRepository.getBarangay(patient.psgcBarangayCode))
        }
    }

    fun onLastnameChanged(value: String) = fields.update {
        it.copy(lastname = transformNameInput(it.lastname, value), errors = emptySet())
    }

    fun onFirstnameChanged(value: String) = fields.update {
        it.copy(firstname = transformNameInput(it.firstname, value), errors = emptySet())
    }

    fun onMiddleNameChanged(value: String) = fields.update {
        it.copy(middleName = transformNameInput(it.middleName, value), errors = emptySet())
    }

    fun onUseCustomCodenameToggled(enabled: Boolean) = fields.update {
        it.copy(useCustomCodename = enabled, errors = emptySet())
    }

    fun onCustomCodenameChanged(value: String) = fields.update {
        it.copy(customCodename = value.take(PATIENT_NAME_MAX_LENGTH).uppercase(), errors = emptySet())
    }

    fun onSexSelected(sex: Sex) = fields.update { it.copy(sex = sex, errors = emptySet()) }

    /**
     * Stores the date as picked. A future one is **rejected**, never clamped.
     *
     * This used to route through `sanitizeDateRange`, which coerces to today. That is right
     * for the Records date filter it was built for — a range ending tomorrow plainly means
     * "up to now" — and wrong here: a birthdate silently becoming today is a patient recorded
     * as newborn, on a clinical record, with nothing on screen saying so. PB-07c says
     * rejected, so [validate] rejects, and [PatientFormError.BIRTHDATE_IN_FUTURE] is
     * reachable again rather than dead.
     *
     * The picker's own `SelectableDates` bound is the first line of defence and stops this
     * arising through the UI at all; this guards a value restored after process death.
     */
    fun onBirthdateSelected(date: LocalDate?) {
        fields.update { it.copy(birthdate = date, errors = emptySet()) }
    }

    fun onBarangayQueryChanged(query: String) = barangayPicker.onQueryChanged(query)

    fun onBarangaySelected(code: String) = barangayPicker.onSelected(code)

    fun onBarangayCleared() = barangayPicker.onCleared()

    /** Shows the discard-confirm dialog if the form is dirty, otherwise cancels immediately. */
    fun onCancel() {
        if (isDirty()) {
            fields.update { it.copy(showDiscardConfirm = true) }
        } else {
            viewModelScope.launch { eventFlow.emit(PatientFormEvent.Cancelled) }
        }
    }

    fun onDiscardConfirmed() {
        val existing = loaded
        fields.value = if (existing != null) {
            PatientFormState(
                lastname = existing.lastname,
                firstname = existing.firstname,
                middleName = existing.middleName.orEmpty(),
                sex = existing.sex,
                birthdate = existing.birthdate,
                isEditing = true,
                showDiscardConfirm = false,
            )
        } else {
            PatientFormState(isEditing = false, showDiscardConfirm = false)
        }
        if (existing != null) {
            viewModelScope.launch {
                barangayPicker.preselect(psgcRepository.getBarangay(existing.psgcBarangayCode))
            }
        } else {
            barangayPicker.onCleared()
        }
        viewModelScope.launch { eventFlow.emit(PatientFormEvent.Cancelled) }
    }

    fun onDiscardDismissed() {
        fields.update { it.copy(showDiscardConfirm = false) }
    }

    /** Clears both pending-duplicate states without saving (dialog cancel). */
    fun onDismissDuplicate() {
        fields.update {
            it.copy(
                pendingSameBarangayDuplicate = null,
                pendingDifferentBarangayDuplicates = emptyList(),
            )
        }
    }

    /**
     * Navigates to the sessions list of an existing patient found during duplicate detection.
     * Clears the duplicate state first so no stale dialog is visible if the user navigates
     * back to this screen.
     */
    fun onGoToExistingPatient(id: String) {
        fields.update {
            it.copy(
                pendingSameBarangayDuplicate = null,
                pendingDifferentBarangayDuplicates = emptyList(),
            )
        }
        viewModelScope.launch { eventFlow.emit(PatientFormEvent.OpenExisting(id)) }
    }

    /**
     * Clears the duplicate dialogs and persists the patient as a new record. Called when
     * the medtech explicitly acknowledges the duplicate warning and proceeds anyway (both
     * the same-barangay "Add anyway" and the different-barangay "Add as new patient" paths).
     */
    fun onProceedAsNewPatient() {
        fields.update {
            it.copy(
                pendingSameBarangayDuplicate = null,
                pendingDifferentBarangayDuplicates = emptyList(),
                isSaving = true,
            )
        }
        viewModelScope.launch {
            val userId = observeLocalIdentityUseCase().map { it?.userId }.first()
            if (userId == null) {
                fields.update {
                    it.copy(
                        isSaving = false,
                        showErrors = true,
                        errors = setOf(PatientFormError.SAVE_FAILED),
                    )
                }
                return@launch
            }
            persist(userId)
        }
    }

    fun onSave() {
        if (fields.value.isSaving) return
        val snapshot = fields.value
        val barangay = barangayPicker.state.value.selected
        val errors = validate(snapshot, barangay)
        if (errors.isNotEmpty()) {
            fields.update { it.copy(showErrors = true, errors = errors) }
            return
        }

        fields.update { it.copy(isSaving = true, showErrors = false, errors = emptySet()) }
        viewModelScope.launch {
            val userId = observeLocalIdentityUseCase().map { it?.userId }.first()
            if (userId == null) {
                fields.update {
                    it.copy(
                        isSaving = false,
                        showErrors = true,
                        errors = setOf(PatientFormError.SAVE_FAILED),
                    )
                }
                return@launch
            }

            val isAnonymous = snapshot.useCustomCodename ||
                (snapshot.lastname.isBlank() && snapshot.firstname.isBlank())
            if (!isAnonymous) {
                // Middle-name coercion must exactly mirror the coercion used at persist time so
                // the query sees the same value the row will be stored with. A blank middle name
                // is absent, not an empty string, because displayName keys off null.
                val middleName = snapshot.middleName.trim().ifBlank { null }

                val duplicates = runCatching {
                    patientRepository.findDuplicates(
                        userId = userId,
                        lastname = snapshot.lastname.trim(),
                        firstname = snapshot.firstname.trim(),
                        middleName = middleName,
                        birthdate = requireNotNull(snapshot.birthdate),
                        sex = requireNotNull(snapshot.sex),
                        excludingId = patientId ?: "",
                    )
                }.getOrElse { throwable ->
                    Log.e(TAG, "Duplicate check failed — proceeding without it", throwable)
                    emptyList()
                }

                if (duplicates.isNotEmpty()) {
                    val inputBarangayCode = requireNotNull(barangay).code
                    val sameBarangay = duplicates.firstOrNull { it.psgcBarangayCode == inputBarangayCode }
                    val differentBarangay = duplicates.filter { it.psgcBarangayCode != inputBarangayCode }

                    if (sameBarangay != null) {
                        val barangayName = psgcRepository.getBarangay(sameBarangay.psgcBarangayCode)?.name
                            ?: sameBarangay.psgcBarangayCode
                        fields.update {
                            it.copy(
                                isSaving = false,
                                pendingSameBarangayDuplicate = PatientDuplicate(sameBarangay, barangayName),
                            )
                        }
                        return@launch
                    }

                    if (differentBarangay.isNotEmpty()) {
                        val entries = differentBarangay.map { patient ->
                            val name = psgcRepository.getBarangay(patient.psgcBarangayCode)?.name
                                ?: patient.psgcBarangayCode
                            PatientDuplicate(patient, name)
                        }
                        fields.update {
                            it.copy(
                                isSaving = false,
                                pendingDifferentBarangayDuplicates = entries,
                            )
                        }
                        return@launch
                    }
                }
            }

            persist(userId)
        }
    }

    /**
     * Writes the patient to the local database and emits [PatientFormEvent.Saved].
     *
     * Extracted from [onSave] so it can be reused by [onProceedAsNewPatient] (both the
     * no-duplicate path and the "proceed anyway after warning" path commit the same row).
     */
    private suspend fun persist(userId: String) {
        val snapshot = fields.value
        val barangay = barangayPicker.state.value.selected
        val now = Instant.now()
        val existing = loaded

        val isAnonymous = snapshot.useCustomCodename ||
            (snapshot.lastname.isBlank() && snapshot.firstname.isBlank())
        val (finalLastname, finalFirstname) = if (isAnonymous) {
            if (snapshot.useCustomCodename && snapshot.customCodename.isNotBlank()) {
                snapshot.customCodename.trim() to ""
            } else if (existing != null && existing.isCodename && !snapshot.useCustomCodename) {
                existing.lastname to ""
            } else {
                val sex = requireNotNull(snapshot.sex)
                val birthdate = requireNotNull(snapshot.birthdate)
                val prefix = CodenameGenerator.bucketPrefix(sex, birthdate)
                val existingCodenames = patientRepository.getExistingCodenamesByPrefix(userId, prefix)
                val codename = CodenameGenerator.generate(sex, birthdate, existingCodenames)
                codename to ""
            }
        } else {
            snapshot.lastname.trim() to snapshot.firstname.trim()
        }

        val patient = Patient(
            id = existing?.id ?: UUID.randomUUID().toString(),
            lastname = finalLastname,
            firstname = finalFirstname,
            // A blank middle name is absent, not an empty string: displayName keys off
            // null to decide whether an initial belongs in the name at all.
            middleName = snapshot.middleName.trim().ifBlank { null },
            sex = requireNotNull(snapshot.sex),
            birthdate = requireNotNull(snapshot.birthdate),
            psgcBarangayCode = requireNotNull(barangay).code,
            createdBy = existing?.createdBy ?: userId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        runCatching {
            if (existing == null) patientRepository.insert(patient)
            else patientRepository.update(patient)
        }.onSuccess {
            fields.value = PatientFormState(isEditing = false, showDiscardConfirm = false)
            loaded = null
            patientId = null
            barangayPicker.onCleared()
            eventFlow.emit(PatientFormEvent.Saved)
        }.onFailure { throwable ->
            // Logged, not swallowed: the screen only says "could not save", so without
            // this a failing write leaves nothing anywhere to diagnose it from.
            Log.e(TAG, "Saving patient ${patient.id} failed", throwable)
            fields.update {
                it.copy(isSaving = false, showErrors = true, errors = setOf(PatientFormError.SAVE_FAILED))
            }
        }
    }

    /**
     * Every rule in one place so the screen's submit gate and this cannot disagree.
     *
     * The future-birthdate rule is deliberately checked here as well as clamped on input.
     * The picker does not offer one and [onBirthdateSelected] clamps anything that does
     * arrive, so this is unreachable through the UI — it guards a value restored from
     * process death, which would otherwise store an age that counts backwards.
     */
    private fun validate(form: PatientFormState, barangay: PsgcBarangay?): Set<PatientFormError> =
        buildSet {
            val isAnonymous = form.useCustomCodename ||
                (form.lastname.isBlank() && form.firstname.isBlank())
            if (!isAnonymous) {
                if (form.lastname.isBlank()) add(PatientFormError.LASTNAME_REQUIRED)
                if (form.firstname.isBlank()) add(PatientFormError.FIRSTNAME_REQUIRED)
            }
            if (form.sex == null) add(PatientFormError.SEX_REQUIRED)
            when {
                form.birthdate == null -> add(PatientFormError.BIRTHDATE_REQUIRED)
                form.birthdate.isAfter(LocalDate.now(CLINICAL_ZONE)) ->
                    add(PatientFormError.BIRTHDATE_IN_FUTURE)
            }
            if (barangay == null) add(PatientFormError.BARANGAY_REQUIRED)
        }

    /**
     * True when any field differs from its starting value (empty defaults for a new patient,
     * the loaded patient's values for an edit). Used to decide whether [onCancel] must show
     * the discard-confirm dialog rather than leaving immediately.
     */
    private fun isDirty(
        form: PatientFormState = fields.value,
        barangay: PsgcBarangay? = barangayPicker.state.value.selected,
    ): Boolean {
        val l = loaded ?: return isNewPatientDirty(form, barangay)
        return isEditingDirty(form, barangay, l)
    }

    private fun isNewPatientDirty(form: PatientFormState, barangay: PsgcBarangay?): Boolean =
        form.useCustomCodename ||
            form.customCodename.isNotEmpty() ||
            form.lastname.isNotEmpty() ||
            form.firstname.isNotEmpty() ||
            form.middleName.isNotEmpty() ||
            form.sex != null ||
            form.birthdate != null ||
            barangay != null

    private fun isEditingDirty(
        form: PatientFormState,
        barangay: PsgcBarangay?,
        loaded: Patient,
    ): Boolean =
        form.lastname != loaded.lastname ||
            form.firstname != loaded.firstname ||
            form.middleName != (loaded.middleName ?: "") ||
            form.sex != loaded.sex ||
            form.birthdate != loaded.birthdate ||
            barangay?.code != loaded.psgcBarangayCode

    private companion object {
        const val TAG = "PatientForm"
    }
}
