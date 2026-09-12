package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state surface for the verification flow.
 *
 * Tracks both the current frame under review and per-detection answers within
 * that frame. The verdict model (Q1→Q2→Q3 branching + frame-level Q4) is
 * documented in the ADR-004 summary in CONTEXT.md.
 *
 * @property isVisible whether the sheet is currently mounted.
 * @property frameIndexInQueue 1-based position of [frame] in `FlaggedFrameStore`.
 * @property queueSize total number of flagged frames in the store.
 * @property frame the frame currently being verified.
 * @property currentDetectionIndex which detection within [frame] is highlighted.
 * @property showBoundingBoxes toggle for the box overlay on the frame image.
 * @property findings what the medtech is asserting about this frame. The first
 *   `frame.predictions.size` entries are the model's boxes, in order; anything after them
 *   is a species the medtech added. An empty list on an AI frame is a clean field.
 * @property missedEgg frame-level Q4 answer — sets `samples.needs_reannotation`.
 * @property isSubmitting true while [SubmitVerificationUseCase] is in flight.
 * @property errorMessage submission failure message; surfaced inline.
 * @property canSubmit derived — true when every finding is complete and we're not already
 *   submitting. A clean field is the exception: it has no findings to complete, so the
 *   missed-egg answer carries the review on its own.
 */
data class VerificationUiState(
    val isVisible: Boolean = false,
    val frameIndexInQueue: Int = 0,
    val queueSize: Int = 0,
    val frame: FlaggedFrame? = null,
    val currentDetectionIndex: Int = 0,
    val showBoundingBoxes: Boolean = true,
    val findings: List<Finding> = emptyList(),
    val missedEgg: Boolean? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val userNote: String = "",
) {
    /**
     * A clean field — an AI capture the model returned no detections for — has no findings to
     * complete, and the old `answers.isNotEmpty()` gate made it permanently un-submittable.
     * It is a normal negative result and has to be recordable. But the missed-egg answer is
     * then the entire content of the review, so it must be given rather than defaulting
     * through as null.
     */
    val isCleanField: Boolean
        get() = frame?.source == FrameSource.MODEL && frame.predictions.isEmpty()

    val canSubmit: Boolean
        get() = when {
            isSubmitting -> false
            frame == null -> false
            isCleanField -> missedEgg != null && findings.all { it.isComplete }
            else -> findings.isNotEmpty() && findings.all { it.isComplete }
        }

    /** False on the first frame of the queue, or when the position is unknown. */
    val canGoPrev: Boolean
        get() = frameIndexInQueue > 1

    /** False on the last frame of the queue, or when the position is unknown. */
    val canGoNext: Boolean
        get() = frameIndexInQueue in 1 until queueSize
}

sealed interface VerificationEvent {
    data object Dismiss : VerificationEvent
    data class ShowError(val message: String?) : VerificationEvent
}

/**
 * State holder for the [VerificationSheet].
 *
 * Owns:
 * - **Frame-level navigation** across the [FlaggedFrameStore] queue
 *   ([onFramePrev], [onFrameNext], [onDeleteFrame]).
 * - **Detection-level navigation** within the current frame
 *   ([onDetectionPrev], [onDetectionNext]) and per-detection answers
 *   ([onQ1Selected], [onQ2Selected], [onSpeciesSelected], [onOtherSpeciesChanged]).
 * - **Frame-level Q4** ("did the model miss any eggs?", via [onQ4Selected]).
 * - **Submit** orchestration through [SubmitVerificationUseCase] — on success
 *   the frame is removed from the store; the verdict model (per ADR-004)
 *   persists every detection regardless of mix (false positives, wrong
 *   class, box-incorrect) so the dataset captures labeled corrections.
 *
 * The store collector (`init`) keeps `queueSize` + `frameIndexInQueue` in sync
 * as frames are added/removed by other surfaces (Capture toast/queue, delete).
 *
 * See CONTEXT.md.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val flaggedFrameStore: FlaggedFrameStore,
    private val submitVerificationUseCase: SubmitVerificationUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VerificationEvent>()
    val events: SharedFlow<VerificationEvent> = _events.asSharedFlow()

    private var currentFrame: FlaggedFrame? = null

    init {
        viewModelScope.launch {
            flaggedFrameStore.state.collect { frames ->
                val cycle = frames
                val frame = currentFrame
                _state.update { current ->
                    current.copy(
                        queueSize = cycle.size,
                        frameIndexInQueue = positionOf(frame, cycle, current.frameIndexInQueue),
                    )
                }
            }
        }
    }

    /**
     * The frames this sheet cycles through: all of them, both sources.
     *
     * There used to be two exclusions. Manual captures were skipped because they had their own
     * sheet and the host picked one from the frame it opened with, so paging onto a manual
     * frame rendered the wrong questions (86d4ab4tq merged the screens). Repeat-marked frames
     * were skipped because verifying a duplicate by accident was the hazard — duplicates are
     * deleted now rather than flagged (86d4ab4vm), so there is nothing left to skip.
     */
    private fun cycleFrames(): List<FlaggedFrame> = flaggedFrameStore.state.value

    /**
     * 1-based position of [frame] within [frames], or [OUT_OF_CYCLE] when the frame is
     * held but no longer part of the cycle — which happens the moment the medtech marks
     * the open frame as repeat. [fallback] covers the no-frame case only.
     *
     * Returning a sentinel rather than a stale number is deliberate: `canGoPrev` and
     * `canGoNext` both fail against it, so the frame buttons dim and the sheet stays put
     * instead of paging out from under a frame that has no position.
     *
     * Both [setFrame] and the store collector route through this so the counter and the
     * displayed frame cannot drift apart.
     */
    private fun positionOf(
        frame: FlaggedFrame?,
        frames: List<FlaggedFrame> = cycleFrames(),
        fallback: Int,
    ): Int {
        if (frame == null) return fallback
        val index = frames.indexOfSample(frame)
        return if (index >= 0) index + 1 else OUT_OF_CYCLE
    }

    fun setFrame(frame: FlaggedFrame) {
        currentFrame = frame
        _state.update {
            it.copy(
                isVisible = true,
                frame = frame,
                frameIndexInQueue = positionOf(frame, fallback = it.frameIndexInQueue),
                currentDetectionIndex = 0,
                findings = frame.initialFindings(),
                missedEgg = null,
                isSubmitting = false,
                errorMessage = null,
                userNote = "",
            )
        }
    }

    /**
     * Captures optional medtech free-form notes; persisted to `samples.user_note`
     * on submit and surfaced in SampleDetail (Track 2.14). Per ADR-005.
     */
    fun onUserNoteChanged(text: String) {
        _state.update { it.copy(userNote = text) }
    }

    fun onQ1Selected(isEgg: Boolean) {
        updateCurrentFinding { finding ->
            finding.copy(
                answers = finding.resetSpecies().copy(isEgg = isEgg, isBoxCorrect = null),
            )
        }
    }

    fun onQ2Selected(isBoxCorrect: Boolean) {
        updateCurrentFinding { finding ->
            finding.copy(
                answers = finding.resetSpecies().copy(
                    isEgg = finding.answers.isEgg,
                    isBoxCorrect = isBoxCorrect,
                ),
            )
        }
    }

    /**
     * The species answer a row starts from: the model's own class, or nothing when there is no
     * model output to borrow from.
     *
     * Changing an earlier answer clears the later ones, so that a stale species cannot survive
     * a change of mind about whether the box even holds an egg. It resets to the **pre-fill**
     * rather than to empty, because resetting to empty would throw away the model's answer the
     * moment the medtech answered Q1 — which is every time — and leave the auto-fill visible
     * only in the instant before it was useful. `speciesTouched` goes back to false with it:
     * whatever deliberate choice was made no longer applies to the question now being asked.
     */
    private fun Finding.resetSpecies(): VerificationAnswers = VerificationAnswers(
        species = prediction?.classLabel?.let(EggSpecies::fromClassLabel),
        speciesTouched = false,
    )

    /**
     * Records a deliberate species choice.
     *
     * [VerificationAnswers.speciesTouched] is set unconditionally, **including when the medtech
     * re-picks the value already showing**. Once the field is pre-filled from the model output
     * that re-pick is the only signal distinguishing "I agree" from "I never looked", and the
     * distinction matters because `detections` doubles as the retraining corpus.
     */
    fun onSpeciesSelected(species: EggSpecies) {
        updateCurrentAnswer {
            it.copy(species = species, otherSpeciesText = "", stage = null, speciesTouched = true)
        }
    }

    fun onOtherSpeciesChanged(text: String) {
        updateCurrentAnswer { it.copy(otherSpeciesText = text) }
    }

    fun onStageSelected(stage: EggStage) {
        updateCurrentAnswer { it.copy(stage = stage) }
    }

    /**
     * What the screen opens with, for each of the three shapes a frame can take.
     *
     * A manual capture gets exactly one finding with no prediction: there is no box, so the
     * isEgg and isBoxCorrect questions never render, and the medtech names a species and a
     * count directly. An AI frame with boxes gets one pre-filled finding per box. An AI frame
     * with none gets an empty list — a clean field, which is a real result and is submittable
     * on the missed-egg answer alone.
     */
    private fun FlaggedFrame.initialFindings(): List<Finding> = when {
        predictions.isNotEmpty() -> predictions.map { it.toPreFilledFinding() }
        source == FrameSource.MANUAL -> listOf(Finding())
        else -> emptyList()
    }

    /**
     * Seeds a box's answers from what the model called it.
     *
     * Model-assisted pre-labelling: the medtech corrects what is wrong instead of retyping
     * what is right. The firm rule is only that a pre-label must never be the sole source of
     * truth, which is what [VerificationAnswers.speciesTouched] is for — seeded false here, and
     * set true by any deliberate selection.
     *
     * **Only the species is pre-filled.** isEgg, isBoxCorrect and missedEgg are the remaining
     * active-judgment gates and are never answered on the medtech's behalf; pre-filling any of
     * them would let a frame reach CONFIRMED with no engagement at all. An unrecognised class
     * label pre-fills nothing rather than guessing OTHER, because OTHER carries a free-text
     * box the model cannot fill in.
     */
    private fun Prediction.toPreFilledFinding(): Finding =
        Finding(prediction = this).let { it.copy(answers = it.resetSpecies()) }

    /**
     * Appends a species the model never boxed.
     *
     * The new row has no prediction, which is the same shape a manual capture has: a human
     * assertion with no box. It is asked for a species, a stage and a count, and never for
     * the isEgg / isBoxCorrect questions, which are questions about a box.
     */
    fun onAddFinding() {
        _state.update { it.copy(findings = it.findings + Finding()) }
    }

    /**
     * Removes a species the medtech added.
     *
     * Refused on a prediction-backed row. You cannot delete a box the model produced - the
     * way to say it was wrong is to answer "not an egg", which persists it as a labelled
     * FALSE_POSITIVE (constraint C8). Silently refusing rather than throwing because the UI
     * does not offer the affordance on those rows in the first place; this is the backstop.
     */
    fun onRemoveFinding(index: Int) {
        _state.update { current ->
            val boxCount = current.frame?.predictions?.size ?: 0
            if (index < boxCount || index !in current.findings.indices) {
                current
            } else {
                current.copy(findings = current.findings.filterIndexed { i, _ -> i != index })
            }
        }
    }

    /** Eggs of this species and stage the medtech counted in the field. */
    fun onEggCountChanged(index: Int, text: String) {
        val parsed = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.coerceAtLeast(0)
        updateAnswerAt(index) { it.copy(eggCount = parsed) }
    }

    fun onAddedSpeciesSelected(index: Int, species: EggSpecies) {
        updateAnswerAt(index) {
            it.copy(species = species, otherSpeciesText = "", stage = null, speciesTouched = true)
        }
    }

    fun onAddedOtherSpeciesChanged(index: Int, text: String) {
        updateAnswerAt(index) { it.copy(otherSpeciesText = text) }
    }

    fun onAddedStageSelected(index: Int, stage: EggStage) {
        updateAnswerAt(index) { it.copy(stage = stage) }
    }

    fun onQ4Selected(missedEgg: Boolean) {
        _state.update { it.copy(missedEgg = missedEgg) }
    }

    fun onDetectionPrev() {
        _state.update { current ->
            current.copy(currentDetectionIndex = (current.currentDetectionIndex - 1).coerceAtLeast(0))
        }
    }

    fun onDetectionNext() {
        _state.update { current ->
            val maxIndex = (current.frame?.predictions?.size ?: 1) - 1
            current.copy(currentDetectionIndex = (current.currentDetectionIndex + 1).coerceAtMost(maxIndex))
        }
    }

    fun onFramePrev() {
        val frames = cycleFrames()
        val current = currentFrame ?: return
        val idx = frames.indexOfSample(current)
        if (idx <= 0) return
        setFrame(frames[idx - 1])
    }

    fun onFrameNext() {
        val frames = cycleFrames()
        val current = currentFrame ?: return
        val idx = frames.indexOfSample(current)
        if (idx < 0 || idx >= frames.size - 1) return
        setFrame(frames[idx + 1])
    }

    fun onDeleteFrame() {
        val frames = cycleFrames()
        val current = currentFrame ?: return
        val idx = frames.indexOfSample(current)
        // Pick replacement BEFORE removal: prefer the next frame, fall back to previous.
        val nextFrame = frames.getOrNull(idx + 1) ?: frames.getOrNull(idx - 1)
        viewModelScope.launch {
            flaggedFrameStore.remove(current)
            if (nextFrame != null) {
                setFrame(nextFrame)
            } else {
                currentFrame = null
                _events.emit(VerificationEvent.Dismiss)
            }
        }
    }

    fun onToggleBoundingBoxes() {
        _state.update { it.copy(showBoundingBoxes = !it.showBoundingBoxes) }
    }

    fun onSubmit() {
        val frame = currentFrame ?: return
        val snapshot = _state.value
        if (!snapshot.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            submitVerificationUseCase(
                frame = frame,
                findings = snapshot.findings,
                missedEgg = snapshot.missedEgg,
                userNote = snapshot.userNote,
            ).fold(
                onSuccess = {
                    currentFrame = null
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(VerificationEvent.Dismiss)
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = throwable.message) }
                    _events.emit(VerificationEvent.ShowError(throwable.message))
                },
            )
        }
    }

    fun onCancel() {
        viewModelScope.launch { _events.emit(VerificationEvent.Dismiss) }
    }

    /**
     * Position of [frame] by sample id. Deliberately not `indexOf`: matching on identity
     * rather than equality keeps navigation working regardless of how `FlaggedFrame`
     * defines equals, which covers mutable fields such as the answers already given.
     */
    private fun List<FlaggedFrame>.indexOfSample(frame: FlaggedFrame): Int =
        indexOfFirst { it.sampleId == frame.sampleId }

    private companion object {
        /** [VerificationUiState.frameIndexInQueue] when the open frame left the cycle. */
        const val OUT_OF_CYCLE = 0
    }

    private fun updateCurrentAnswer(transform: (VerificationAnswers) -> VerificationAnswers) {
        updateAnswerAt(_state.value.currentDetectionIndex, transform)
    }

    private fun updateCurrentFinding(transform: (Finding) -> Finding) {
        val index = _state.value.currentDetectionIndex
        _state.update { current ->
            val updated = current.findings.toMutableList()
            if (index in updated.indices) updated[index] = transform(updated[index])
            current.copy(findings = updated)
        }
    }

    private fun updateAnswerAt(index: Int, transform: (VerificationAnswers) -> VerificationAnswers) {
        _state.update { current ->
            val updated = current.findings.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(answers = transform(updated[index].answers))
            }
            current.copy(findings = updated)
        }
    }
}
