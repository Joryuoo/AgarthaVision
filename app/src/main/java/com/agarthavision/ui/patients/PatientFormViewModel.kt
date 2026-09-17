package com.agarthavision.ui.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.util.sanitizeDateRange
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.BarangayPickerDelegate
import com.agarthavision.ui.components.BarangayPickerState
import com.agarthavision.ui.sessions.limitInput
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

/** Editable form state. */
data class PatientFormState(
    val lastname: String = "",
    val firstname: String = "",
    val middleName: String = "",
    val sex: Sex? = null,
    val birthdate: LocalDate? = null,
    val barangay: BarangayPickerState = BarangayPickerState(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val errors: Set<PatientFormError> = emptySet(),
    /** The picker must not offer a future date; this is today in [CLINICAL_ZONE]. */
    val today: LocalDate = LocalDate.now(CLINICAL_ZONE),
)

sealed interface PatientFormEvent {
    data object Saved : PatientFormEvent
    data object Cancelled : PatientFormEvent
}

/**
 * Backs the New / Edit Patient form.
 *
 * **No duplicate detection.** Duplicates are resolved admin-side by decision, and a
 * client-side match check here would train medtechs to dismiss a warning that is wrong more
 * often than it is right.
 *
 * **No delete.** Removing a patient is admin-side.
 *
 * Editing does not retroactively change anything already derived from a patient: session
 * labels keep the initials and barangay they were minted with (PB-10) and generated reports
 * keep the details they were generated with (PB-20). The screen says so where the edit is
 * confirmed, because otherwise the first medtech to correct a misspelled name and see the
 * old one still on a report will file it as a bug.
 */
@HiltViewModel
class PatientFormViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val psgcRepository: PsgcRepository,
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    searchBarangaysUseCase: SearchBarangaysUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null for a blank form; set when opened on an existing patient. */
    private val patientId: String? = savedStateHandle["patientId"]

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

    val state: StateFlow<PatientFormState> =
        combine(fields, barangayPicker.state) { form, barangay -> form.copy(barangay = barangay) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PatientFormState(isEditing = patientId != null),
            )

    init {
        barangayPicker.start(viewModelScope)
        patientId?.let { loadExisting(it) }
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
        it.copy(lastname = limitName(it.lastname, value), errors = emptySet())
    }

    fun onFirstnameChanged(value: String) = fields.update {
        it.copy(firstname = limitName(it.firstname, value), errors = emptySet())
    }

    fun onMiddleNameChanged(value: String) = fields.update {
        it.copy(middleName = limitName(it.middleName, value), errors = emptySet())
    }

    fun onSexSelected(sex: Sex) = fields.update { it.copy(sex = sex, errors = emptySet()) }

    /**
     * Clamped through the shared [sanitizeDateRange] rather than a second hand-written
     * future-date guard. It was built for the Records date filter, where it hit and fixed
     * exactly this bug; a patient cannot have been born tomorrow either.
     */
    fun onBirthdateSelected(date: LocalDate?) {
        val (clamped, _) = sanitizeDateRange(date, date, LocalDate.now(CLINICAL_ZONE))
        fields.update { it.copy(birthdate = clamped, errors = emptySet()) }
    }

    fun onBarangayQueryChanged(query: String) = barangayPicker.onQueryChanged(query)

    fun onBarangaySelected(code: String) = barangayPicker.onSelected(code)

    fun onBarangayCleared() = barangayPicker.onCleared()

    fun onCancel() {
        viewModelScope.launch { eventFlow.emit(PatientFormEvent.Cancelled) }
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
                    it.copy(isSaving = false, showErrors = true, errors = setOf(PatientFormError.SAVE_FAILED))
                }
                return@launch
            }
            val now = Instant.now()
            val existing = loaded
            val patient = Patient(
                id = existing?.id ?: UUID.randomUUID().toString(),
                lastname = snapshot.lastname.trim(),
                firstname = snapshot.firstname.trim(),
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
                fields.update { it.copy(isSaving = false) }
                eventFlow.emit(PatientFormEvent.Saved)
            }.onFailure {
                fields.update {
                    it.copy(isSaving = false, showErrors = true, errors = setOf(PatientFormError.SAVE_FAILED))
                }
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
            if (form.lastname.isBlank()) add(PatientFormError.LASTNAME_REQUIRED)
            if (form.firstname.isBlank()) add(PatientFormError.FIRSTNAME_REQUIRED)
            if (form.sex == null) add(PatientFormError.SEX_REQUIRED)
            when {
                form.birthdate == null -> add(PatientFormError.BIRTHDATE_REQUIRED)
                form.birthdate.isAfter(LocalDate.now(CLINICAL_ZONE)) ->
                    add(PatientFormError.BIRTHDATE_IN_FUTURE)
            }
            if (barangay == null) add(PatientFormError.BARANGAY_REQUIRED)
        }

    private fun limitName(previous: String, proposed: String): String =
        limitInput(previous, proposed, NAME_MAX_LENGTH)

    private companion object {
        /** Sized for one line of the list row's title. */
        const val NAME_MAX_LENGTH = 40
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
