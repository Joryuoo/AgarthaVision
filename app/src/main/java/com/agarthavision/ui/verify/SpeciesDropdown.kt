@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.agarthavision.ui.verify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.ui.theme.AgarthaTheme

@Composable
fun SpeciesDropdown(
    selected: EggSpecies?,
    otherText: String,
    onSpeciesSelected: (EggSpecies) -> Unit,
    onOtherTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Species already recorded on this device that match what is being typed into "Other".
     *
     * Empty by default, so a caller with no suggestions to offer needs no change. The list is
     * only ever a convenience: typing a name the index has never seen has to keep working,
     * because free text is the sole path by which a species outside [EggSpecies] enters the
     * corpus at all.
     */
    suggestions: List<String> = emptyList(),
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
    // The committed selection's own name is not a filter: reopening the menu after a pick
    // must still show every species, not just the one already chosen.
    val committedName = selected?.displayName ?: ""
    val filteredSpecies = EggSpecies.entries.filter { species ->
        query.isBlank() || query == committedName || species.displayName.contains(query, ignoreCase = true)
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
                label = { Text(stringResource(R.string.verify_species_picker_label)) },
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
            OtherSpeciesSuggestions(
                // A suggestion identical to what is already typed offers nothing, and reads as
                // the field failing to notice it has been answered.
                suggestions = suggestions.filterNot { it.equals(otherText.trim(), ignoreCase = true) },
                onPick = onOtherTextChanged,
            )
        }
    }
}

/**
 * The species this device already holds, offered under the free-text field.
 *
 * **Rendered inline rather than in a popup menu**, for the reason `SearchableDropdown` records:
 * a popup inside a `ModalBottomSheet` competes with the sheet for the IME, and this is that
 * exact context. The species picker above still uses `ExposedDropdownMenu`, which predates that
 * finding - worth watching on a device, but not something to change from here.
 *
 * Tapping a name only fills the field. Nothing is committed, and the medtech can keep typing
 * over it, because the index is a record of what has been entered before and not a vocabulary
 * the corpus is limited to.
 */
@Composable
private fun OtherSpeciesSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val colors = AgarthaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.OTHER_SPECIES_SUGGESTIONS)
            .padding(top = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.verify_other_suggestions_label),
            color = colors.textTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        // Capped well below the use case's own limit. A prefix match over a device-local index
        // rarely returns more than a few, and a long list inside a bottom sheet pushes the field
        // being typed into off the screen - which costs more than the matches it would show.
        suggestions.take(DISPLAY_LIMIT).forEach { name ->
            Text(
                text = name,
                color = colors.accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VerifyTestTags.otherSpeciesSuggestion(name))
                    .clickable { onPick(name) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Matches shown at once. See [OtherSpeciesSuggestions]. */
private const val DISPLAY_LIMIT = 5

