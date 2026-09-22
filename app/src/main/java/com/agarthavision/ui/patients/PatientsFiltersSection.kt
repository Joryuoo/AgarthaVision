package com.agarthavision.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.patients.PatientSort
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.components.BarangayPickerState
import com.agarthavision.ui.components.SearchableDropdown
import com.agarthavision.ui.components.SearchableDropdownActions
import com.agarthavision.ui.components.SearchableDropdownConfig
import com.agarthavision.ui.components.SearchableDropdownState
import com.agarthavision.ui.components.toOption
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/**
 * Compact pill trigger on the main Patients screen: `[ Filters ]` when 0 filters active,
 * or highlighted `[ Filters · N ]` when filters are active.
 */
@Composable
internal fun PatientsFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val hasActive = activeCount > 0
    val bg = if (hasActive) colors.accent else colors.surface
    val border = if (hasActive) colors.accent else colors.borderStrong
    val content = if (hasActive) colors.onAccent else colors.textPrimary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.patients_filters_title),
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (hasActive) {
                stringResource(R.string.patients_filters_button_count, activeCount)
            } else {
                stringResource(R.string.patients_filters_title)
            },
            fontSize = 13.sp,
            fontWeight = if (hasActive) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}

/**
 * Dedicated bottom sheet housing Sort, Sex, Barangay, and Age filter controls.
 * Maintains local draft state and commits only upon tapping "Apply filters".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientsFilterSheet(
    state: PatientsState,
    onDismiss: () -> Unit,
    onApply: (
        sort: PatientSort,
        sex: Sex?,
        barangay: PsgcBarangay?,
        minAge: Int?,
        maxAge: Int?,
    ) -> Unit,
    onBarangayQueryChange: (String) -> Unit,
) {
    val colors = AgarthaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftSort by remember(state.sort) { mutableStateOf(state.sort) }
    var draftSex by remember(state.selectedSex) { mutableStateOf(state.selectedSex) }
    var draftBarangay by remember(state.barangayPickerState.selected) {
        mutableStateOf(state.barangayPickerState.selected)
    }
    var draftMinAge by remember(state.minAge) { mutableStateOf(state.minAge) }
    var draftMaxAge by remember(state.maxAge) { mutableStateOf(state.maxAge) }

    val hasFilters = draftSort != PatientSort.RECENT ||
        draftSex != null ||
        draftBarangay != null ||
        draftMinAge != null ||
        draftMaxAge != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp)),
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.patients_filters_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.patients_filters_clear_short),
                    color = if (hasFilters) colors.accent else colors.textTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = hasFilters) {
                            draftSort = PatientSort.RECENT
                            draftSex = null
                            draftBarangay = null
                            draftMinAge = null
                            draftMaxAge = null
                        }
                        .padding(4.dp),
                )
            }

            // 1. Sort by
            FilterSortSelector(
                sort = draftSort,
                onSortSelected = { draftSort = it },
            )

            // 2. Sex
            FilterSexSegmentedRow(
                selectedSex = draftSex,
                onSexSelected = { draftSex = it },
            )

            // 3. Barangay
            FilterBarangayControls(
                selected = draftBarangay,
                state = state.barangayPickerState,
                onQueryChange = onBarangayQueryChange,
                onSelect = { draftBarangay = it },
                onClear = { draftBarangay = null },
            )

            // 4. Age
            FilterAgeControls(
                minAge = draftMinAge,
                maxAge = draftMaxAge,
                onMinAgeChanged = { draftMinAge = it },
                onMaxAgeChanged = { draftMaxAge = it },
            )

            // 5. Apply filters CTA
            Spacer(modifier = Modifier.height(Spacing.xs))
            Button(
                onClick = {
                    onApply(draftSort, draftSex, draftBarangay, draftMinAge, draftMaxAge)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(
                    text = stringResource(R.string.patients_filters_apply),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FilterSortSelector(
    sort: PatientSort,
    onSortSelected: (PatientSort) -> Unit,
) {
    val colors = AgarthaTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val label = when (sort) {
        PatientSort.RECENT -> stringResource(R.string.patients_sort_recent)
        PatientSort.LAST_NAME -> stringResource(R.string.patients_sort_lastname)
        PatientSort.FIRST_NAME -> stringResource(R.string.patients_sort_firstname)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_sort_label),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Spacing.md))
                    .background(colors.surface)
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(Spacing.md))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.patients_sort_recent)) },
                    onClick = {
                        onSortSelected(PatientSort.RECENT)
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.patients_sort_lastname)) },
                    onClick = {
                        onSortSelected(PatientSort.LAST_NAME)
                        expanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.patients_sort_firstname)) },
                    onClick = {
                        onSortSelected(PatientSort.FIRST_NAME)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterSexSegmentedRow(
    selectedSex: Sex?,
    onSexSelected: (Sex?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_filter_sex),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val options = listOf(
                null to stringResource(R.string.patients_filter_sex_all),
                Sex.MALE to stringResource(R.string.patients_sex_male),
                Sex.FEMALE to stringResource(R.string.patients_sex_female),
            )
            for ((sex, label) in options) {
                val isSelected = selectedSex == sex
                val bg = if (isSelected) colors.accent else colors.surface
                val border = if (isSelected) colors.accent else colors.borderStrong
                val text = if (isSelected) colors.onAccent else colors.textPrimary

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Spacing.md))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(Spacing.md))
                        .clickable { onSexSelected(sex) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = text,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = text,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBarangayControls(
    selected: PsgcBarangay?,
    state: BarangayPickerState,
    onQueryChange: (String) -> Unit,
    onSelect: (PsgcBarangay) -> Unit,
    onClear: () -> Unit,
) {
    SearchableDropdown(
        state = SearchableDropdownState(
            selected = selected?.toOption(),
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
            onSelect = { option ->
                val match = state.results.firstOrNull { it.code == option.key }
                if (match != null) onSelect(match)
            },
            onClear = onClear,
        ),
    )
}

@Composable
private fun FilterAgeControls(
    minAge: Int?,
    maxAge: Int?,
    onMinAgeChanged: (Int?) -> Unit,
    onMaxAgeChanged: (Int?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.patients_filter_age_chip),
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
