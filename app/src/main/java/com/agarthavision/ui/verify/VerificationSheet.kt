@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.activity.compose.BackHandler
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
import com.agarthavision.ui.records.AppColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VerificationSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(frame) {
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
            .background(AppColors.Gray200)
            .systemBarsPadding(),
    ) {
        VerificationSheetContent(
            state = state,
            actions = VerificationSheetActions(
                onQ1Selected = viewModel::onQ1Selected,
                onQ2Selected = viewModel::onQ2Selected,
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
                onToggleRepeat = viewModel::onToggleRepeat,
                onUserNoteChanged = viewModel::onUserNoteChanged,
            ),
        )
    }
}

@Composable
private fun VerificationSheetContent(
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
    val currentAnswers = state.answers.getOrNull(state.currentDetectionIndex)
    val speciesName = currentPrediction?.classLabel ?: "Unknown"
    val confidence = currentPrediction?.confidence ?: 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar(
            title = "Verify detection",
            metaText = "Frame ${state.frameIndexInQueue}/${state.queueSize} · $timeLabel",
            onBack = actions.onCancel,
            actions = {
                // Repeat sample toggle (persists to Room via FlaggedFrameStore.toggleRepeat)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.isRepeat) AppColors.BlueTint else Color.Transparent)
                        .border(
                            width = 0.5.dp,
                            color = if (state.isRepeat) AppColors.Blue.copy(alpha = 0.35f) else AppColors.Gray300,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { actions.onToggleRepeat() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isRepeat) Icons.Filled.Flag else Icons.Outlined.Flag,
                        contentDescription = if (state.isRepeat) "Repeat sample (enabled)" else "Mark as repeat sample",
                        tint = if (state.isRepeat) AppColors.Blue else AppColors.Gray700,
                    )
                }
            },
        )

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                SmallToggle("Previous frame", false, actions.onFramePrev, Modifier.weight(1f))
                SmallToggle("Next frame", false, actions.onFrameNext, Modifier.weight(1f))
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
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
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
                        color = AppColors.Gray900,
                    )
                    Text(
                        text = "Detection ${state.currentDetectionIndex + 1} of ${state.answers.size.coerceAtLeast(1)}",
                        color = AppColors.Gray500,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "${(confidence * 100).toInt()}% Conf",
                    color = AppColors.Blue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                SmallToggle("Prev detection", false, actions.onDetectionPrev, Modifier.weight(1f))
                SmallToggle("Next detection", false, actions.onDetectionNext, Modifier.weight(1f))
            }

            QuestionSection(
                title = stringResource(R.string.verify_q1),
                options = listOf(true to "Yes", false to "No"),
                selected = currentAnswers?.isEgg,
                onSelect = actions.onQ1Selected,
            )

            if (currentAnswers?.isEgg == true) {
                QuestionSection(
                    title = stringResource(R.string.verify_q2),
                    options = listOf(true to "Yes", false to "No"),
                    selected = currentAnswers.isBoxCorrect,
                    onSelect = actions.onQ2Selected,
                )

                if (currentAnswers.isBoxCorrect == true) {
                    SpeciesDropdown(
                        selected = currentAnswers.species,
                        otherText = currentAnswers.otherSpeciesText,
                        onSpeciesSelected = actions.onSpeciesSelected,
                        onOtherTextChanged = actions.onOtherSpeciesChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                    )
                }
            }

            QuestionSection(
                title = stringResource(R.string.verify_q4),
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
                    color = AppColors.Gray700,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.showBoundingBoxes,
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
                        checkedTrackColor = AppColors.Blue,
                        checkedIconColor = AppColors.Blue,
                        uncheckedThumbColor = AppColors.Gray500,
                        uncheckedTrackColor = AppColors.Gray200,
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
                    color = AppColors.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            SheetActionRow(
                primaryLabel = "Submit",
                secondaryLabel = "Discard",
                onPrimaryClick = actions.onSubmit,
                onSecondaryClick = { showDiscardConfirm.value = true },
                primaryLoading = state.isSubmitting,
                primaryEnabled = state.canSubmit,
            )
        }
    }

    if (showDiscardConfirm.value) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm.value = false },
            title = { Text("Discard this frame?") },
            text = { Text("This will remove the current frame from the verification queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm.value = false
                        actions.onDeleteFrame()
                    },
                    enabled = !state.isSubmitting,
                ) {
                    Text("Discard", color = AppColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun <T> QuestionSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    Text(
        text = title,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Gray500,
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
                    .background(if (isSelected) AppColors.Blue else AppColors.White, RoundedCornerShape(8.dp))
                    .border(1.dp, if (isSelected) AppColors.Blue else AppColors.Gray300, RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) AppColors.White else AppColors.Gray900,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SmallToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(if (selected) AppColors.BlueTint else AppColors.White, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) AppColors.Blue else AppColors.Gray300, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Blue else AppColors.Gray700,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = AppColors.Gray400, fontSize = 13.sp) },
        minLines = 1,
        maxLines = 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Blue,
            unfocusedBorderColor = AppColors.Gray300,
            focusedContainerColor = AppColors.White,
            unfocusedContainerColor = AppColors.White,
            focusedTextColor = AppColors.Gray900,
            unfocusedTextColor = AppColors.Gray900,
        ),
    )
}
