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
import androidx.compose.material.icons.filled.Close
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
import com.komoui.components.Input
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: ManualCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(frame) {
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = viewModel::onCancel,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color(15, 23, 42, (0.32f * 255).toInt()),
        dragHandle = null, // We build our own
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
            ManualSheetContent(
                state = state,
                actions = ManualSheetActions(
                    onSpeciesSelected = viewModel::onSpeciesSelected,
                    onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                    onUserNoteChanged = viewModel::onUserNoteChanged,
                    onToggleRepeat = viewModel::onToggleRepeat,
                    onDeleteFrame = viewModel::onDeleteFrame,
                    onSubmit = viewModel::onSubmit,
                    onCancel = viewModel::onCancel,
                ),
            )
        }
    }
}

private data class ManualSheetActions(
    val onSpeciesSelected: (EggSpecies) -> Unit,
    val onOtherSpeciesChanged: (String) -> Unit,
    val onUserNoteChanged: (String) -> Unit,
    val onToggleRepeat: () -> Unit,
    val onDeleteFrame: () -> Unit,
    val onSubmit: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
private fun ManualSheetContent(
    state: ManualCaptureUiState,
    actions: ManualSheetActions,
) {
    val frame = state.frame ?: return
    val timeLabel = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }

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
            title = "Label sample",
            metaText = "MANUAL · ${frame.capturedAt.toEpochMilli().toString().takeLast(3)}",
            isManual = true
        )

        // Image preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(0.5.dp, Color(0, 0, 0, (0.08f * 255).toInt()), RoundedCornerShape(18.dp))
        ) {
            AsyncImage(
                model = frame.jpegBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Manual Tag (Top Right)
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(120, 120, 128, (0.92f * 255).toInt()), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close, // Using Close for now, will replace with proper icon if needed
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text("Manual capture", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    text = "$timeLabel · ${frame.imageWidth}x${frame.imageHeight} · Manual",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // Species Section
        Text(
            text = "SPECIES",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = SheetMuted,
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
                        .background(if (selected) SheetBrand else SheetSurface, RoundedCornerShape(100.dp))
                        .border(0.5.dp, if (selected) SheetBrand else SheetHairline, RoundedCornerShape(100.dp))
                        .clickable { actions.onSpeciesSelected(species) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else Color(0xFF3C3C43),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.1).sp
                    )
                }
            }

            // Other... chip (mocking behavior for now, just sets it to another species or we'd open a picker)
            val isOtherSelected = state.selectedSpecies != null && quickSpecies.none { it.first == state.selectedSpecies }
            Box(
                modifier = Modifier
                    .background(if (isOtherSelected) SheetBrand else SheetSurface, RoundedCornerShape(100.dp))
                    .border(0.5.dp, if (isOtherSelected) SheetBrand else SheetHairline, RoundedCornerShape(100.dp))
                    .clickable { 
                        // TODO: Open full species picker.
                        actions.onSpeciesSelected(EggSpecies.OTHER) 
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isOtherSelected) state.selectedSpecies?.name ?: "Other..." else "Other...",
                    color = if (isOtherSelected) Color.White else Color(0xFF3C3C43),
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
                .background(SheetSurface, RoundedCornerShape(14.dp))
                .border(0.5.dp, SheetHairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "NOTE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = SheetMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Input(
                value = state.userNote,
                onValueChange = actions.onUserNoteChanged,
                placeholder = "Add an observation about morphology, color, or staining.",
                singleLine = false,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SheetActionRow(
            primaryLabel = "Save label",
            secondaryLabel = "Discard",
            onPrimaryClick = actions.onSubmit,
            onSecondaryClick = actions.onCancel,
            primaryLoading = state.isSubmitting,
            primaryEnabled = state.canSubmit
        )
    }
}
