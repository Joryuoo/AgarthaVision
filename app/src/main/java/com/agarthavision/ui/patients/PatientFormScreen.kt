package com.agarthavision.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.SearchableDropdown
import com.agarthavision.ui.components.SearchableDropdownActions
import com.agarthavision.ui.components.SearchableDropdownConfig
import com.agarthavision.ui.components.SearchableDropdownState
import com.agarthavision.ui.components.ScreenHeader
import com.agarthavision.ui.components.SheetInput
import com.agarthavision.ui.components.SheetInputConfig
import com.agarthavision.ui.components.toOption
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The New / Edit Patient form.
 *
 * Works entirely offline: the barangay picker searches the PSGC dataset bundled in the APK,
 * and the save is a local write that syncs when connectivity returns.
 *
 * **No duplicate-detection warning** — duplicates are an admin-side decision. **No delete**
 * — also admin-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(
    onDone: () -> Unit,
    viewModel: PatientFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AgarthaTheme.colors

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PatientFormEvent.Saved, PatientFormEvent.Cancelled -> onDone()
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(
                title = stringResource(
                    if (state.isEditing) R.string.patient_form_edit_title
                    else R.string.patient_form_new_title,
                ),
                purpose = stringResource(R.string.patients_subtitle_purpose),
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SheetInput(
                    value = state.lastname,
                    onValueChange = viewModel::onLastnameChanged,
                    config = SheetInputConfig(
                        label = stringResource(R.string.patient_form_lastname),
                        placeholder = stringResource(R.string.patient_form_lastname_placeholder),
                        isError = state.showErrors &&
                            PatientFormError.LASTNAME_REQUIRED in state.errors,
                    ),
                )
                SheetInput(
                    value = state.firstname,
                    onValueChange = viewModel::onFirstnameChanged,
                    config = SheetInputConfig(
                        label = stringResource(R.string.patient_form_firstname),
                        placeholder = stringResource(R.string.patient_form_firstname_placeholder),
                        isError = state.showErrors &&
                            PatientFormError.FIRSTNAME_REQUIRED in state.errors,
                    ),
                )
                // Optional on purpose: many patients do not supply one, and a required
                // field here would only collect junk.
                SheetInput(
                    value = state.middleName,
                    onValueChange = viewModel::onMiddleNameChanged,
                    config = SheetInputConfig(
                        label = stringResource(R.string.patient_form_middle_name),
                        placeholder = stringResource(R.string.patient_form_middle_name_placeholder),
                        isError = false,
                        isRequired = false,
                    ),
                )

                SexSelector(
                    selected = state.sex,
                    onSelected = viewModel::onSexSelected,
                    isError = state.showErrors && PatientFormError.SEX_REQUIRED in state.errors,
                )

                BirthdateField(
                    birthdate = state.birthdate,
                    isError = state.showErrors && state.errors.any { it in BIRTHDATE_ERRORS },
                    onClick = { showDatePicker = true },
                )

                SearchableDropdown(
                    state = SearchableDropdownState(
                        selected = state.barangay.selected?.toOption(),
                        query = state.barangay.query,
                        options = state.barangay.results.map { it.toOption() },
                    ),
                    config = SearchableDropdownConfig(
                        label = stringResource(R.string.patient_form_barangay),
                        placeholder = stringResource(R.string.patient_form_barangay_placeholder),
                        hint = stringResource(
                            R.string.patient_form_barangay_hint,
                            SearchBarangaysUseCase.MIN_QUERY_LENGTH,
                        ),
                        noMatches = stringResource(R.string.patient_form_barangay_no_matches),
                        clearLabel = stringResource(R.string.patient_form_barangay_clear),
                        minQueryLength = SearchBarangaysUseCase.MIN_QUERY_LENGTH,
                        badge = stringResource(R.string.patient_form_required_badge),
                        isError = state.showErrors &&
                            PatientFormError.BARANGAY_REQUIRED in state.errors,
                    ),
                    actions = SearchableDropdownActions(
                        onQueryChange = viewModel::onBarangayQueryChanged,
                        onSelect = { viewModel.onBarangaySelected(it.key) },
                        onClear = viewModel::onBarangayCleared,
                    ),
                )

                if (state.isEditing) {
                    EditNote()
                }

                state.errors.firstOrNull()?.takeIf { state.showErrors }?.let { error ->
                    Text(
                        text = stringResource(error.messageRes()),
                        color = colors.danger,
                        fontSize = 12.sp,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(onClick = viewModel::onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.patient_form_cancel), color = colors.textSecondary)
                    }
                    Button(
                        onClick = viewModel::onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.patient_form_save),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        BirthdatePickerDialog(
            initial = state.birthdate,
            today = state.today,
            onDismiss = { showDatePicker = false },
            onSelected = {
                viewModel.onBirthdateSelected(it)
                showDatePicker = false
            },
        )
    }
}

/**
 * Dropdown-shaped rather than free text, and limited to two values: that is how DOH and WHO
 * stratify STH surveillance data, and a free-text sex would not aggregate.
 */
@Composable
private fun SexSelector(
    selected: Sex?,
    onSelected: (Sex) -> Unit,
    isError: Boolean,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patient_form_sex),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Sex.entries.forEach { sex ->
                val active = selected == sex
                Text(
                    text = stringResource(
                        if (sex == Sex.MALE) R.string.patients_sex_male
                        else R.string.patients_sex_female,
                    ),
                    color = if (active) colors.onAccent else colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) colors.accent else colors.surface)
                        .border(
                            1.dp,
                            if (isError && selected == null) colors.danger else colors.border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelected(sex) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun BirthdateField(
    birthdate: LocalDate?,
    isError: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patient_form_birthdate),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = birthdate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                ?: stringResource(R.string.patient_form_birthdate_placeholder),
            color = if (birthdate == null) colors.textTertiary else colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(
                    1.dp,
                    if (isError) colors.danger else colors.border,
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/**
 * The date picker cannot offer a future date.
 *
 * [today] is resolved in [CLINICAL_ZONE], the frame birthdates are stored in, so the bound
 * does not move by a day depending on the device's timezone. Material's picker works in UTC
 * epoch millis, which is why the conversions here are explicitly UTC rather than local —
 * they are the picker's frame, not the patient's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdatePickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
) {
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= todayUtcMillis

                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )

    // Material3 defaults dialogs to shapes.extraLarge, which is this app's 999.dp pill token,
    // so without this the picker renders as an ellipse. Every other dialog in the app already
    // passes it -- see the note on DialogShape in Theme.kt, and SingleDatePickerDialog in
    // DateRangeFilterBar, which this now matches.
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        confirmButton = {
            TextButton(
                // Disabled until a day is picked, matching the Records picker. Previously the
                // button was always live and simply did nothing when nothing was selected.
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.patient_form_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.patient_form_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun EditNote() {
    val colors = AgarthaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.patient_form_edit_note),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
    Spacer(modifier = Modifier.height(Spacing.xs))
}

/** Both birthdate failures light the same field. */
private val BIRTHDATE_ERRORS = setOf(
    PatientFormError.BIRTHDATE_REQUIRED,
    PatientFormError.BIRTHDATE_IN_FUTURE,
)

private fun PatientFormError.messageRes(): Int = when (this) {
    PatientFormError.LASTNAME_REQUIRED -> R.string.patient_form_error_lastname
    PatientFormError.FIRSTNAME_REQUIRED -> R.string.patient_form_error_firstname
    PatientFormError.SEX_REQUIRED -> R.string.patient_form_error_sex
    PatientFormError.BIRTHDATE_REQUIRED -> R.string.patient_form_error_birthdate
    PatientFormError.BIRTHDATE_IN_FUTURE -> R.string.patient_form_error_birthdate_future
    PatientFormError.BARANGAY_REQUIRED -> R.string.patient_form_error_barangay
    PatientFormError.SAVE_FAILED -> R.string.patient_form_error_save
}
