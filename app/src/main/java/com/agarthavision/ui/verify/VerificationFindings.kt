@file:Suppress("FunctionNaming")

package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.domain.usecase.verify.toFindingRows
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * What the model said about this frame — one of exactly three states, never collapsed to two.
 *
 * The distinction the medtech has to be able to make here is between **a clean field** and **a
 * container that never answered**. A clean field is a real clinical result and the most common
 * one in surveillance; "no model output" means the server was unreachable. Collapsing them would
 * make a negative smear indistinguishable from a broken container, and 86d4a6prb persists a
 * zero-detection frame with an empty predictions list precisely so the clean field is recorded.
 *
 * What it reports is the **model's own claim**, before any human answer — deliberately not the
 * same number as [FindingsSummary], which shows what submitting would write. The gap between the
 * two is what the medtech is here to create.
 */
@Composable
internal fun ModelOutputSection(
    output: ModelOutput,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.MODEL_OUTPUT_PANEL)
            .padding(bottom = 14.dp),
    ) {
        SectionLabel(stringResource(R.string.verify_model_output))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            when (output) {
                ModelOutput.InProgress -> CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .testTag(VerifyTestTags.MODEL_OUTPUT_SPINNER)
                        .size(24.dp),
                )

                ModelOutput.Unavailable -> Text(
                    text = stringResource(R.string.verify_model_unavailable),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )

                is ModelOutput.Read -> if (output.species.isEmpty()) {
                    // A result, not a failure. The wording says what the model did, not what it
                    // failed to do.
                    Text(
                        text = stringResource(R.string.verify_model_no_eggs),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    output.species.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(row.label, color = colors.textPrimary, fontSize = 13.sp)
                            Text(
                                row.count.toString(),
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.verify_model_total),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            output.total.toString(),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        // The C7 caution, moved here from the detection card it used to sit under. It is a
        // caution about model output, so it belongs to the section that reports model output -
        // and it is now shown once per frame rather than once per box.
        if (output is ModelOutput.Read && output.species.isNotEmpty()) {
            Text(
                text = stringResource(R.string.verify_ai_suggestion_note),
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .testTag(VerifyTestTags.AI_SUGGESTION_NOTE)
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * The species the medtech added on top of whatever the model boxed.
 *
 * Rendered as a stacked, always-visible list rather than the one-at-a-time carousel the model's
 * boxes use. A box row is paged because `FrameWithBoxes` highlights exactly one box at a time
 * and the highlight is the point; an added row has no box to highlight and has to be scanned as
 * a set, because the whole reason it exists is that a field can hold several species at once.
 *
 * A manual capture therefore renders one card and no carousel — which is the ManualSheet
 * experience, unified rather than merged.
 */
@Composable
internal fun AddedFindings(
    findings: List<Finding>,
    boxCount: Int,
    actions: VerificationSheetActions,
    modifier: Modifier = Modifier,
) {
    val addedIndices = findings.indices.filter { it >= boxCount }

    Column(modifier = modifier.fillMaxWidth()) {
        if (addedIndices.isNotEmpty()) {
            SectionLabel(stringResource(R.string.verify_species_you_added))
        }

        addedIndices.forEach { index ->
            AddedFindingCard(
                index = index,
                finding = findings[index],
                // Counted separately from this row: the boxes the model already drew for this
                // same species. Shown so the medtech types the additional eggs rather than
                // the field total, which would double-count.
                alreadyBoxed = findings.take(boxCount).count { boxed ->
                    boxed.eggContribution > 0 &&
                        boxed.answers.speciesLabel != null &&
                        boxed.answers.speciesLabel == findings[index].answers.speciesLabel
                },
                actions = actions,
            )
        }

        Text(
            text = stringResource(R.string.verify_add_species),
            color = AgarthaTheme.colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .testTag(VerifyTestTags.ADD_SPECIES)
                .clickable { actions.onAddFinding() }
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun AddedFindingCard(
    index: Int,
    finding: Finding,
    alreadyBoxed: Int,
    actions: VerificationSheetActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // States plainly that there is no model output behind this row, rather than
            // leaving a blank the reader has to interpret.
            Text(
                text = stringResource(R.string.verify_not_boxed),
                color = AgarthaTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = stringResource(R.string.verify_remove_species),
                color = AgarthaTheme.colors.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .testTag(VerifyTestTags.removeFinding(index))
                    .clickable { actions.onRemoveFinding(index) },
            )
        }

        SpeciesDropdown(
            selected = finding.answers.species,
            otherText = finding.answers.otherSpeciesText,
            onSpeciesSelected = { actions.onAddedSpeciesSelected(index, it) },
            onOtherTextChanged = { actions.onAddedOtherSpeciesChanged(index, it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.addedSpeciesDropdown(index))
                .padding(bottom = 10.dp),
        )

        OutlinedTextField(
            value = finding.answers.eggCount?.toString().orEmpty(),
            onValueChange = { actions.onEggCountChanged(index, it) },
            label = { Text(stringResource(R.string.verify_egg_count_label)) },
            supportingText = if (alreadyBoxed > 0) {
                { Text(stringResource(R.string.verify_egg_count_addendum, alreadyBoxed)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(180.dp)
                .testTag(VerifyTestTags.countField(index)),
        )
    }
}

/**
 * What submitting would actually write, recomputed as the medtech answers.
 *
 * This is the annotator's feedback loop: the clinical record forming in front of them, per
 * species, which is the unit the count is interpreted in — WHO intensity thresholds differ
 * between species by more than an order of magnitude, so a combined total is uninterpretable.
 * Read-only — the numbers come from the box answers and the typed counts above it.
 */
@Composable
internal fun FindingsSummary(
    findings: List<Finding>,
    modifier: Modifier = Modifier,
) {
    val rows = findings.toFindingRows()
    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.FINDINGS_SUMMARY)
            .padding(bottom = 14.dp),
    ) {
        SectionLabel(stringResource(R.string.verify_will_be_saved))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgarthaTheme.colors.accentTint, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = row.species,
                        color = AgarthaTheme.colors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = row.eggCount.toString(),
                        color = AgarthaTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AgarthaTheme.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Manual-capture species selection UI.
 *
 * Replaces the AI-path Q1→Q2→Q3 chain and AddedFindings entirely for frames whose source
 * is [FrameSource.MANUAL]. The medtech either asserts nothing was present (no-detection row)
 * or checks off whichever species they observed and types a count per species. The "Other…"
 * option reveals a free-text name field above its count field, reusing [EggSpecies.OTHER]
 * and [com.agarthavision.domain.usecase.verify.VerificationAnswers.otherSpeciesText].
 */
@Composable
internal fun ManualSpeciesChecklist(
    findings: List<Finding>,
    noDetectionSelected: Boolean,
    actions: VerificationSheetActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.verify_select_species))

        // Radio-like no-detection row — mutually exclusive with any species selection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(
                    if (noDetectionSelected) AgarthaTheme.colors.accent else AgarthaTheme.colors.surface,
                    RoundedCornerShape(8.dp),
                )
                .border(
                    1.dp,
                    if (noDetectionSelected) AgarthaTheme.colors.accent else AgarthaTheme.colors.borderStrong,
                    RoundedCornerShape(8.dp),
                )
                .clickable { actions.onManualNoDetectionSelected() }
                .testTag(VerifyTestTags.MANUAL_NO_DETECTION)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.verify_no_detection),
                color = if (noDetectionSelected) AgarthaTheme.colors.onAccent else AgarthaTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        listOf(EggSpecies.ASCARIS, EggSpecies.TRICHURIS, EggSpecies.HOOKWORM, EggSpecies.OTHER)
            .forEach { species ->
                val checked = findings.any { it.answers.species == species }
                val finding = findings.find { it.answers.species == species }
                ManualSpeciesRow(
                    species = species,
                    checked = checked,
                    finding = finding,
                    actions = actions,
                )
            }
    }
}

@Composable
private fun ManualSpeciesRow(
    species: EggSpecies,
    checked: Boolean,
    finding: Finding?,
    actions: VerificationSheetActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { actions.onManualSpeciesToggled(species, it) },
                modifier = Modifier.testTag(VerifyTestTags.manualSpeciesCheckbox(species)),
            )
            Text(
                text = species.displayName,
                color = AgarthaTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        if (checked) {
            // For OTHER: reveal the free-text name field above the count field.
            if (species == EggSpecies.OTHER) {
                OutlinedTextField(
                    value = finding?.answers?.otherSpeciesText.orEmpty(),
                    onValueChange = actions.onManualOtherNameChanged,
                    label = { Text(stringResource(R.string.verify_other_label)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.verify_other_hint),
                            color = AgarthaTheme.colors.textTertiary,
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(VerifyTestTags.MANUAL_OTHER_NAME_FIELD)
                        .padding(bottom = 8.dp),
                )
            }

            // Count field — starts empty, "0" as placeholder (D1).
            OutlinedTextField(
                value = finding?.answers?.eggCount?.toString().orEmpty(),
                onValueChange = { actions.onManualCountChanged(species, it) },
                label = { Text(stringResource(R.string.verify_manual_count_label)) },
                placeholder = { Text("0", color = AgarthaTheme.colors.textTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(180.dp)
                    .testTag(VerifyTestTags.manualCountField(species)),
            )
        }
    }
}
