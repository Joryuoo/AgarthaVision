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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.boxedCountOf
import com.agarthavision.domain.usecase.verify.fieldTotalOf
import com.agarthavision.domain.usecase.verify.floorFor
import com.agarthavision.domain.usecase.verify.toFindingRows
import com.agarthavision.domain.usecase.verify.unboxedCountOf
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
 * Add Species: what the medtech says is in this field, on top of whatever the model boxed.
 *
 * **Always present, with or without model output.** It is the only path by which a frame
 * captured while the inference container was unreachable can be verified at all, and a frame
 * with model output still needs it for eggs the model missed. Claiming more of a species than
 * the model boxed is also what answers Q4 — see `VerificationUiState.missedEgg`.
 *
 * **One card per species with a count, not one row per egg.** A Kato-Katz field can hold twenty
 * Ascaris, and a medtech counts them with a tally, not by adding twenty rows. The data agrees:
 * `sample_species_findings` is unique on `(sample_id, species)`, the detection ids derive from
 * the species, and the reopen path already rebuilt one row per species — the per-egg list was
 * the only part of the system that thought otherwise, and two cards naming one species came back
 * merged anyway.
 *
 * Rendered as a stacked, always-visible list rather than the one-at-a-time carousel the model's
 * boxes use. A box row is paged because `FrameWithBoxes` highlights exactly one box at a time
 * and the highlight is the point; an added species has no single box to highlight and has to be
 * scanned as a set, because the whole reason it exists is that a field can hold several species
 * at once.
 */
@Composable
internal fun AddedFindings(
    findings: List<Finding>,
    boxCount: Int,
    actions: VerificationSheetActions,
    modifier: Modifier = Modifier,
    /**
     * Species already on this device matching what the added row at this index is typing.
     *
     * A lookup rather than a list, because the rows are addressed by index and only one of them
     * is ever being typed into. Defaults to nothing, so a caller with no index to consult - a
     * preview, a screenshot test - needs no change.
     */
    suggestionsFor: (Int) -> List<String> = { emptyList() },
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
                // The eggs of this species the frame already accounts for: boxes the medtech
                // kept, plus boxes they drew themselves. Shown under the field as context, and
                // it is the floor the typed total may not go below.
                floor = findings.floorFor(findings[index].answers.speciesLabel),
                boxed = findings.boxedCountOf(findings[index].answers.speciesLabel),
                actions = actions,
                suggestions = suggestionsFor(index),
            )
        }

        Text(
            text = stringResource(R.string.verify_add_species),
            color = AgarthaTheme.colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .testTag(VerifyTestTags.ADD_SPECIES)
                .clickable { actions.onAddSpecies() }
                .padding(vertical = 10.dp),
        )

        LocateEggsSection(findings = findings, boxCount = boxCount, actions = actions)
    }
}

// `suggestions` is data and VerificationSheetActions is callbacks, so it cannot join the
// holder already here. A second holder would exist only to satisfy the threshold.
@Suppress("LongParameterList")
@Composable
private fun AddedFindingCard(
    index: Int,
    finding: Finding,
    floor: Int,
    boxed: Int,
    actions: VerificationSheetActions,
    suggestions: List<String> = emptyList(),
) {
    val total = finding.answers.fieldTotal
    val belowFloor = (total ?: 0) < floor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, AgarthaTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
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
            suggestions = suggestions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.addedSpeciesDropdown(index))
                .padding(bottom = 10.dp),
        )

        // The field asks for the **total** for this species, not the eggs beyond the model's
        // boxes. A medtech counting 23 Ascaris against nine boxed ones would otherwise have to
        // work out 14 in their head, under time pressure, with nothing anywhere to catch a slip
        // - and the wrong number reaches the low-power-field count in silence.
        OutlinedTextField(
            value = total?.toString().orEmpty(),
            onValueChange = { actions.onFieldTotalChanged(index, it) },
            label = { Text(stringResource(R.string.verify_field_total_label)) },
            isError = belowFloor,
            supportingText = when {
                belowFloor -> { { Text(stringResource(R.string.verify_field_total_floor, floor)) } }
                boxed > 0 -> { { Text(stringResource(R.string.verify_field_total_boxed, boxed)) } }
                else -> null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(220.dp)
                .testTag(VerifyTestTags.countField(index)),
        )
    }
}

/**
 * The eggs with no box yet, hidden behind one line until the medtech asks for them.
 *
 * **Why it is optional, and must stay optional.** Locating an egg is what turns a count into
 * something a detector can train on, and this is the only place a missed egg gets geometry at
 * all. But gating submit on it would make declaring fourteen missed eggs cost fourteen drawings,
 * and the medtech's way out of that is to stop declaring them — at which point the app loses the
 * count *and* records that the model was complete. Never make the honest answer more expensive
 * than the lazy one.
 *
 * **Why the label carries the count.** A closed disclosure only pulls at someone (Zeigarnik) if
 * the unfinished work is visible while it is shut. "0 of 14 located" does that; a bare "Show
 * list" hides the fact that there is anything to do.
 */
@Composable
private fun LocateEggsSection(
    findings: List<Finding>,
    boxCount: Int,
    actions: VerificationSheetActions,
) {
    val slots = findings.locatableSlots(boxCount)
    if (slots.isEmpty()) return

    val expanded = rememberSaveable { mutableStateOf(false) }
    val located = slots.count { it.box != null }
    val colors = AgarthaTheme.colors

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.LOCATE_TOGGLE)
                .clickable { expanded.value = !expanded.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded.value) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = stringResource(R.string.verify_locate_eggs, located, slots.size),
                    color = colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.verify_locate_eggs_note),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        if (expanded.value) {
            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.verify_locate_egg_row,
                            slot.species,
                            slot.ordinalInField,
                            slot.fieldTotal,
                        ),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    DrawBoxAction(
                        label = stringResource(
                            if (slot.box == null) R.string.verify_draw_box else R.string.verify_redraw_box,
                        ),
                        tag = VerifyTestTags.drawBox(slot.findingIndex, slot.slot),
                        onClick = { actions.onBeginDraw(slot.findingIndex, slot.slot) },
                    )
                    // Offered only where there is a box to discard. Accepting one used to be
                    // final: the species' total is floored at the boxes drawn on it, so a box in
                    // the wrong place made its own count unlowerable and the only way out was to
                    // remove the species and retype it. Removing leaves the count alone — the egg
                    // is still there, it simply goes back to unlocated.
                    if (slot.box != null) {
                        DrawBoxAction(
                            label = stringResource(R.string.verify_remove_box),
                            tag = VerifyTestTags.removeDrawnBox(slot.findingIndex, slot.slot),
                            onClick = { actions.onRemoveDrawnBox(slot.findingIndex, slot.slot) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One egg of an added species that has no box from the model — drawn or still to draw.
 *
 * [ordinalInField] counts within the species across the whole frame, so the eggs the model boxed
 * take the first numbers and these carry on from there: "egg 10 of 23" means what it says to
 * someone looking down a microscope, where "unboxed egg 1" would not.
 */
private data class LocatableSlot(
    val findingIndex: Int,
    val slot: Int,
    val species: String,
    val ordinalInField: Int,
    val fieldTotal: Int,
    val box: ImageBox?,
)

private fun List<Finding>.locatableSlots(boxCount: Int): List<LocatableSlot> =
    indices.filter { it >= boxCount }.flatMap { index ->
        val answers = this[index].answers
        val species = answers.speciesLabel ?: return@flatMap emptyList<LocatableSlot>()
        val boxed = boxedCountOf(species)
        (0 until unboxedCountOf(species)).map { slot ->
            LocatableSlot(
                findingIndex = index,
                slot = slot,
                species = species,
                ordinalInField = boxed + slot + 1,
                fieldTotal = fieldTotalOf(species),
                box = answers.drawnBoxes.getOrNull(slot),
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

// ManualSpeciesChecklist and ManualSpeciesRow are gone.
//
// They were a second, parallel way to say what is in a field, reached only when the inference
// container had been unreachable at capture time. Add Egg says the same thing for every frame,
// so a medtech working a smear with the container down now uses the screen they already know
// instead of a different one that happens to look similar. The "no eggs / species detected"
// row went with it: submitting an empty list is that assertion, and a tap asking the medtech to
// restate the absence of work is a tap the screen can do without.
