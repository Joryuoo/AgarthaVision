package com.agarthavision.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

internal data class PatientsFiltersActions(
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
internal fun PatientsFiltersSection(
    state: PatientsState,
    actions: PatientsFiltersActions,
) {
    val colors = AgarthaTheme.colors
    var filtersExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }
    var ageExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.barangayPickerState.selected) {
        if (state.barangayPickerState.selected != null) {
            barangayExpanded = true
        }
    }
    LaunchedEffect(state.minAge, state.maxAge) {
        if (state.minAge != null || state.maxAge != null) {
            ageExpanded = true
        }
    }

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
                imageVector = if (filtersExpanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
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
                    .clickable {
                        barangayExpanded = false
                        ageExpanded = false
                        actions.onClearFilters()
                    },
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SortDropdownChip(
                    sort = state.sort,
                    onSortSelected = actions.onSortSelected,
                )
                SexDropdownChip(
                    selected = state.selectedSex,
                    onSelected = actions.onSexSelected,
                )
                BarangayToggleChip(
                    selectedBarangay = state.barangayPickerState.selected,
                    expanded = barangayExpanded,
                    onToggle = {
                        if (barangayExpanded) {
                            barangayExpanded = false
                            actions.onBarangayCleared()
                        } else {
                            barangayExpanded = true
                        }
                    },
                )
                AgeToggleChip(
                    minAge = state.minAge,
                    maxAge = state.maxAge,
                    expanded = ageExpanded,
                    onToggle = {
                        if (ageExpanded) {
                            ageExpanded = false
                            actions.onMinAgeChanged(null)
                            actions.onMaxAgeChanged(null)
                        } else {
                            ageExpanded = true
                        }
                    },
                )
            }

            if (barangayExpanded) {
                BarangayFilterControls(
                    state = state.barangayPickerState,
                    onQueryChange = actions.onBarangayQueryChange,
                    onSelect = actions.onBarangaySelected,
                    onClear = actions.onBarangayCleared,
                )
            }

            if (ageExpanded) {
                AgeRangeFilterControls(
                    minAge = state.minAge,
                    maxAge = state.maxAge,
                    onMinAgeChanged = actions.onMinAgeChanged,
                    onMaxAgeChanged = actions.onMaxAgeChanged,
                )
            }
        }
    }
}

@Composable
private fun SortDropdownChip(
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
    val isSelected = expanded || sort != PatientSort.RECENT
    val pillColors = FilterPillColors(
        activeBg = colors.accentTint,
        activeBorder = colors.accent,
        activeText = colors.accent,
        activeIcon = colors.accent,
    )

    Box {
        PatientFilterChip(
            label = label,
            selected = isSelected,
            onClick = { expanded = !expanded },
            activeColors = pillColors,
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) pillColors.activeIcon else colors.textSecondary,
                )
            },
        )
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

@Composable
private fun SexDropdownChip(
    selected: Sex?,
    onSelected: (Sex?) -> Unit,
) {
    val colors = AgarthaTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val sexLabel = stringResource(R.string.patients_filter_sex)
    val label = when (selected) {
        null -> "$sexLabel: ${stringResource(R.string.patients_filter_sex_all)}"
        Sex.MALE -> "$sexLabel: ${stringResource(R.string.patients_sex_male)}"
        Sex.FEMALE -> "$sexLabel: ${stringResource(R.string.patients_sex_female)}"
    }
    val isSelected = expanded || selected != null
    val pillColors = FilterPillColors(
        activeBg = colors.goldTint,
        activeBorder = colors.gold,
        activeText = colors.goldText,
        activeIcon = colors.goldText,
    )

    Box {
        PatientFilterChip(
            label = label,
            selected = isSelected,
            onClick = { expanded = !expanded },
            activeColors = pillColors,
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) pillColors.activeIcon else colors.textSecondary,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_filter_sex_all)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sex_male)) },
                onClick = {
                    onSelected(Sex.MALE)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sex_female)) },
                onClick = {
                    onSelected(Sex.FEMALE)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun BarangayToggleChip(
    selectedBarangay: PsgcBarangay?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val hasSelection = selectedBarangay != null
    val label = if (hasSelection) {
        "${stringResource(R.string.patient_form_barangay)}: ${selectedBarangay?.name.orEmpty()}"
    } else {
        stringResource(R.string.patient_form_barangay)
    }
    val isSelected = expanded || hasSelection
    val pillColors = FilterPillColors(
        activeBg = colors.surfaceMuted,
        activeBorder = colors.textPrimary,
        activeText = colors.textPrimary,
        activeIcon = colors.textPrimary,
    )

    PatientFilterChip(
        label = label,
        selected = isSelected,
        onClick = onToggle,
        activeColors = pillColors,
        trailingIcon = {
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) pillColors.activeIcon else colors.textSecondary,
            )
        },
    )
}

@Composable
private fun AgeToggleChip(
    minAge: Int?,
    maxAge: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val hasFilter = minAge != null || maxAge != null
    val ageChipPrefix = stringResource(R.string.patients_filter_age_chip)
    val label = when {
        minAge != null && maxAge != null -> "$ageChipPrefix: $minAge–$maxAge"
        minAge != null -> "$ageChipPrefix: \u2265$minAge"
        maxAge != null -> "$ageChipPrefix: \u2264$maxAge"
        else -> ageChipPrefix
    }
    val isSelected = expanded || hasFilter
    val pillColors = FilterPillColors(
        activeBg = colors.surfaceMuted,
        activeBorder = colors.borderStrong,
        activeText = colors.textPrimary,
        activeIcon = colors.textPrimary,
    )

    PatientFilterChip(
        label = label,
        selected = isSelected,
        onClick = onToggle,
        activeColors = pillColors,
        trailingIcon = {
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) pillColors.activeIcon else colors.textSecondary,
            )
        },
    )
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

private data class FilterPillColors(
    val activeBg: Color,
    val activeBorder: Color,
    val activeText: Color,
    val activeIcon: Color = activeText,
)

@Composable
private fun PatientFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColors: FilterPillColors? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = AgarthaTheme.colors
    val bg = if (selected && activeColors != null) {
        activeColors.activeBg
    } else if (selected) {
        colors.textPrimary
    } else {
        colors.surface
    }
    val border = if (selected && activeColors != null) {
        activeColors.activeBorder
    } else if (selected) {
        colors.textPrimary
    } else {
        colors.borderStrong
    }
    val text = if (selected && activeColors != null) {
        activeColors.activeText
    } else if (selected) {
        colors.background
    } else {
        colors.textSecondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = text,
        )
        trailingIcon?.invoke()
    }
}
