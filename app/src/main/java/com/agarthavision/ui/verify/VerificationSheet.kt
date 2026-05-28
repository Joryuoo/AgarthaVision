@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.agarthavision.ui.components.glassChrome
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
                is VerificationEvent.ShowError -> { /* inline */ }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = viewModel::onCancel,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color(15, 23, 42, (0.32f * 255).toInt()),
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassChrome(
                    backgroundColor = Color(248, 248, 250, (0.96f * 255).toInt())
                )
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .heightIn(max = screenHeightDp * 0.95f)
                .navigationBarsPadding()
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
}

@Composable
private fun VerificationSheetContent(
    state: VerificationUiState,
    actions: VerificationSheetActions,
) {
    val frame = state.frame ?: return
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
            .padding(top = 8.dp, start = 22.dp, end = 22.dp, bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SheetDragHandle()
        }

        SheetTitleRow(
            title = "Verify detection",
            metaText = "FRAME ${frame.capturedAt.toEpochMilli().toString().takeLast(3)}",
            isManual = false
        )

        // Image Preview (Height 170px for verification)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(0.5.dp, Color(0, 0, 0, (0.08f * 255).toInt()), RoundedCornerShape(18.dp))
        ) {
            AsyncImage(
                model = frame.jpegBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bounding Box
            if (currentPrediction != null) {
                // Approximate representation based on absolute positioned bounding box 40/38, 28/26 width height
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.28f)
                            .fillMaxHeight(0.26f)
                            .border(2.dp, SheetWarning, RoundedCornerShape(6.dp))
                    )
                }
            }

            // AI Tag (Top Right)
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(175, 82, 222, (0.96f * 255).toInt()), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Sparkle Icon (using generic vector, assuming we have one or just text)
                Text(
                    text = "✨ AI · ${"%.0f".format(confidence * 100)}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }

            // Frame Tag (Bottom Left)
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0, 0, 0, (0.6f * 255).toInt()), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$timeLabel · $speciesName",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // Model Prediction Summary Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(SheetSurface, RoundedCornerShape(14.dp))
                .border(0.5.dp, SheetHairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MODEL PREDICTED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SheetMuted,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = speciesName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = SheetBrand,
                    letterSpacing = (-0.2).sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(Color(120, 120, 128, (0.16f * 255).toInt()), RoundedCornerShape(100.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(confidence)
                            .background(Brush.horizontalGradient(listOf(SheetWarning, SheetSuccess)), RoundedCornerShape(100.dp))
                    )
                }
                Text(
                    text = "${"%.0f".format(confidence * 100)}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SheetInk,
                    letterSpacing = (-0.2).sp
                )
            }
        }

        // Yes/No Questions Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(SheetSurface, RoundedCornerShape(14.dp))
                .border(0.5.dp, SheetHairline, RoundedCornerShape(14.dp))
        ) {
            // Q1
            QuestionRow(
                text = "Is there a parasitic egg in this bounding box?",
                isYes = currentAnswers?.isEgg,
                onSelect = actions.onQ1Selected,
                isNeutralNo = false,
                hasBottomBorder = true
            )
            // Q2
            QuestionRow(
                text = "Is the bounding box correctly placed?",
                isYes = currentAnswers?.isBoxCorrect,
                onSelect = actions.onQ2Selected,
                isNeutralNo = false,
                hasBottomBorder = true
            )
            // Q3 (formerly Q4: Did the model miss any eggs)
            QuestionRow(
                text = "Did the model miss any eggs in this frame?",
                isYes = state.missedEgg,
                onSelect = actions.onQ4Selected,
                isNeutralNo = true,
                hasBottomBorder = false
            )
        }

        // Species Row Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(SheetSurface, RoundedCornerShape(14.dp))
                .border(0.5.dp, SheetHairline, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        // Mocking dropdown selection by cycling species for now, 
                        // as SpeciesDropdown component logic was inlined, 
                        // or we would use a sub-sheet picker.
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Species",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SheetInk
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val currentSelectionText = currentAnswers?.species?.name 
                        ?: currentAnswers?.otherSpeciesText 
                        ?: speciesName
                        
                    Text(
                        text = currentSelectionText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        color = SheetBrand
                    )
                    // Chevron icon placeholder (we'll just use text or omit for now if we don't have the icon, but let's assume we have it or use a default)
                    Text("▾", color = SheetBrand, fontSize = 14.sp)
                }
            }
        }

        SheetActionRow(
            primaryLabel = "Submit",
            secondaryLabel = "Cancel",
            onPrimaryClick = actions.onSubmit,
            onSecondaryClick = actions.onCancel,
            primaryLoading = state.isSubmitting,
            primaryEnabled = state.canSubmit
        )
    }
}

@Composable
fun QuestionRow(
    text: String,
    isYes: Boolean?,
    onSelect: (Boolean) -> Unit,
    isNeutralNo: Boolean,
    hasBottomBorder: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasBottomBorder) Modifier.border(
                    width = 0.5.dp,
                    color = SheetHairlineSoft,
                    shape = RoundedCornerShape(0.dp)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = SheetInk,
            lineHeight = 19.6.sp,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.padding(bottom = 9.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YesNoButton(
                label = "Yes",
                isSelected = isYes == true,
                isNeutral = false, // Yes is always brand if selected
                onClick = { onSelect(true) }
            )
            YesNoButton(
                label = "No",
                isSelected = isYes == false,
                isNeutral = isNeutralNo,
                onClick = { onSelect(false) }
            )
        }
    }
}

@Composable
fun YesNoButton(
    label: String,
    isSelected: Boolean,
    isNeutral: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        if (isNeutral) SheetMuted else SheetBrand
    } else {
        Color(120, 120, 128, (0.14f * 255).toInt())
    }
    
    val fg = if (isSelected) Color.White else SheetInk
    
    val border = if (isSelected) {
        if (isNeutral) SheetMuted else SheetBrand
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(0.5.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.1).sp
        )
    }
}
