@file:Suppress("FunctionNaming", "LongParameterList")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.agarthavision.ui.verify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AgarthaColors

/** Corner radius shared by every field in the manual-verify species form. */
internal val FieldShape = RoundedCornerShape(12.dp)

/** Value text style shared by every field in the manual-verify species form. */
internal val FieldTextStyle: TextStyle
    @Composable
    get() = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textPrimary,
    )

/**
 * Label-above text matching [com.agarthavision.ui.components.SheetInput] and
 * `SearchableDropdown`'s `FieldLabel` — 13sp Medium, no rule line, a 6dp gap to the field below.
 */
@Composable
internal fun FieldLabel(label: String) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AgarthaTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * The single [OutlinedTextFieldDefaults.colors] used by every field in this file, replacing the
 * two near-duplicate `fieldColors` helpers that used to live separately on [SpeciesDropdown] and
 * [StageDropdown]. Border and background follow the same state logic as `SheetInput` and
 * `SearchField`: danger on error, accent while focused, `borderStrong` otherwise.
 */
@Composable
internal fun fieldColors(colors: AgarthaColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = colors.accent,
    unfocusedBorderColor = colors.borderStrong,
    errorBorderColor = colors.danger,
    focusedContainerColor = colors.surface,
    unfocusedContainerColor = colors.surface,
    errorContainerColor = colors.dangerTint.copy(alpha = 0.5f),
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
    errorTextColor = colors.textPrimary,
    cursorColor = colors.accent,
    errorCursorColor = colors.danger,
)

// Selection state, the two callbacks it raises, the modifier and the suggestion list.
// Bundling them would add a type that exists only to satisfy the threshold.
@Suppress("LongParameterList")
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
    val colors = AgarthaTheme.colors
    val colorScheme = fieldColors(colors)

    Column(modifier = modifier) {
        FieldLabel(stringResource(R.string.verify_species_picker_label))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.displayName.orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = FieldTextStyle,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = colorScheme,
                shape = FieldShape,
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
                        text = { Text(species.displayName) },
                        onClick = {
                            onSpeciesSelected(species)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (selected == EggSpecies.OTHER) {
            FieldLabel(stringResource(R.string.verify_other_label))
            OutlinedTextField(
                value = otherText,
                onValueChange = onOtherTextChanged,
                placeholder = { Text(stringResource(R.string.verify_other_hint)) },
                textStyle = FieldTextStyle,
                colors = colorScheme,
                shape = FieldShape,
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

@Suppress("LongParameterList")
@Composable
fun StageDropdown(
    selectedSpecies: EggSpecies?,
    selectedStage: EggStage?,
    onStageSelected: (EggStage) -> Unit,
    modifier: Modifier = Modifier,
    otherStageText: String = "",
    onOtherStageTextChanged: (String) -> Unit = {},
) {
    val stages = remember(selectedSpecies) { EggStage.forSpecies(selectedSpecies) }
    if (stages.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    val colors = AgarthaTheme.colors
    val colorScheme = fieldColors(colors)

    Column(modifier = modifier) {
        FieldLabel(stringResource(R.string.verify_stage_picker_label))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedStage?.getDisplayName(selectedSpecies).orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = FieldTextStyle,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = colorScheme,
                shape = FieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                stages.forEach { stage ->
                    DropdownMenuItem(
                        text = { Text(stage.getDisplayName(selectedSpecies)) },
                        onClick = {
                            onStageSelected(stage)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (selectedStage == EggStage.OTHER) {
            FieldLabel(stringResource(R.string.verify_other_stage_label))
            OutlinedTextField(
                value = otherStageText,
                onValueChange = onOtherStageTextChanged,
                placeholder = { Text(stringResource(R.string.verify_other_stage_hint)) },
                textStyle = FieldTextStyle,
                colors = colorScheme,
                shape = FieldShape,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VerifyTestTags.OTHER_STAGE_FIELD),
            )
        }
    }
}
