@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.domain.usecase.verify.VerificationTarget
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.AppTypography
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.theme.MonoDataStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VerificationSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel(),
    /**
     * What the medtech already said about this sample, when it has been verified before.
     *
     * Empty for a sample opened from capture, which has no history yet. Supplied by the queue,
     * which loads it through `OpenVerificationTargetUseCase` - without it, reopening a verified
     * sample would show a blank questionnaire and the edit would be a re-review.
     */
    prior: VerificationTarget? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the id, not the frame: FlaggedFrame equality covers mutable fields
    // such as the answers already given, so keying on the frame would re-seed it — and wipe
    // the in-progress answers — every time the store re-emits.
    LaunchedEffect(frame.sampleId) {
        viewModel.setFrame(frame, prior)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VerificationEvent.Dismiss -> onDismiss()
                is VerificationEvent.ShowError -> Unit
            }
        }
    }

    BackHandler(onBack = viewModel::onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgarthaTheme.colors.background)
            .systemBarsPadding(),
    ) {
        VerificationSheetContent(
            state = state,
            actions = VerificationSheetActions(
                onQ1Selected = viewModel::onQ1Selected,
                onQ2Selected = viewModel::onQ2Selected,
                onSpeciesConfirmed = viewModel::onSpeciesConfirmed,
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onQ4Selected = viewModel::onQ4Selected,
                onDetectionPrev = viewModel::onDetectionPrev,
                onDetectionNext = viewModel::onDetectionNext,
                onFramePrev = viewModel::onFramePrev,
                onFrameNext = viewModel::onFrameNext,
                onDeleteFrame = viewModel::onDeleteFrame,
                onToggleBoundingBoxes = viewModel::onToggleBoundingBoxes,
                onSubmit = viewModel::onSubmit,
                onCancel = viewModel::onCancel,
                onUserNoteChanged = viewModel::onUserNoteChanged,
                onAddFinding = viewModel::onAddFinding,
                onRemoveFinding = viewModel::onRemoveFinding,
                onEggCountChanged = viewModel::onEggCountChanged,
                onAddedSpeciesSelected = viewModel::onAddedSpeciesSelected,
                onAddedOtherSpeciesChanged = viewModel::onAddedOtherSpeciesChanged,
            ),
        )
    }
}

@VisibleForTesting
@Composable
internal fun VerificationSheetContent(
    state: VerificationUiState,
    actions: VerificationSheetActions,
) {
    val frame = state.frame ?: return
    val showDiscardConfirm = remember { mutableStateOf(false) }
    val timeLabel = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }
    val currentPrediction = frame.predictions.getOrNull(state.currentDetectionIndex)
    val currentAnswers = state.findings.getOrNull(state.currentDetectionIndex)?.answers
    val speciesName = currentPrediction?.classLabel ?: "Unknown"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar(
            title = "Verify detection",
            // A frame marked repeat leaves the cycle and has no position, so show what it
            // is rather than "Frame 0/4".
            metaText = if (state.frameIndexInQueue > 0) {
                stringResource(
                    R.string.verify_frame_meta,
                    state.frameIndexInQueue,
                    state.queueSize,
                    timeLabel,
                )
            } else {
                stringResource(R.string.verify_frame_meta_out_of_cycle, timeLabel)
            },
            onBack = actions.onCancel,
        )

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            // Full width between the side margins, at the frame's own aspect ratio, so the
            // whole field is visible without letterboxing.
            FrameWithBoxes(
                jpegBytes = frame.jpegBytes,
                predictions = frame.predictions,
                highlightedIndex = state.currentDetectionIndex,
                showBoxes = state.showBoundingBoxes,
                inferenceImageWidth = frame.imageWidth,
                inferenceImageHeight = frame.imageHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.previewAspectRatio())
                    .testTag(VerifyTestTags.FRAME_PREVIEW)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.5.dp, AgarthaTheme.colors.border, RoundedCornerShape(18.dp)),
            )

            FrameNavRow(
                canGoPrev = state.canGoPrev,
                canGoNext = state.canGoNext,
                onPrev = actions.onFramePrev,
                onNext = actions.onFrameNext,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )

            // Says what the model produced when it produced nothing to review. Returns
            // without drawing when there are boxes - the card below is the model's output
            // in that case.
            ModelOutputPanel(frame = frame)

            // Everything in this block is about a model box. A manual capture has none, and
            // neither has a model frame the model called clean, so none of it is drawn: no
            // card, no egg pager, no boxes toggle, and none of the per-box questions. What
            // those frames get is the added-findings list below, which is the whole of the
            // manual-capture experience.
            val boxCount = frame.predictions.size
            if (boxCount > 0) {
                DetectionCard(
                    speciesName = speciesName,
                    detectionLabel = stringResource(
                        R.string.verify_detection_counter,
                        state.currentDetectionIndex + 1,
                        boxCount,
                    ),
                    source = frame.source,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                // Steps between the boxes on this frame. Only drawn when there is more than
                // one box, and each side goes dead at its end of the range, so it cannot read
                // as a way to leave the frame.
                //
                // Counted from `frame.predictions`, never from the answer list: the medtech
                // can append a species the model never boxed, so the answer list is the longer
                // of the two and paging by it would walk off the end of the boxes.
                if (boxCount > 1) {
                    NavPairRow(
                        state = NavPairState(
                            prevLabel = stringResource(R.string.verify_prev_egg),
                            nextLabel = stringResource(R.string.verify_next_egg),
                            prevTag = VerifyTestTags.DETECTION_PREV,
                            nextTag = VerifyTestTags.DETECTION_NEXT,
                            canGoPrev = state.currentDetectionIndex > 0,
                            canGoNext = state.currentDetectionIndex < boxCount - 1,
                            onPrev = actions.onDetectionPrev,
                            onNext = actions.onDetectionNext,
                        ),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                BoundingBoxesToggle(
                    checked = state.showBoundingBoxes,
                    onToggle = actions.onToggleBoundingBoxes,
                )

                BoxQuestionChain(
                    answers = currentAnswers,
                    suggestedSpecies = currentPrediction
                        ?.let { EggSpecies.fromClassLabel(it.classLabel) },
                    actions = actions,
                )
            }

            AddedFindings(
                findings = state.findings,
                boxCount = boxCount,
                actions = actions,
            )

            FindingsSummary(findings = state.findings)

            QuestionSection(
                title = stringResource(R.string.verify_q4),
                tag = VerifyTestTags.QUESTION_Q4,
                options = listOf(true to "Yes", false to "No"),
                selected = state.missedEgg,
                onSelect = actions.onQ4Selected,
            )

            SheetSectionLabel(
                text = stringResource(R.string.verify_remarks_label),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
            NoteField(
                value = state.userNote,
                onValueChange = actions.onUserNoteChanged,
                placeholder = stringResource(R.string.verify_remarks_placeholder),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = AgarthaTheme.colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            SheetActionRow(
                SheetActionRowState(
                    primaryLabel = "Submit",
                    secondaryLabel = "Discard",
                    onPrimaryClick = actions.onSubmit,
                    onSecondaryClick = { showDiscardConfirm.value = true },
                    primaryLoading = state.isSubmitting,
                    primaryEnabled = state.canSubmit,
                )
            )
        }
    }

    if (showDiscardConfirm.value) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm.value = false },
            shape = DialogShape,
            title = { Text("Discard this frame?") },
            text = { Text("This will remove the current frame from the verification queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm.value = false
                        actions.onDeleteFrame()
                    },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.testTag(VerifyTestTags.DISCARD_DIALOG_CONFIRM),
                ) {
                    Text("Discard", color = AgarthaTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm.value = false },
                    modifier = Modifier.testTag(VerifyTestTags.DISCARD_DIALOG_DISMISS),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Show / hide the model's boxes on the preview. Sits directly above the first question
 * because that question is about the highlighted box.
 */
@Composable
private fun BoundingBoxesToggle(checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.verify_boxes_toggle),
            color = AgarthaTheme.colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Switch(
            modifier = Modifier.testTag(VerifyTestTags.BOXES_TOGGLE),
            checked = checked,
            onCheckedChange = { onToggle() },
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Outlined.CropSquare,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.White,
                checkedTrackColor = AgarthaTheme.colors.accent,
                checkedIconColor = AgarthaTheme.colors.accent,
                uncheckedThumbColor = AgarthaTheme.colors.textSecondary,
                uncheckedTrackColor = AgarthaTheme.colors.borderStrong,
            ),
        )
    }
}

/**
 * The per-box questions, each revealed by the previous answer: is it an egg → is the box
 * placed right → is it the species the model named. The species step is a confirmation
 * before a picker: the common answer is yes, and a yes should not cost a pick from a list
 * the medtech has just agreed with. Only a no opens [SpeciesDropdown]. A model class the
 * app cannot map to an [EggSpecies] ([suggestedSpecies] null) has nothing to confirm, so
 * the picker is offered directly.
 */
@Composable
private fun BoxQuestionChain(
    answers: VerificationAnswers?,
    suggestedSpecies: EggSpecies?,
    actions: VerificationSheetActions,
) {
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
    // Deliberately `== null`, not `!= true`. A box in the wrong place still contains a real
    // egg, and that egg still has to be named and counted - short-circuiting on a no dropped
    // it from the low-power-field count, and left the frame permanently unsubmittable, because
    // `Finding.isComplete` asks for a species on a BOX_INCORRECT row too. The verdict still
    // records BOX_INCORRECT; see computeVerdict.
    if (answers.isBoxCorrect == null) return

    if (suggestedSpecies != null) {
        QuestionSection(
            title = stringResource(R.string.verify_q3, suggestedSpecies.displayName),
            tag = VerifyTestTags.QUESTION_Q3,
            options = listOf(true to "Yes", false to "No"),
            selected = answers.speciesConfirmed,
            onSelect = actions.onSpeciesConfirmed,
        )
    }
    if (suggestedSpecies == null || answers.speciesConfirmed == false) {
        SpeciesDropdown(
            selected = answers.species,
            otherText = answers.otherSpeciesText,
            onSpeciesSelected = actions.onSpeciesSelected,
            onOtherTextChanged = actions.onOtherSpeciesChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.SPECIES_DROPDOWN)
                .padding(bottom = 14.dp),
        )
    }
}

/**
 * Highlights what the model (or the medtech, for a manual capture) put in front of the
 * reviewer: the species large, which box this is beneath it, and the provenance pill.
 * A model frame carries a caution line under the card, because the name on it is a
 * suggestion the medtech is about to confirm or correct — not a finding (C7).
 */
@Composable
private fun DetectionCard(
    speciesName: String,
    detectionLabel: String,
    source: FrameSource,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    // Maroon brand surface, like Session Detail's hero card: everything on it reads in
    // onAccent tints, and the provenance pill becomes a light chip so it stays legible.
    val onCard = colors.onAccent
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.DETECTION_CARD)
                .background(colors.accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.verify_species_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onCard.copy(alpha = 0.72f),
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = speciesName,
                    style = AppTypography.headlineSmall,
                    color = onCard,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detectionLabel,
                    color = onCard.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    style = MonoDataStyle,
                )
            }
            SourceBadge(source = source, onAccentSurface = true)
        }
        if (source == FrameSource.MODEL) {
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
 * Provenance pill. On the maroon [DetectionCard] ([onAccentSurface]) it is a translucent
 * light chip; elsewhere it keeps the tinted accent/warning treatment.
 */
@Composable
private fun SourceBadge(source: FrameSource, onAccentSurface: Boolean = false) {
    val colors = AgarthaTheme.colors
    val isModelSource = source == FrameSource.MODEL
    val background = when {
        onAccentSurface -> colors.onAccent.copy(alpha = 0.18f)
        isModelSource -> colors.accentTint
        else -> colors.warningTint
    }
    val textColor = when {
        onAccentSurface -> colors.onAccent
        isModelSource -> colors.accent
        else -> colors.warningText
    }

    Box(
        modifier = Modifier
            .testTag(VerifyTestTags.SOURCE_BADGE)
            .background(color = background, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (isModelSource) "AI-suggested" else "Manual",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
internal fun <T> QuestionSection(
    title: String,
    tag: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    SheetSectionLabel(
        text = title,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) AgarthaTheme.colors.accent else AgarthaTheme.colors.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) AgarthaTheme.colors.accent else AgarthaTheme.colors.borderStrong,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(value) }
                    .testTag(VerifyTestTags.questionOption(tag, label))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) AgarthaTheme.colors.onAccent else AgarthaTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(VerifyTestTags.NOTE_FIELD),
        placeholder = { Text(placeholder, color = AgarthaTheme.colors.textTertiary, fontSize = 13.sp) },
        minLines = 1,
        maxLines = 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgarthaTheme.colors.accent,
            unfocusedBorderColor = AgarthaTheme.colors.borderStrong,
            focusedContainerColor = AgarthaTheme.colors.surface,
            unfocusedContainerColor = AgarthaTheme.colors.surface,
            focusedTextColor = AgarthaTheme.colors.textPrimary,
            unfocusedTextColor = AgarthaTheme.colors.textPrimary,
        ),
    )
}
