@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.agarthavision.ui.verify

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import com.agarthavision.domain.usecase.verify.VerificationTarget
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.AgarthaButtonVariant
import com.agarthavision.ui.components.AgarthaToastHost
import com.agarthavision.ui.components.rememberAgarthaToastState
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.DialogShape
import kotlinx.coroutines.launch
import java.io.File
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
    val toastState = rememberAgarthaToastState()
    val finishFirst = stringResource(R.string.verify_add_species_blocked)

    // Keyed on the id, not the frame: FlaggedFrame equality covers mutable fields
    // such as the answers already given, so keying on the frame would re-seed it — and wipe
    // the in-progress answers — every time the store re-emits.
    LaunchedEffect(frame.sampleId) {
        viewModel.setFrame(frame, prior)
    }

    LaunchedEffect(viewModel, toastState) {
        viewModel.events.collect { event ->
            when (event) {
                is VerificationEvent.Dismiss -> onDismiss()
                is VerificationEvent.ShowError -> Unit
                VerificationEvent.FinishCurrentSpeciesFirst -> toastState.show(finishFirst)
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
                onStageSelected = viewModel::onStageSelected,
                onOtherStageChanged = viewModel::onOtherStageChanged,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onDetectionPrev = viewModel::onDetectionPrev,
                onDetectionNext = viewModel::onDetectionNext,
                onFramePrev = viewModel::onFramePrev,
                onFrameNext = viewModel::onFrameNext,
                onDeleteFrame = viewModel::onDeleteFrame,
                onToggleBoundingBoxes = viewModel::onToggleBoundingBoxes,
                onSubmit = viewModel::onSubmit,
                onCancel = viewModel::onCancel,
                onUserNoteChanged = viewModel::onUserNoteChanged,
                onAddSpecies = viewModel::onAddSpecies,
                onRemoveFinding = viewModel::onRemoveFinding,
                onExpandFinding = viewModel::onExpandFinding,
                onCollapseFinding = viewModel::onCollapseFinding,
                onFieldTotalChanged = viewModel::onFieldTotalChanged,
                onAddedSpeciesSelected = viewModel::onAddedSpeciesSelected,
                onAddedStageSelected = viewModel::onAddedStageSelected,
                onAddedOtherSpeciesChanged = viewModel::onAddedOtherSpeciesChanged,
                onAddedOtherStageChanged = viewModel::onAddedOtherStageChanged,
                onBeginDraw = viewModel::onBeginDraw,
                onBoxDrawn = viewModel::onBoxDrawn,
                onCancelDraw = viewModel::onCancelDraw,
                onRemoveDrawnBox = viewModel::onRemoveDrawnBox,
                onRemoveReplacementBox = viewModel::onRemoveReplacementBox,
                onConfirmLeave = viewModel::onConfirmLeave,
                onDismissLeave = viewModel::onDismissLeave,
            ),
        )
        AgarthaToastHost(
            state = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@VisibleForTesting
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun VerificationSheetContent(
    state: VerificationUiState,
    actions: VerificationSheetActions,
) {
    val frame = state.frame ?: return

    // Hoisted above the draw screen and kept for the sheet's whole life, so cancelling a draw
    // lands the medtech on the exact scroll they left rather than back at the top.
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val showDiscardConfirm = remember { mutableStateOf(false) }
    // The sample's label is the moment it was captured. The same label the queue row carries,
    // so the row the medtech tapped names the screen they land on.
    val capturedAtLabel = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }
    val currentPrediction = frame.predictions.getOrNull(state.currentDetectionIndex)
    val currentAnswers = state.findings.getOrNull(state.currentDetectionIndex)?.answers
    val boxCount = frame.predictions.size
    val anchor = remember { ExpandedCardAnchor() }
    val expandedIndexState = rememberUpdatedState(state.expandedFindingIndex)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (state.isDrawing) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            // 1. Top bar: back, and the sample's label. Pinned at top, not scrollable.
            ScreenTopBar(
                title = capturedAtLabel,
                metaText = "",
                onBack = actions.onCancel,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 32.dp)
                    .onGloballyPositioned { anchor.sheet = it }
                    .collapseOnOutsideTap(
                        anchor = anchor,
                        expandedIndex = { expandedIndexState.value },
                        onOutsideTap = actions.onCollapseFinding,
                    )
                    .verticalScroll(scrollState),
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                    // 2. Frame section: the image, then one row carrying where you are and how to move.
                    val imageModel = rememberFrameImageModel(frame, state.imageSource)
                    if (imageModel == null) {
                        // Honest about it, rather than opening a blank canvas the medtech might
                        // annotate into the void. A missing image and an empty one used to be
                        // indistinguishable here: File("").readBytes() threw, getOrDefault swallowed it,
                        // and every sample synced from another device opened silently empty.
                        FrameUnavailable(
                            reason = state.imageSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(frame.previewAspectRatio())
                                .testTag(VerifyTestTags.FRAME_UNAVAILABLE)
                                .clip(RoundedCornerShape(18.dp))
                                .border(0.5.dp, AgarthaTheme.colors.border, RoundedCornerShape(18.dp)),
                        )
                    } else {
                        FrameWithBoxes(
                            imageModel = imageModel,
                            // The model's boxes AND the medtech's own, which is the whole of 86d4by5n4:
                            // `frame.predictions` alone never held a hand-drawn box, so every one of them
                            // was invisible and a replaced box left the model's wrong rectangle on screen.
                            boxes = state.findings.frameBoxes(
                                active = DrawTarget(state.currentDetectionIndex),
                            ),
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
                    }

                    CycleRow(
                        indicator = if (state.frameIndexInQueue > 0) {
                            stringResource(
                                R.string.verify_sample_indicator,
                                state.frameIndexInQueue,
                                state.queueSize,
                            )
                        } else {
                            stringResource(R.string.verify_sample_out_of_queue)
                        },
                        prevDescription = stringResource(R.string.verify_prev_frame),
                        nextDescription = stringResource(R.string.verify_next_frame),
                        prevTag = VerifyTestTags.FRAME_PREV,
                        nextTag = VerifyTestTags.FRAME_NEXT,
                        canGoPrev = state.canGoPrev,
                        canGoNext = state.canGoNext,
                        onPrev = actions.onFramePrev,
                        onNext = actions.onFrameNext,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    )

                    // 3. Model output: what the container said, or that it said nothing, or that it has
                    //    not answered yet. Always present, never collapsed to two states.
                    ModelOutputSection(output = frame.modelOutput())

                    // 4. Current detection. Only when there is model output with at least one box -
                    //    every question in here is a question about a box.
                    if (boxCount > 0) {
                        CycleRow(
                            indicator = stringResource(
                                R.string.verify_detection_counter,
                                state.currentDetectionIndex + 1,
                                boxCount,
                            ),
                            prevDescription = stringResource(R.string.verify_prev_egg),
                            nextDescription = stringResource(R.string.verify_next_egg),
                            prevTag = VerifyTestTags.DETECTION_PREV,
                            nextTag = VerifyTestTags.DETECTION_NEXT,
                            // Counted from frame.predictions, never from the answer list: the medtech
                            // can append a species the model never boxed, so the answer list is the
                            // longer of the two and paging by it would walk off the end of the boxes.
                            canGoPrev = state.currentDetectionIndex > 0,
                            canGoNext = state.currentDetectionIndex < boxCount - 1,
                            onPrev = actions.onDetectionPrev,
                            onNext = actions.onDetectionNext,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    // Offered whenever the frame has anything to show, which since 86d4by5n4 includes a
                    // frame with no model output at all: a manual capture the medtech located eggs on by
                    // hand has boxes to hide and used to have no control that could hide them, because
                    // this sat inside the Current Detection block and that block needs a model box to
                    // exist. Its position is unchanged for every frame that has one.
                    if (boxCount > 0 || state.findings.any { it.answers.drawnBoxes.isNotEmpty() }) {
                        BoundingBoxesToggle(
                            checked = state.showBoundingBoxes,
                            onToggle = actions.onToggleBoundingBoxes,
                        )
                    }

                    if (boxCount > 0) {
                        BoxQuestionChain(
                            answers = currentAnswers,
                            suggestedSpecies = currentPrediction
                                ?.let { EggSpecies.fromClassLabel(it.classLabel) },
                            detectionIndex = state.currentDetectionIndex,
                            actions = actions,
                            // Derived against this field's own text, so a list fetched for another row
                            // - or for a keystroke since typed over - simply does not come back.
                            suggestions = state.suggestionsFor(
                                SuggestionTarget.CurrentDetection,
                                currentAnswers?.otherSpeciesText.orEmpty(),
                            ),
                        )
                    }

                    // 5. Add Species. Always present, with or without model output - it is the only
                    //    path by which a frame captured with the container unreachable can be verified
                    //    at all, and a frame with model output still needs it for eggs the model missed.
                    AddedFindings(
                        findings = state.findings,
                        boxCount = boxCount,
                        actions = actions,
                        expandedIndex = state.expandedFindingIndex,
                        onExpandedCardPositioned = { anchor.card = it },
                        suggestionsFor = { index ->
                            state.suggestionsFor(
                                SuggestionTarget.AddedFinding(index),
                                state.findings.getOrNull(index)?.answers?.otherSpeciesText.orEmpty(),
                            )
                        },
                    )

                    // No Q4 section. "Did the model miss any eggs in this frame?" is derived from the
                    // findings, not asked - see VerificationUiState.missedEgg. Claiming more eggs of a
                    // species than the model boxed already answers it, and asking again lets the two
                    // disagree.

                    // 6. Bottom bar: remarks, then Discard and Submit sharing a row.
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

        if (state.pendingLeave != null) {
            LeaveSampleDialog(
                onConfirm = actions.onConfirmLeave,
                onDismiss = actions.onDismissLeave,
            )
        }

        // Drawing covers the sheet rather than replacing it, so the sheet - and its scroll - is
        // still there to come back to. See DrawModeScreen for why drawing gets a screen at all. No
        // section of the sheet moved, so PB-13a's ordering (86d4bk51n) is untouched.
        if (state.isDrawing) {
            DrawModeScreen(
                state = state,
                actions = actions,
                // Back to the top on a save, where the frame is, so the box just drawn is the first
                // thing the medtech sees. A cancel has nothing new to show, and leaves them in place.
                onSaved = { box ->
                    actions.onBoxDrawn(box)
                    scope.launch { scrollState.scrollTo(0) }
                },
            )
        }
    }
}

/**
 * Asks before a cycle button or back throws away edits that were never submitted.
 *
 * Nothing on the sheet is kept until Submit, and the cycle buttons sit right beside the frame
 * where a stray thumb lands, so leaving a sample that has edits on it is a question rather than
 * an accident. Keep editing is the dismiss action, so tapping outside the dialog is also "stay".
 */
@Composable
private fun LeaveSampleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = { Text(stringResource(R.string.verify_leave_title)) },
        text = { Text(stringResource(R.string.verify_leave_body)) },
        confirmButton = {
            AgarthaButton(
                onClick = onConfirm,
                variant = AgarthaButtonVariant.Destructive,
                modifier = Modifier.testTag(VerifyTestTags.LEAVE_DIALOG_CONFIRM),
            ) {
                Text(stringResource(R.string.verify_leave_confirm))
            }
        },
        dismissButton = {
            AgarthaButton(
                onClick = onDismiss,
                variant = AgarthaButtonVariant.Secondary,
                modifier = Modifier.testTag(VerifyTestTags.LEAVE_DIALOG_DISMISS),
            ) {
                Text(stringResource(R.string.verify_leave_dismiss))
            }
        },
    )
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
 * The per-box statements: there is an egg → the box is placed right → it is the species the model
 * named. All three arrive pre-filled from model output, so a frame the model got right is
 * submitted without a tap, and unchecking is how the medtech disagrees. A ticked Q1 shows Q2 and
 * Q3 together; an unticked one hides both, since neither means anything without an egg.
 *
 * **Unticked always carries its correction**: the redraw action under Q2, the picker under Q3.
 *
 * The species step is a confirmation before a picker: the common answer is agreement, and
 * agreeing should not cost a pick from a list the medtech has just agreed with. Only unchecking
 * opens [SpeciesDropdown]. A model class the app cannot map to an [EggSpecies]
 * ([suggestedSpecies] null) has nothing to confirm, so the picker is offered directly.
 *
 * Q2 has three states worth naming, because the middle one is the whole point of the redraw
 * affordance and the last is what the checkbox made newly visible:
 *
 * - **Checked** — nothing under it. There is nothing to correct while the box is agreed to be
 *   right, which is why redrawing is not offered here.
 * - **Unchecked** — "Redraw the box", and it stays optional: unchecked with no redraw is a
 *   complete answer that records a localisation error on its own.
 * - **Unchecked, box replaced** — redraw and remove, and the checkbox is disabled, because the
 *   answer is latched until the replacement is removed.
 */
@Composable
private fun BoxQuestionChain(
    answers: VerificationAnswers?,
    suggestedSpecies: EggSpecies?,
    detectionIndex: Int,
    actions: VerificationSheetActions,
    suggestions: List<String> = emptyList(),
) {
    CheckQuestion(
        title = stringResource(R.string.verify_q1),
        tag = VerifyTestTags.QUESTION_Q1,
        checked = answers?.isEgg == true,
        onToggle = { actions.onQ1Selected(answers?.isEgg != true) },
    )
    if (answers?.isEgg != true) return

    CheckQuestion(
        title = stringResource(R.string.verify_q2),
        tag = VerifyTestTags.QUESTION_Q2,
        checked = answers.isBoxCorrect == true,
        // Latched once a box has been replaced: "the model placed this right" is false and stays
        // false, whoever fixed it afterwards. `onQ2Selected` already refuses the tap, but a
        // Yes/No pair showed that honestly - as a Yes button that would not take - and a
        // checkbox silently ignoring a tap reads as a bug, so it says so instead.
        enabled = !answers.boxReplaced,
        onToggle = { actions.onQ2Selected(answers.isBoxCorrect != true) },
    )

    // Offered whenever Q2 is unticked, and never while it is ticked - there is nothing to correct
    // while the model's box is agreed to be right. Optional: answering "No" without redrawing is
    // a complete answer that records a localisation error on its own.
    //
    // Keyed on "not ticked" rather than on `== false`. A checkbox draws null and false the same,
    // so keying on false alone left an unticked Q2 with no redraw under it whenever the answer
    // was merely unset - the medtech had to tick and untick it to get the action back.
    //
    // A replaced box keeps the redraw and gains a remove, the same pair an added egg's box has.
    // A box drawn in the wrong place used to be final here, while the same mistake on an added
    // egg could be undone. Removing takes back the medtech's box only: the model's comes back
    // into view, still marked misplaced, because a model box is never removed (C8).
    if (answers.isBoxCorrect != true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawBoxAction(
                icon = BoxIcons.redraw,
                label = stringResource(R.string.verify_redraw_box),
                tag = VerifyTestTags.REDRAW_BOX,
                onClick = { actions.onBeginDraw(detectionIndex, null) },
            )
            if (answers.boxReplaced) {
                DrawBoxAction(
                    icon = BoxIcons.remove,
                    label = stringResource(R.string.verify_remove_box),
                    tag = VerifyTestTags.REMOVE_REPLACEMENT_BOX,
                    onClick = { actions.onRemoveReplacementBox(detectionIndex) },
                )
            }
        }
    }
    if (answers.boxReplaced) {
        Text(
            text = stringResource(R.string.verify_box_replaced),
            color = AgarthaTheme.colors.textTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .testTag(VerifyTestTags.BOX_REPLACED_NOTE)
                .padding(start = 4.dp, bottom = 12.dp),
        )
    }
    // No early return on Q2, whatever it holds. A box in the wrong place still contains a real
    // egg, and that egg still has to be named and counted - short-circuiting on a no dropped
    // it from the low-power-field count, and left the frame permanently unsubmittable, because
    // `Finding.isComplete` asks for a species on a BOX_INCORRECT row too. The verdict still
    // records BOX_INCORRECT; see computeVerdict. Stopping on an unset Q2 hid Q3 behind a box
    // that already looked unticked, so every question under a ticked Q1 is always shown.

    if (suggestedSpecies != null) {
        CheckQuestion(
            title = stringResource(R.string.verify_q3, suggestedSpecies.displayName),
            tag = VerifyTestTags.QUESTION_Q3,
            checked = answers.speciesConfirmed == true,
            onToggle = { actions.onSpeciesConfirmed(answers.speciesConfirmed != true) },
        )
    }
    // "Not ticked", for the same reason as the redraw above: an unset confirmation draws as an
    // unticked Q3 and has to carry the picker an unticked Q3 always carries.
    if (suggestedSpecies == null || answers.speciesConfirmed != true) {
        SpeciesDropdown(
            selected = answers.species,
            otherText = answers.otherSpeciesText,
            onSpeciesSelected = actions.onSpeciesSelected,
            onOtherTextChanged = actions.onOtherSpeciesChanged,
            suggestions = suggestions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.SPECIES_DROPDOWN)
                .padding(bottom = 14.dp),
        )
    }

    val selectedSpecies = answers.species
    if (selectedSpecies != null && EggStage.forSpecies(selectedSpecies).isNotEmpty()) {
        StageDropdown(
            selectedSpecies = selectedSpecies,
            selectedStage = answers.stage,
            onStageSelected = actions.onStageSelected,
            otherStageText = answers.otherStageText,
            onOtherStageTextChanged = actions.onOtherStageChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VerifyTestTags.STAGE_DROPDOWN)
                .padding(bottom = 14.dp),
        )
    }
}

// DetectionCard and SourceBadge are gone with the section restructure.
//
// The card announced the current detection's species in large type above the questions; Q3 now
// asks about that species directly, one line below, and two statements of the same name is one
// more thing to read on a screen a medtech works through ten times a smear. The provenance pill
// it carried said AI-suggested or Manual, which is exactly what the model-output section's three
// states now say at greater length and in the place the medtech looks for it. The C7 caution the
// card sat above moved there with it.

/**
 * What Coil should load for this frame, or null when nothing can be.
 *
 * Three cases, in the order they are preferred. The frame's own bytes, which only exist on the
 * device that captured the sample. A local file, for a sample captured here and reopened. A
 * signed Storage URL, for one synced from another device — keyed on the stable storage path
 * rather than the URL, which carries a fresh token every time it is minted, so the disk cache
 * outlives the signature instead of missing on every open.
 */
@Composable
internal fun rememberFrameImageModel(frame: FlaggedFrame, source: SampleImageSource?): Any? {
    val context = LocalContext.current
    return remember(frame.sampleId, frame.jpegBytes.size, source) {
        when {
            frame.jpegBytes.isNotEmpty() -> frame.jpegBytes
            source is SampleImageSource.Local -> File(source.path)
            source is SampleImageSource.RemoteSignedUrl -> ImageRequest.Builder(context)
                .data(source.url)
                .memoryCacheKey(source.cacheKey)
                .diskCacheKey(source.cacheKey)
                .crossfade(true)
                .build()
            else -> null
        }
    }
}

/**
 * Says the frame cannot be shown, and why.
 *
 * The two reasons need different things from the medtech — one waits for a sync, the other for a
 * connection — and neither is "carry on annotating", which is what a blank canvas invites.
 */
@Composable
internal fun FrameUnavailable(reason: SampleImageSource?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(AgarthaTheme.colors.surfaceVariant)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when ((reason as? SampleImageSource.Unavailable)?.reason) {
                    SampleImageUnavailableReason.NO_STORAGE_PATH ->
                        R.string.sample_detail_image_no_storage_path
                    SampleImageUnavailableReason.REMOTE_LOAD_FAILED ->
                        R.string.sample_detail_image_remote_failed
                    else -> R.string.sample_detail_image_unavailable
                },
            ),
            color = AgarthaTheme.colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * An icon and a one-word label that draw, redraw or remove a box: [BoxIcons] names which.
 *
 * The icon makes the action findable at a glance, the label makes it unambiguous — a pencil and
 * a bin side by side under a question read as decoration until they say what they do. The label
 * is kept to a word so an egg row still fits its name beside two of these on a small phone.
 * Still low-key on purpose: drawing is always optional, and a filled button would read as
 * something the medtech has to do.
 *
 * The whole pair is one target, at least 48dp tall. The icon carries no description of its own:
 * the label beside it is what TalkBack reads, so the action is announced once, not twice.
 */
@Composable
internal fun DrawBoxAction(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val accent = AgarthaTheme.colors.accent
    Row(
        modifier = Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** One glyph per box action, so the same action looks the same on every row that offers it. */
internal object BoxIcons {
    /** Draw a box where there is none yet. */
    val draw: ImageVector = Icons.Outlined.CropFree

    /** Draw a box again, over one already there. */
    val redraw: ImageVector = Icons.Outlined.Edit

    /** Discard a box the medtech drew. Never offered on the model's own. */
    val remove: ImageVector = Icons.Outlined.Delete
}

/**
 * One thing the medtech is agreeing or disagreeing with, as a checkbox on a tappable row.
 *
 * Replaced a title over a Yes/No button pair, which cost about 80dp three times over on a screen
 * a medtech works through ten times a smear. The copy moved with it, from a question to a
 * statement, because that is what a checkbox reads as.
 *
 * **A checkbox has two states where the buttons had three**, and that is only safe because the
 * screen pre-fills every answer from model output (86d4bk51w): nothing prediction-backed reaches
 * here unanswered, so there is no third state left to draw. Submitting a pre-filled row is the
 * medtech's confirmation of it; a row they disagree with is one they untick.
 *
 * The whole row is the target, not the 24dp box (Fitts), and [enabled] is for an answer that is
 * latched rather than merely set — a disabled box says the tap will not take, where one that
 * silently ignores it reads as a bug.
 */
@Composable
internal fun CheckQuestion(
    title: String,
    tag: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.borderStrong,
                checkmarkColor = colors.onAccent,
            ),
        )
        Text(
            text = title,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
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

private class ExpandedCardAnchor {
    var sheet: LayoutCoordinates? = null
    var card: LayoutCoordinates? = null
}

private fun Modifier.collapseOnOutsideTap(
    anchor: ExpandedCardAnchor,
    expandedIndex: () -> Int?,
    onOutsideTap: (Int) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val index = expandedIndex() ?: return@awaitEachGesture
        val sheet = anchor.sheet
        val card = anchor.card
        val isAttached = card?.isAttached == true
        if (sheet != null && card != null && isAttached) {
            if (sheet.localBoundingBoxOf(card, clipBounds = false).contains(down.position)) {
                return@awaitEachGesture
            }
        }
        var isTap = true
        var pointerPressed = true
        while (pointerPressed) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null) {
                break
            }
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                isTap = false
            }
            pointerPressed = change.pressed
        }
        if (isTap) onOutsideTap(index)
    }
}

