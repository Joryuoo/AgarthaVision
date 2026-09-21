package com.agarthavision.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.patients.PatientListItem
import com.agarthavision.domain.usecase.patients.PatientSort
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.BarangayPickerState
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.ScreenHeader
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.components.SearchableDropdown
import com.agarthavision.ui.components.SearchableDropdownActions
import com.agarthavision.ui.components.SearchableDropdownConfig
import com.agarthavision.ui.components.SearchableDropdownState
import com.agarthavision.ui.components.toOption
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import java.time.Instant

/**
 * Bottom clearance so the last row is not stranded under the floating New Patient button.
 *
 * A literal rather than a [Spacing] token: the compact scale this file uses tops out at 32dp
 * and this has to clear a ~48dp button plus its own 16dp inset. It is a layout clearance for
 * one specific control, not a spacing step.
 */
private val FloatingActionClearance = 64.dp

/**
 * The patient list: everyone the signed-in medtech is linked to.
 *
 * Every read behind this screen is local, so it renders and searches with the radio off.
 *
 * **There is no delete affordance, and none should be added.** Removing a patient is an
 * admin-side action.
 */
@Composable
fun PatientsScreen(
    onPatientSelected: (String) -> Unit,
    onEditPatient: (String) -> Unit,
    onCreatePatient: () -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AgarthaTheme.colors

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PatientsEvent.OpenPatient -> onPatientSelected(event.patientId)
                is PatientsEvent.EditPatient -> onEditPatient(event.patientId)
                PatientsEvent.CreatePatient -> onCreatePatient()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.TopCenter),
        ) {
            ScreenHeader(
                title = stringResource(R.string.patients_title),
                purpose = stringResource(R.string.patients_subtitle_purpose),
                status = stringResource(R.string.patients_status_counts, state.total),
            )

            SearchInput(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = stringResource(R.string.patients_search_placeholder),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            PatientsFiltersSection(
                state = state,
                actions = PatientsFiltersActions(
                    onSortSelected = viewModel::onSortSelected,
                    onSexSelected = viewModel::onSexSelected,
                    onBarangayQueryChange = viewModel::onBarangayQueryChanged,
                    onBarangaySelected = viewModel::onBarangaySelected,
                    onBarangayCleared = viewModel::onBarangayCleared,
                    onMinAgeChanged = viewModel::onMinAgeChanged,
                    onMaxAgeChanged = viewModel::onMaxAgeChanged,
                    onClearFilters = viewModel::onClearFilters,
                ),
            )

            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.onLoadMore()
            }

            if (!state.isLoading && state.patients.isEmpty()) {
                val narrowed = state.isNarrowed
                val emptyAction: (@Composable () -> Unit)? = if (narrowed) {
                    null
                } else {
                    { NewPatientButton(onClick = viewModel::onCreatePatient) }
                }
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = stringResource(
                        if (narrowed) R.string.patients_empty_filtered_title
                        else R.string.patients_empty_title,
                    ),
                    body = stringResource(
                        if (narrowed) R.string.patients_empty_filtered_body
                        else R.string.patients_empty_body,
                    ),
                    modifier = Modifier.padding(top = Spacing.xxl),
                    action = emptyAction,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = FloatingActionClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(state.patients, key = { it.patient.id }) { item ->
                        PatientRow(
                            item = item,
                            now = state.now,
                            onClick = { viewModel.onPatientSelected(item.patient.id) },
                            onEdit = { viewModel.onEditPatient(item.patient.id) },
                        )
                    }
                }
            }
        }

        if (state.patients.isNotEmpty()) {
            NewPatientButton(
                onClick = viewModel::onCreatePatient,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.lg),
            )
        }
    }
}

@Composable
private fun NewPatientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
    ) {
        Text(text = stringResource(R.string.patients_new), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * One patient.
 *
 * Age is computed from the birthdate against [now] rather than stored, which is the whole
 * reason the birthdate is the column. [now] is fixed for the screen so a list open across
 * midnight cannot show two ages for one birthday.
 */
@Composable
private fun PatientRow(
    item: PatientListItem,
    now: Instant,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val patient = item.patient

    val sexLabel = when (patient.sex) {
        Sex.MALE -> stringResource(R.string.patients_sex_male)
        Sex.FEMALE -> stringResource(R.string.patients_sex_female)
        null -> stringResource(R.string.patients_sex_unknown)
    }
    val meta = stringResource(
        R.string.patients_row_meta,
        sexLabel,
        stringResource(R.string.patients_age_years, patient.ageYears(now)),
        // A code that resolves to nothing renders as itself: a PSGC vintage change can
        // retire one, and a blank where a barangay should be reads as missing data.
        item.barangayName ?: patient.psgcBarangayCode,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = patient.displayName,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The row itself opens this patient's smears, which is the common action. Editing
        // their details is rarer and deliberate, so it gets its own target rather than
        // displacing the tap. `material-icons-extended` per C11: this is an in-screen
        // affordance, not house identity.
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.patients_edit_desc, patient.displayName),
                tint = colors.textSecondary,
            )
        }
    }
}

private data class PatientsFiltersActions(
    val onSortSelected: (PatientSort) -> Unit,
    val onSexSelected: (Sex?) -> Unit,
    val onBarangayQueryChange: (String) -> Unit,
    val onBarangaySelected: (String) -> Unit,
    val onBarangayCleared: () -> Unit,
    val onMinAgeChanged: (Int?) -> Unit,
    val onMaxAgeChanged: (Int?) -> Unit,
    val onClearFilters: () -> Unit,
)

@Composable
private fun PatientsFiltersSection(
    state: PatientsState,
    actions: PatientsFiltersActions,
) {
    val colors = AgarthaTheme.colors
    var filtersExpanded by remember { mutableStateOf(false) }
    val hasActiveFilters = state.selectedSex != null ||
        state.barangayPickerState.selected != null ||
        state.minAge != null ||
        state.maxAge != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { filtersExpanded = !filtersExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.patients_filters_title),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = if (filtersExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(R.string.patients_filters_title),
                tint = colors.textSecondary,
            )
        }
        if (hasActiveFilters) {
            Text(
                text = stringResource(R.string.patients_filters_clear),
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = Spacing.md)
                    .clickable { actions.onClearFilters() },
            )
        }
    }

    if (filtersExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SortControls(sort = state.sort, onSortSelected = actions.onSortSelected)
            SexFilterControls(selected = state.selectedSex, onSelected = actions.onSexSelected)
            BarangayFilterControls(
                state = state.barangayPickerState,
                onQueryChange = actions.onBarangayQueryChange,
                onSelect = actions.onBarangaySelected,
                onClear = actions.onBarangayCleared,
            )
            AgeRangeFilterControls(
                minAge = state.minAge,
                maxAge = state.maxAge,
                onMinAgeChanged = actions.onMinAgeChanged,
                onMaxAgeChanged = actions.onMaxAgeChanged,
            )
        }
    }
}

@Composable
private fun SortControls(
    sort: PatientSort,
    onSortSelected: (PatientSort) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_sort_label),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PatientFilterChip(
                label = stringResource(R.string.patients_sort_recent),
                selected = sort == PatientSort.RECENT,
                onClick = { onSortSelected(PatientSort.RECENT) },
            )
            PatientFilterChip(
                label = stringResource(R.string.patients_sort_lastname),
                selected = sort == PatientSort.LAST_NAME,
                onClick = { onSortSelected(PatientSort.LAST_NAME) },
            )
            PatientFilterChip(
                label = stringResource(R.string.patients_sort_firstname),
                selected = sort == PatientSort.FIRST_NAME,
                onClick = { onSortSelected(PatientSort.FIRST_NAME) },
            )
        }
    }
}

@Composable
private fun SexFilterControls(
    selected: Sex?,
    onSelected: (Sex?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_filter_sex),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PatientFilterChip(
                label = stringResource(R.string.patients_filter_sex_all),
                selected = selected == null,
                onClick = { onSelected(null) },
            )
            PatientFilterChip(
                label = stringResource(R.string.patients_sex_male),
                selected = selected == Sex.MALE,
                onClick = { onSelected(Sex.MALE) },
            )
            PatientFilterChip(
                label = stringResource(R.string.patients_sex_female),
                selected = selected == Sex.FEMALE,
                onClick = { onSelected(Sex.FEMALE) },
            )
        }
    }
}

@Composable
private fun BarangayFilterControls(
    state: BarangayPickerState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    SearchableDropdown(
        state = SearchableDropdownState(
            selected = state.selected?.toOption(),
            query = state.query,
            options = state.results.map { it.toOption() },
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
            badge = null,
            isError = false,
        ),
        actions = SearchableDropdownActions(
            onQueryChange = onQueryChange,
            onSelect = { onSelect(it.key) },
            onClear = onClear,
        ),
    )
}

@Composable
private fun AgeRangeFilterControls(
    minAge: Int?,
    maxAge: Int?,
    onMinAgeChanged: (Int?) -> Unit,
    onMaxAgeChanged: (Int?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_filter_age),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgeInputField(
                value = minAge,
                placeholder = stringResource(R.string.patients_filter_age_min_placeholder),
                onValueChange = onMinAgeChanged,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "–",
                color = colors.textSecondary,
                fontSize = 14.sp,
            )
            AgeInputField(
                value = maxAge,
                placeholder = stringResource(R.string.patients_filter_age_max_placeholder),
                onValueChange = onMaxAgeChanged,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AgeInputField(
    value: Int?,
    placeholder: String,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }

    BasicTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(3)
            text = digits
            onValueChange(digits.toIntOrNull())
        },
        modifier = modifier,
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = colors.textPrimary,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(Spacing.md))
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(Spacing.md))
                    .padding(horizontal = Spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 14.sp,
                            color = colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun PatientFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val bg = if (selected) colors.textPrimary else colors.surface
    val border = if (selected) colors.textPrimary else colors.borderStrong
    val text = if (selected) colors.background else colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = text,
        )
    }
}
