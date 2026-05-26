@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.agarthavision.ui.verify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.komoui.themes.styles

@Composable
fun SpeciesDropdown(
    selected: EggSpecies?,
    otherText: String,
    onSpeciesSelected: (EggSpecies) -> Unit,
    onOtherTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.styles.ring,
        unfocusedBorderColor = MaterialTheme.styles.border,
        focusedLabelColor = MaterialTheme.styles.foreground,
        unfocusedLabelColor = MaterialTheme.styles.mutedForeground,
    )

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.let { speciesLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.verify_q3)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                EggSpecies.entries.forEach { species ->
                    DropdownMenuItem(
                        text = { Text(speciesLabel(species)) },
                        onClick = {
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
    }
}

@Composable
private fun speciesLabel(species: EggSpecies): String = when (species) {
    EggSpecies.ASCARIS -> stringResource(R.string.verify_species_ascaris)
    EggSpecies.TRICHURIS -> stringResource(R.string.verify_species_trichuris)
    EggSpecies.HOOKWORM -> stringResource(R.string.verify_species_hookworm)
    EggSpecies.OTHER -> stringResource(R.string.verify_species_other)
}
