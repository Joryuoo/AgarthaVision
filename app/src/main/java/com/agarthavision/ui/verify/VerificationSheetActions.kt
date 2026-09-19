package com.agarthavision.ui.verify

import com.agarthavision.domain.model.EggSpecies

/**
 * Everything the Verification Screen can report back.
 *
 * There is no Q4 entry: Q4 is derived from the findings, not asked. There are no manual-capture
 * entries either - a frame captured with the inference container unreachable is verified through
 * the same Add Egg section as any other frame, so it needs no controls of its own.
 */
data class VerificationSheetActions(
    val onQ1Selected: (Boolean) -> Unit,
    val onQ2Selected: (Boolean) -> Unit,
    val onSpeciesConfirmed: (Boolean) -> Unit,
    val onSpeciesSelected: (EggSpecies) -> Unit,
    val onOtherSpeciesChanged: (String) -> Unit,
    val onDetectionPrev: () -> Unit,
    val onDetectionNext: () -> Unit,
    val onFramePrev: () -> Unit,
    val onFrameNext: () -> Unit,
    val onDeleteFrame: () -> Unit,
    val onToggleBoundingBoxes: () -> Unit,
    val onSubmit: () -> Unit,
    val onCancel: () -> Unit,
    val onUserNoteChanged: (String) -> Unit,
    /** Adds an egg the model never boxed. Opens holding one. */
    val onAddFinding: () -> Unit,
    /** Removes an added egg. Never offered on a prediction-backed row. */
    val onRemoveFinding: (Int) -> Unit,
    val onEggCountChanged: (Int, String) -> Unit,
    val onAddedSpeciesSelected: (Int, EggSpecies) -> Unit,
    val onAddedOtherSpeciesChanged: (Int, String) -> Unit,
)
