package com.agarthavision.ui.verify

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage

data class VerificationSheetActions(
    val onQ1Selected: (Boolean) -> Unit,
    val onQ2Selected: (Boolean) -> Unit,
    val onSpeciesSelected: (EggSpecies) -> Unit,
    val onOtherSpeciesChanged: (String) -> Unit,
    val onStageSelected: (EggStage) -> Unit,
    val onQ4Selected: (Boolean) -> Unit,
    val onDetectionPrev: () -> Unit,
    val onDetectionNext: () -> Unit,
    val onFramePrev: () -> Unit,
    val onFrameNext: () -> Unit,
    val onDeleteFrame: () -> Unit,
    val onToggleBoundingBoxes: () -> Unit,
    val onSubmit: () -> Unit,
    val onCancel: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onUserNoteChanged: (String) -> Unit,
)
