@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.agarthavision.ui.verify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.ui.theme.AgarthaTheme

// Species + stage are two related pickers sharing one composable so the stage field can be
// gated on the species answer without a second file; the extra params are the stage
// counterparts of the existing species params, not independently meaningful knobs.
@Suppress("LongParameterList")
@Composable
fun SpeciesDropdown(
    selected: EggSpecies?,
    otherText: String,
    onSpeciesSelected: (EggSpecies) -> Unit,
    onOtherTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedStage: EggStage? = null,
    onStageSelected: (EggStage) -> Unit = {},
    stageModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(selected?.displayName ?: "") }
    LaunchedEffect(selected) {
        query = selected?.displayName ?: ""
    }
    val colors = AgarthaTheme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.borderStrong,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.textSecondary,
        unfocusedLabelColor = colors.textSecondary,
    )
    val filteredSpecies = EggSpecies.entries.filter { species ->
        query.isBlank() || species.displayName.contains(query, ignoreCase = true)
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    expanded = true
                },
                readOnly = false,
                label = { Text(stringResource(R.string.verify_q3)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .onFocusChanged { focusState ->
                        // Losing focus without tapping a menu item (e.g. tabbing away, or the
                        // outside-click that also triggers onDismissRequest) must not leave a
                        // typed-but-uncommitted query on screen - it has to fall back to the
                        // last committed selection.
                        if (!focusState.isFocused) {
                            expanded = false
                            query = selected?.displayName ?: ""
                        }
                    },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    query = selected?.displayName ?: ""
                },
            ) {
                filteredSpecies.forEach { species ->
                    DropdownMenuItem(
                        text = { Text(species.displayName) },
                        onClick = {
                            query = species.displayName
                            onSpeciesSelected(species)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (selected == EggSpecies.OTHER) {
            OutlinedTextField(
                value = otherText,
                onValueChange = onOtherTextChanged,
                label = { Text(stringResource(R.string.verify_other_label)) },
                placeholder = { Text(stringResource(R.string.verify_other_hint)) },
                colors = fieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val stageOptions = selected?.let { EggStage.validFor(it) }.orEmpty()
        if (selected != null && stageOptions.isNotEmpty()) {
            var stageExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = stageExpanded,
                onExpandedChange = { stageExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedStage?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.verify_q3b)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = stageExpanded)
                    },
                    colors = fieldColors,
                    modifier = stageModifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = stageExpanded,
                    onDismissRequest = { stageExpanded = false },
                ) {
                    stageOptions.forEach { stage ->
                        DropdownMenuItem(
                            text = { Text(stage.displayName) },
                            onClick = {
                                onStageSelected(stage)
                                stageExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

