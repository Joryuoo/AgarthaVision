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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.components.glassChrome
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ManualSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: ManualCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the id, not the frame: FlaggedFrame equality covers mutable fields
    // like markedAsRepeat, so keying on the frame would re-seed the sheet — and wipe
    // the in-progress answers — every time the store re-emits.
    LaunchedEffect(frame.sampleId) {
        viewModel.setFrame(frame)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ManualCaptureEvent.Dismiss -> onDismiss()
                is ManualCaptureEvent.ShowError -> { /* inline errors only */ }
            }
        }
    }

    BackHandler(onBack = viewModel::onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgarthaTheme.colors.background)
            .systemBarsPadding()
    ) {
        ManualSheetContent(
            state = state,
            actions = ManualSheetActions(
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onUserNoteChanged = viewModel::onUserNoteChanged,
                onFramePrev = viewModel::onFramePrev,
                onFrameNext = viewModel::onFrameNext,
                onDeleteFrame = viewModel::onDeleteFrame,
                onSubmit = viewModel::onSubmit,
                onCancel = viewModel::onCancel,
            ),
        )
    }
}

@VisibleForTesting
internal data class ManualSheetActions(
    val onSpeciesSelected: (EggSpecies) -> Unit,
    val onOtherSpeciesChanged: (String) -> Unit,
    val onUserNoteChanged: (String) -> Unit,
    val onFramePrev: () -> Unit,
    val onFrameNext: () -> Unit,
    val onDeleteFrame: () -> Unit,
    val onSubmit: () -> Unit,
    val onCancel: () -> Unit,
)

@VisibleForTesting
@Composable
internal fun ManualSheetContent(
    state: ManualCaptureUiState,
    actions: ManualSheetActions,
) {
    val frame = state.frame ?: return

    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showCustomSpeciesDialog by remember { mutableStateOf(false) }
    var customSpeciesText by remember { mutableStateOf("") }
    val colors = AgarthaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val timeLabel = remember(frame.capturedAt) {
            DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(frame.capturedAt)
        }

        ScreenTopBar(
            title = "Label sample",
            metaText = stringResource(
                R.string.manual_frame_meta,
                state.frameIndexInQueue,
                state.queueSize,
                timeLabel,
            ),
            onBack = actions.onCancel
        )

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {

            // Cycles manual captures only — model detections need the other sheet.
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

            // Image preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag(VerifyTestTags.FRAME_PREVIEW)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.5.dp, colors.border, RoundedCornerShape(18.dp))
            ) {
                AsyncImage(
                    model = frame.jpegBytes,
                    contentDescription = null,
                    // Fit, matching FrameWithBoxes on the AI sheet. Crop overflowed a
                    // square 640x640 frame against this landscape container and the
                    // parent's clip cut the top and bottom off — the medtech was
                    // labelling a specimen they could only partly see.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Species Section
            Text(
                text = "SPECIES",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            )

            // Quick Chips
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickSpecies = listOf(
                    EggSpecies.ASCARIS to "Ascaris",
                    EggSpecies.TRICHURIS to "Trichuris",
                    EggSpecies.HOOKWORM to "Hookworm"
                )

                quickSpecies.forEach { (species, label) ->
                    val selected = state.selectedSpecies == species
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) colors.accentTint else Color.Transparent,
                                RoundedCornerShape(100.dp)
                            )
                            .border(
                                0.5.dp,
                                if (selected) colors.accent else colors.borderStrong,
                                RoundedCornerShape(100.dp)
                            )
                            .clickable { actions.onSpeciesSelected(species) }
                            .testTag(VerifyTestTags.speciesChip(species.name))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.accent else colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }

                // Other... chip
                val isOtherSelected = state.selectedSpecies != null &&
                    quickSpecies.none { it.first == state.selectedSpecies }
                Box(
                    modifier = Modifier
                        .background(
                            if (isOtherSelected) colors.accentTint else Color.Transparent,
                            RoundedCornerShape(100.dp)
                        )
                        .border(
                            0.5.dp,
                            if (isOtherSelected) colors.accent else colors.borderStrong,
                            RoundedCornerShape(100.dp)
                        )
                        .clickable {
                            showCustomSpeciesDialog = true
                        }
                        .testTag(VerifyTestTags.SPECIES_CHIP_OTHER)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isOtherSelected) state.selectedSpecies?.name ?: "Other..." else "Other...",
                        color = if (isOtherSelected) colors.accent else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Normal, // Not italic
                        letterSpacing = (-0.1).sp
                    )
                }
            }

            // Note section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .background(Color.Transparent, RoundedCornerShape(14.dp))
                    .border(0.5.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "NOTE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = state.userNote,
                    onValueChange = actions.onUserNoteChanged,
                    placeholder = {
                        Text("Add an observation about morphology, color, or staining.")
                    },
                    singleLine = false,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(VerifyTestTags.NOTE_FIELD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.borderStrong,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            SheetActionRow(
                SheetActionRowState(
                    primaryLabel = "Submit",
                    secondaryLabel = "Discard",
                    onPrimaryClick = actions.onSubmit,
                    onSecondaryClick = { showDiscardConfirm = true },
                    primaryLoading = state.isSubmitting,
                    primaryEnabled = state.canSubmit
                )
            )
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            shape = DialogShape,
            title = { Text("Discard this frame?") },
            text = { Text("This will remove the current frame from the verification queue.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
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
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.testTag(VerifyTestTags.DISCARD_DIALOG_DISMISS),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCustomSpeciesDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSpeciesDialog = false },
            shape = DialogShape,
            title = { Text("Custom Species") },
            text = {
                OutlinedTextField(
                    value = customSpeciesText,
                    onValueChange = { customSpeciesText = it },
                    label = { Text("Species name") },
                    singleLine = true,
                    modifier = Modifier.testTag(VerifyTestTags.CUSTOM_SPECIES_FIELD),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        actions.onSpeciesSelected(EggSpecies.OTHER)
                        actions.onOtherSpeciesChanged(customSpeciesText)
                        showCustomSpeciesDialog = false
                    },
                    modifier = Modifier.testTag(VerifyTestTags.CUSTOM_SPECIES_SAVE),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomSpeciesDialog = false },
                    modifier = Modifier.testTag(VerifyTestTags.CUSTOM_SPECIES_DISMISS),
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
