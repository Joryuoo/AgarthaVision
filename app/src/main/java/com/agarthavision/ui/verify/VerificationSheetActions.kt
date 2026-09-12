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
    val onUserNoteChanged: (String) -> Unit,
    /** Appends a species the model never boxed. */
    val onAddFinding: () -> Unit,
    /** Removes an added species. Never offered on a prediction-backed row. */
    val onRemoveFinding: (Int) -> Unit,
    val onEggCountChanged: (Int, String) -> Unit,
    val onAddedSpeciesSelected: (Int, EggSpecies) -> Unit,
    val onAddedOtherSpeciesChanged: (Int, String) -> Unit,
    val onAddedStageSelected: (Int, EggStage) -> Unit,
)
