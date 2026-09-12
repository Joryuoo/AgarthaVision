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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.DialogShape
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VerificationSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the id, not the frame: FlaggedFrame equality covers mutable fields
    // such as the answers already given, so keying on the frame would re-seed it — and wipe
    // the in-progress answers — every time the store re-emits.
    LaunchedEffect(frame.sampleId) {
        viewModel.setFrame(frame)
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
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onStageSelected = viewModel::onStageSelected,
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
                onAddedStageSelected = viewModel::onAddedStageSelected,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                SmallToggle(
                    label = stringResource(R.string.verify_prev_frame),
                    selected = false,
                    onClick = actions.onFramePrev,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(VerifyTestTags.FRAME_PREV),
                    enabled = state.canGoPrev,
                )
                SmallToggle(
                    label = stringResource(R.string.verify_next_frame),
                    selected = false,
                    onClick = actions.onFrameNext,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(VerifyTestTags.FRAME_NEXT),
                    enabled = state.canGoNext,
                )
            }

            FrameWithBoxes(
                jpegBytes = frame.jpegBytes,
                predictions = frame.predictions,
                highlightedIndex = state.currentDetectionIndex,
                showBoxes = state.showBoundingBoxes,
                inferenceImageWidth = frame.imageWidth,
                inferenceImageHeight = frame.imageHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .testTag(VerifyTestTags.FRAME_PREVIEW)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.5.dp, AgarthaTheme.colors.border, RoundedCornerShape(18.dp)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = speciesName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = AgarthaTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Detection ${state.currentDetectionIndex + 1} of " +
                            "${state.findings.size.coerceAtLeast(1)}",
                        color = AgarthaTheme.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                SourceBadge(source = frame.source)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                SmallToggle(
                    "Prev detection",
                    false,
                    actions.onDetectionPrev,
                    Modifier
                        .weight(1f)
                        .testTag(VerifyTestTags.DETECTION_PREV),
                )
                SmallToggle(
                    "Next detection",
                    false,
                    actions.onDetectionNext,
                    Modifier
                        .weight(1f)
                        .testTag(VerifyTestTags.DETECTION_NEXT),
                )
            }

            ModelOutputPanel(frame = frame)

            BoxReview(
                prediction = currentPrediction,
                answers = currentAnswers,
                actions = actions,
            )

            AddedFindings(
                findings = state.findings,
                boxCount = frame.predictions.size,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Boxes",
                    color = AgarthaTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Nothing to toggle without model output. FrameWithBoxes already paints
                // nothing on an empty prediction list, so this is about not offering a
                // control that does nothing rather than about the drawing.
                Switch(
                    modifier = Modifier.testTag(VerifyTestTags.BOXES_TOGGLE),
                    enabled = frame.predictions.isNotEmpty(),
                    checked = state.showBoundingBoxes && frame.predictions.isNotEmpty(),
                    onCheckedChange = { actions.onToggleBoundingBoxes() },
                    thumbContent = if (state.showBoundingBoxes) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.CropSquare,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.White,
                        checkedTrackColor = AgarthaTheme.colors.accent,
                        checkedIconColor = AgarthaTheme.colors.accent,
                        uncheckedThumbColor = AgarthaTheme.colors.textSecondary,
                        uncheckedTrackColor = AgarthaTheme.colors.borderStrong,
                    ),
                )
            }

            NoteField(
                value = state.userNote,
                onValueChange = actions.onUserNoteChanged,
                placeholder = "Notes for this sample",
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

@Composable
private fun SourceBadge(source: FrameSource) {
    val isModelSource = source == FrameSource.MODEL

    Box(
        modifier = Modifier
            .testTag(VerifyTestTags.SOURCE_BADGE)
            .background(
                color = if (isModelSource) AgarthaTheme.colors.accentTint else AgarthaTheme.colors.warningTint,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (isModelSource) "AI-suggested" else "Manual",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isModelSource) AgarthaTheme.colors.accent else AgarthaTheme.colors.warningText,
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
    Text(
        text = title,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textSecondary,
        letterSpacing = 0.8.sp,
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
