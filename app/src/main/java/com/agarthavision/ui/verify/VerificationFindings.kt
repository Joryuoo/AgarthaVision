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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.domain.usecase.verify.toFindingRows
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * What the model said about this frame, or a plain statement that it said nothing.
 *
 * **Never silently hidden.** The three states below mean different things to a reviewer and to
 * the training corpus, and the last two are deliberately not collapsed into one "no model
 * output" line: "no signal was ever collected" and "the model looked and asserted nothing was
 * there" are different facts, and the second is itself a labelled datapoint.
 *
 * Derived from [FlaggedFrame.source] and the prediction list on every recomposition. There is
 * deliberately no `hasModelOutput` flag stored beside the source — that would be a second
 * source of truth for the same fact, and it would drift.
 */
@Composable
internal fun ModelOutputPanel(
    frame: FlaggedFrame,
    modifier: Modifier = Modifier,
) {
    val message = when {
        frame.source == FrameSource.MANUAL -> stringResource(R.string.verify_model_none_manual)
        frame.predictions.isEmpty() ->
            stringResource(
                R.string.verify_model_clean_field,
                frame.inferenceModelVersion.orEmpty(),
            )
        else -> return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.MODEL_OUTPUT_PANEL)
            .padding(bottom = 14.dp),
    ) {
        SectionLabel(stringResource(R.string.verify_model_output))
        Text(
            text = message,
            color = AgarthaTheme.colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(AgarthaTheme.colors.surfaceMuted, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
    }
}

/**
 * The question chain for one row: is it an egg, is the box right, and what is it.
 *
 * The first two are questions *about a box*. A row with no [prediction] — a manual capture, or
 * a species the medtech added — has none, so they do not render and are never answered on the
 * medtech's behalf. Such a row goes straight to the species picker.
 */
@Composable
internal fun BoxReview(
    prediction: Prediction?,
    answers: VerificationAnswers?,
    actions: VerificationSheetActions,
) {
    if (prediction != null) {
        QuestionSection(
            title = stringResource(R.string.verify_q1),
            tag = VerifyTestTags.QUESTION_Q1,
            options = listOf(true to "Yes", false to "No"),
            selected = answers?.isEgg,
            onSelect = actions.onQ1Selected,
        )
        if (answers?.isEgg != true) return
        QuestionSection(
            title = stringResource(R.string.verify_q2),
            tag = VerifyTestTags.QUESTION_Q2,
            options = listOf(true to "Yes", false to "No"),
            selected = answers.isBoxCorrect,
            onSelect = actions.onQ2Selected,
        )
        // Shown whether or not the box is correctly placed. A misplaced box still contains a
        // countable egg, and stopping here dropped it from the low-power-field count. The
        // verdict for the box still records BOX_INCORRECT.
        if (answers.isBoxCorrect == null) return
    }

    SpeciesDropdown(
        selected = answers?.species,
        otherText = answers?.otherSpeciesText.orEmpty(),
        onSpeciesSelected = actions.onSpeciesSelected,
        onOtherTextChanged = actions.onOtherSpeciesChanged,
        selectedStage = answers?.stage,
        onStageSelected = actions.onStageSelected,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.SPECIES_DROPDOWN)
            .padding(bottom = 14.dp),
        stageModifier = Modifier.testTag(VerifyTestTags.STAGE_DROPDOWN),
    )
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
                // same species and stage. Shown so the medtech types the additional eggs
                // rather than the field total, which would double-count.
                alreadyBoxed = findings.take(boxCount).count { boxed ->
                    boxed.eggContribution > 0 &&
                        boxed.answers.speciesLabel != null &&
                        boxed.answers.speciesLabel == findings[index].answers.speciesLabel &&
                        boxed.answers.stage == findings[index].answers.stage
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
            selectedStage = finding.answers.stage,
            onStageSelected = { actions.onAddedStageSelected(index, it) },
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
 * species and stage, which is the unit the count is interpreted in. Read-only — the numbers
 * come from the box answers and the typed counts above it.
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
                        text = row.stage?.let { "${row.species} · ${it.displayName}" } ?: row.species,
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
