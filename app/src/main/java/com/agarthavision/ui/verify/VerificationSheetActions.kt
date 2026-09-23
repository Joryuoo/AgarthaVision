package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.ImageBox
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
    /** Adds a species the model did not account for. Opens holding one egg. */
    val onAddSpecies: () -> Unit,
    /** Removes an added species. Never offered on a prediction-backed row. */
    val onRemoveFinding: (Int) -> Unit,
    /** Eggs of an added species in this field, the model's own boxes included. */
    val onFieldTotalChanged: (Int, String) -> Unit,
    val onAddedSpeciesSelected: (Int, EggSpecies) -> Unit,
    val onAddedOtherSpeciesChanged: (Int, String) -> Unit,
    /**
     * Starts drawing a box: a redraw on the model box at this finding (`slot` null), or a
     * location for egg `slot` of an added species. Both optional — a box the medtech says is
     * misplaced is a complete answer without a redraw, and an added egg is a complete finding
     * with no box at all.
     */
    val onBeginDraw: (Int, Int?) -> Unit,
    /** The medtech accepted a drawn box, already in the model's centre-based image space. */
    val onBoxDrawn: (ImageBox) -> Unit,
    val onCancelDraw: () -> Unit,
    /**
     * Discards the box on egg `slot` of the added species at this finding, leaving the count
     * alone. Only reachable where a box exists, and never on a model box.
     */
    val onRemoveDrawnBox: (Int, Int) -> Unit,
)
