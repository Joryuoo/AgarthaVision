package com.agarthavision.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.totalsAreConsistent
import com.agarthavision.domain.usecase.verify.unboxedCountOf
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
import com.agarthavision.domain.usecase.verify.SubmitVerificationUseCase
import com.agarthavision.domain.usecase.verify.VerificationTarget
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /**
     * Where the frame's image can be loaded from, when it did not come with its own bytes.
     *
     * Null on the capture path, where the frame carries the JPEG it was just taken from.
     */
    val imageSource: SampleImageSource? = null,
    val currentDetectionIndex: Int = 0,
    val showBoundingBoxes: Boolean = true,
    val findings: List<Finding> = emptyList(),
    /**
     * What the medtech is drawing a box for, or null when nobody is drawing.
     *
     * An address rather than a boolean, because one gesture serves two jobs — replacing a box
     * the model got wrong, and locating an egg it never boxed — and the frame can only host one
     * at a time.
     */
    val drawTarget: DrawTarget? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val userNote: String = "",
    /**
     * Species already recorded on this device that match what is being typed into a free-text
     * "Other species" field, with the field they were fetched for and the text they answer.
     *
     * All three, because the screen has **two** free-text fields — Current Detection and every
     * added species — and only one is ever being typed into. Carrying the target and the query
     * alongside the names lets [suggestionsFor] *derive* whether a list still belongs where it
     * is about to render, instead of the view model having to remember to clear it on every
     * path that moves the medtech elsewhere. A suggestion list rendered under the wrong row
     * would be worse than no suggestions at all: it invites a tap that writes one row's species
     * into another.
     */
    val speciesSuggestions: List<String> = emptyList(),
    val speciesSuggestionTarget: SuggestionTarget? = null,
    val speciesSuggestionQuery: String = "",
) {
    /**
     * The suggestions to show under [target]'s free-text field, given what it currently holds.
     *
     * Empty unless the last lookup was for this field *and* for this exact text, so a stale list
     * cannot outlive the keystroke that produced it.
     */
    fun suggestionsFor(target: SuggestionTarget, query: String): List<String> =
        if (target == speciesSuggestionTarget && query == speciesSuggestionQuery) {
            speciesSuggestions
        } else {
            emptyList()
        }

    /**
     * Q4 — "did the model miss any eggs in this frame?" — **derived, never asked.**
     *
     * The medtech already answers it by acting: recording an egg the model never boxed *is*
     * saying the model missed one, and taking that egg away again says it did not. Asking a
     * second time is asking them to restate what the screen can already see, and lets the two
     * disagree.
     *
     * Read off the findings with no prediction behind them rather than by comparing totals
     * against `frame.predictions.size`. The two agree on every transition the spec describes,
     * and differ in one case the totals get wrong: a frame where the medtech rejects one of the
     * model's boxes *and* adds an egg it missed nets out to the same count while both things
     * are true.
     *
     * Null on a frame with no model output. There is no model claim there to have missed
     * anything, so the question does not apply — which is also why it is never rendered for one.
     * `samples.needs_reannotation` is nullable for exactly this.
     */
    val missedEgg: Boolean?
        get() = when {
            frame == null -> null
            frame.source == FrameSource.MANUAL -> null
            else -> findings.any {
                it.prediction == null && findings.unboxedCountOf(it.answers.speciesLabel) > 0
            }
        }

    val isManual: Boolean
        get() = frame?.source == FrameSource.MANUAL

    /** An AI capture the model returned no detections for: a real negative result. */
    val isCleanField: Boolean
        get() = frame?.source == FrameSource.MODEL && frame.predictions.isEmpty()

    /**
     * Submit unlocks when every row the medtech is asserting is finished.
     *
     * **An empty list qualifies, and that is the point.** A clean field the medtech agrees with,
     * and a No-Model-Output field where they saw nothing, are both real results with nothing to
     * fill in; the screen's governing principle is that a medtech whose model was right submits
     * without tapping anything. The gate this replaced demanded a missed-egg answer on a clean
     * field and an explicit no-detection assertion on a manual one, both of which were a tap
     * asking the medtech to restate the absence of work.
     *
     * The one thing it does hold for is a contradiction: an added species whose typed total is
     * lower than the eggs the frame already accounts for — the boxes kept, plus the boxes the
     * medtech drew themselves. Submitting that would write fewer slots than there are drawn
     * boxes and quietly lose hand-placed geometry, so it is refused rather than clamped as they
     * type. See `List<Finding>.totalsAreConsistent`.
     *
     * C7 is unaffected: nothing reaches `samples` except through `SubmitVerificationUseCase`,
     * and a row nobody touched carries `species_touched = false` into the corpus, so an
     * unopposed model answer stays distinguishable from a confirmed one.
     */
    val canSubmit: Boolean
        get() = when {
            isSubmitting -> false
            frame == null -> false
            else -> findings.all { it.isComplete } && findings.totalsAreConsistent()
        }

    /** True while a box is being drawn, which is what dims every existing box on the frame. */
    val isDrawing: Boolean
        get() = drawTarget != null

    /** False on the first frame of the queue, or when the position is unknown. */
    val canGoPrev: Boolean
        get() = frameIndexInQueue > 1

    /** False on the last frame of the queue, or when the position is unknown. */
    val canGoNext: Boolean
        get() = frameIndexInQueue in 1 until queueSize
}

/**
 * What a box being drawn is for.
 *
 * @property findingIndex the row in `findings` the box belongs to.
 * @property slot which egg of an added species, when [findingIndex] names an added row. Null on
 *   a prediction-backed row, where there is exactly one box and drawing replaces it. The two
 *   cases commit differently — a replacement latches `boxReplaced` and holds Q2 at "No", a
 *   location has no model claim to contradict — so the address has to carry which one it is
 *   rather than leaving `onBoxDrawn` to guess from the row.
 */
data class DrawTarget(val findingIndex: Int, val slot: Int? = null)

/**
 * Which free-text species field a suggestion list was fetched for.
 *
 * Not an `Int?` index with null meaning "the current detection". The two fields are different
 * things — one names the egg in a box the model drew, the other names a species the model never
 * boxed — and a nullable index makes "the current detection" and "some added row" the same type,
 * which is how a list ends up rendering under the wrong one.
 */
sealed interface SuggestionTarget {
    /** The "Other species" field under Q3, for the box currently on screen. */
    data object CurrentDetection : SuggestionTarget

    /** The "Other species" field on the added species at [index]. */
    data class AddedFinding(val index: Int) : SuggestionTarget
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
    private val searchSpeciesSuggestions: SearchSpeciesSuggestionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VerificationEvent>()
    val events: SharedFlow<VerificationEvent> = _events.asSharedFlow()

    private var currentFrame: FlaggedFrame? = null

    /** The in-flight suggestion lookup, cancelled by the next keystroke. */
    private var suggestionJob: Job? = null

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

    /**
     * Opens [frame] for review.
     *
     * [prior] carries what the medtech already said, when this sample has been verified before.
     * A verified sample stays editable, and reopening it with a blank questionnaire would make
     * every edit a full re-review - and would silently discard answers by resubmitting defaults
     * over them. Absent it, the frame seeds from its own shape instead.
     */
    fun setFrame(frame: FlaggedFrame, prior: VerificationTarget? = null) {
        currentFrame = frame
        _state.update {
            it.copy(
                isVisible = true,
                frame = frame,
                imageSource = prior?.imageSource,
                frameIndexInQueue = positionOf(frame, fallback = it.frameIndexInQueue),
                currentDetectionIndex = 0,
                findings = prior?.findings?.takeIf { findings -> findings.isNotEmpty() }
                    ?: frame.initialFindings(),
                drawTarget = null,
                isSubmitting = false,
                errorMessage = null,
                userNote = prior?.userNote.orEmpty(),
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

    /**
     * Tapping the answer a row already holds does nothing.
     *
     * Not a nicety. Every row opens pre-filled from model output, and changing an earlier answer
     * clears the later ones — so without this, re-affirming a correct "Yes" would wipe the
     * pre-filled species underneath it and hand the medtech back the work the pre-fill saved
     * them. A tap that asserts what is already asserted has changed nothing, so nothing
     * downstream of it has gone stale.
     */
    fun onQ1Selected(isEgg: Boolean) {
        updateCurrentAnswer {
            if (it.isEgg == isEgg) it else it.clearSpecies().copy(isEgg = isEgg, isBoxCorrect = null)
        }
    }

    /**
     * **A replaced box cannot be told it was placed correctly.**
     *
     * Once the medtech has redrawn a box, "yes the model placed it right" is false, and it stays
     * false — the model did put the box in the wrong place, and a human fixing it does not undo
     * that. Letting Q2 flip back would leave a detection claiming correct localisation while
     * carrying the human's geometry, which is exactly the label the drawing feature exists to
     * produce. Refused silently, because the screen does not offer the affordance on a replaced
     * row; this is the backstop.
     */
    fun onQ2Selected(isBoxCorrect: Boolean) {
        updateCurrentAnswer {
            when {
                it.boxReplaced && isBoxCorrect -> it
                it.isBoxCorrect == isBoxCorrect -> it
                else -> it.clearSpecies().copy(isEgg = it.isEgg, isBoxCorrect = isBoxCorrect)
            }
        }
    }

    /**
     * "Is this egg <model's species>?" Yes records the model's species as the medtech's answer
     * in the same step; no clears it so the picker can take over (86d4auj84).
     *
     * The suggestion is read from **this finding's own prediction**, not from
     * `frame.predictions[currentDetectionIndex]`. The two agreed while the answer list was one
     * entry per box; once a medtech can append a species the model never boxed, the list is
     * longer than `predictions` and the index would run off the end - or, worse, land on a
     * different box and confirm a species nothing suggested.
     */
    fun onSpeciesConfirmed(confirmed: Boolean) {
        updateCurrentFinding { finding ->
            val suggested = finding.prediction?.classLabel?.let(EggSpecies::fromClassLabel)
            finding.copy(
                answers = finding.answers.copy(
                    speciesConfirmed = confirmed,
                    species = if (confirmed) suggested else null,
                    otherSpeciesText = "",
                    // A yes is a deliberate assertion, not a silent pass-through: the medtech
                    // read the model's answer and agreed with it.
                    speciesTouched = confirmed,
                ),
            )
        }
    }

    /**
     * Drops the species half of a row's answers.
     *
     * Changing an earlier answer clears the later ones, so a stale species cannot survive a
     * change of mind about whether the box even holds an egg. This clears to **empty** rather
     * than back to the model's class: 86d4auj84 replaced the silent pre-fill with an explicit
     * "is this egg <species>?", and re-seeding here would answer that question on the
     * medtech's behalf - the one thing the confirm step exists to stop.
     *
     * [VerificationAnswers.speciesConfirmed] and [VerificationAnswers.speciesTouched] go with
     * it: whatever was asserted no longer applies to the question now being asked.
     */
    private fun VerificationAnswers.clearSpecies(): VerificationAnswers = VerificationAnswers(
        fieldTotal = fieldTotal,
        // Geometry is not an answer to any of the questions being cleared. A medtech who redrew
        // a box and then changed their mind about the species has not un-drawn the box, and
        // dropping it here would quietly restore the model's own geometry under them. The same
        // holds for the boxes on an added species: the eggs are still where they were put.
        drawnBox = drawnBox,
        drawnBoxes = drawnBoxes,
        boxReplaced = boxReplaced,
    )

    /**
     * Records a deliberate species choice.
     *
     * [VerificationAnswers.speciesTouched] is set unconditionally. Under the confirm-first flow
     * this is reached only after the medtech has said the model was wrong (or there was nothing
     * to confirm), so it is a human judgement by construction — and the flag stays because
     * `detections` doubles as the retraining corpus, where a species with no human behind it
     * must never be indistinguishable from one with.
     */
    fun onSpeciesSelected(species: EggSpecies) {
        updateCurrentAnswer {
            it.copy(species = species, otherSpeciesText = "", speciesTouched = true)
        }
    }

    fun onOtherSpeciesChanged(text: String) {
        updateCurrentAnswer { it.copy(otherSpeciesText = text) }
        searchSuggestions(SuggestionTarget.CurrentDetection, text)
    }

    /**
     * What the screen opens with, for each of the shapes a frame can take.
     *
     * **Every answer is pre-filled from model output.** A box the model drew opens as "yes there
     * is an egg", "yes the box is placed right", and "yes it is the species the model named" —
     * so a medtech whose model was right submits without tapping anything, and answering is only
     * required where the model was wrong. That is the whole economics of the screen: ten fields
     * a smear, most of them the model gets right.
     *
     * **[VerificationAnswers.speciesTouched] stays false on every seeded row**, and that is the
     * safeguard rather than an oversight. A seeded species is the model's own answer sitting in
     * the slot a human answer is read from; the flag is what keeps "a human did not object"
     * distinguishable from "a human confirmed this" in a table that doubles as the retraining
     * corpus. The moment the medtech confirms or changes it, it flips.
     *
     * A model class this app cannot map to an [EggSpecies] seeds **nothing** for the species
     * question — guessing OTHER would be wrong, because OTHER carries a free-text box only a
     * human can fill, so the row would look answered while being incomplete. The chain offers
     * the picker directly instead, and the row stays incomplete until a species is chosen.
     *
     * A frame with no boxes — a clean field, or one captured with the container unreachable —
     * opens with nothing. There is no box to ask a question about; what it gets is the Add Egg
     * section, which is always present.
     */
    private fun FlaggedFrame.initialFindings(): List<Finding> = predictions.map { prediction ->
        val suggested = EggSpecies.fromClassLabel(prediction.classLabel)
        Finding(
            prediction = prediction,
            answers = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                speciesConfirmed = if (suggested != null) true else null,
                species = suggested,
                speciesTouched = false,
            ),
        )
    }

    /**
     * Adds an egg the model never boxed.
     *
     * The new row has no prediction, which is the same shape a frame captured with the container
     * unreachable has: a human assertion with no box. It is asked for a species and a count, and
     * never for the isEgg / isBoxCorrect questions, which are questions about a box.
     *
     * **It opens holding one egg**, because the button says Add Egg and one egg is what the
     * medtech just said they saw. The count stays editable: a field with five of the same species
     * is one row reading 5, not five taps. An added egg needs no bounding box to be a complete
     * finding — `detections.bbox_*` is nullable precisely for this
     * (`0007_detection_bbox_nullable.sql`), and drawing one is optional (PB-14).
     */
    fun onAddSpecies() {
        _state.update {
            it.copy(findings = it.findings + Finding(answers = VerificationAnswers(fieldTotal = 1)))
        }
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

    /**
     * Eggs of this species the medtech counted in the field, **the model's boxes included**.
     *
     * Taken as typed. It is not clamped up to the floor here, because the floor is often above
     * the first digit of the number being typed — heading for 23 against nine boxed eggs, a
     * clamp would snap "2" to 9 and the 3 would land on the wrong number. A total below the
     * floor holds submit and says why instead; see `List<Finding>.totalsAreConsistent`.
     */
    fun onFieldTotalChanged(index: Int, text: String) {
        val parsed = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.coerceAtLeast(0)
        updateAnswerAt(index) { it.copy(fieldTotal = parsed) }
    }

    /**
     * Names the species on an added card, merging it into an existing card for the same species.
     *
     * The merge is not tidiness. `sample_species_findings` is unique on `(sample_id, species)`,
     * the detection ids derive from the species, and the reopen path rebuilds one card per
     * species — two cards naming one species have nowhere to be stored separately and would come
     * back as one anyway. The card that already held the species survives; the one just renamed
     * into it adds its total and hands over its drawn boxes, so nothing the medtech placed is
     * dropped by renaming a card into a collision.
     */
    fun onAddedSpeciesSelected(index: Int, species: EggSpecies) {
        _state.update { current ->
            val named = current.findings.getOrNull(index)?.answers
                ?.copy(species = species, otherSpeciesText = "", speciesTouched = true)
                ?: return@update current
            val twinIndex = current.findings.indexOfFirst { other ->
                other.prediction == null && other.answers.speciesLabel == named.speciesLabel
            }
            if (twinIndex == -1 || twinIndex == index) {
                current.copy(
                    findings = current.findings.mapIndexed { i, finding ->
                        if (i == index) finding.copy(answers = named) else finding
                    },
                )
            } else {
                val twin = current.findings[twinIndex].answers
                val merged = twin.copy(
                    fieldTotal = (twin.fieldTotal ?: 0) + (named.fieldTotal ?: 0),
                    drawnBoxes = twin.drawnBoxes + named.drawnBoxes,
                    speciesTouched = true,
                )
                current.copy(
                    findings = current.findings
                        .mapIndexed { i, finding ->
                            if (i == twinIndex) finding.copy(answers = merged) else finding
                        }
                        .filterIndexed { i, _ -> i != index },
                )
            }
        }
    }

    fun onAddedOtherSpeciesChanged(index: Int, text: String) {
        updateAnswerAt(index) { it.copy(otherSpeciesText = text) }
        searchSuggestions(SuggestionTarget.AddedFinding(index), text)
    }

    /**
     * Looks up species already on this device for whichever free-text field is being typed into.
     *
     * One job, cancelled on every keystroke, so a burst of typing costs one query at the end of
     * it rather than one per character. The debounce is also what keeps the results honest: an
     * in-flight lookup for "asc" must never land after the one for "ascar" and put the wider
     * list back on screen.
     *
     * A failed lookup shows nothing rather than leaving the previous names up. The index is a
     * local table read, so a failure here is not a transient offline blip that is worth riding
     * out - it means the query did not answer, and stale names under a live field would be a
     * suggestion the device cannot stand behind.
     *
     * Nothing is written from here. A suggestion is a convenience: the medtech's typing is the
     * answer, and a name absent from the index has to keep working, since that is the only way a
     * species new to this device ever enters the corpus.
     */
    private fun searchSuggestions(target: SuggestionTarget, query: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            val names = searchSpeciesSuggestions(query).getOrDefault(emptyList())
            _state.update {
                it.copy(
                    speciesSuggestions = names,
                    speciesSuggestionTarget = target,
                    speciesSuggestionQuery = query,
                )
            }
        }
    }

    // The manual species checklist is gone, and with it onManualNoDetectionSelected,
    // onManualSpeciesToggled, onManualCountChanged, onManualOtherNameChanged and
    // updateFindingForSpecies. A frame captured with the inference container unreachable is no
    // longer a different kind of screen with its own controls: it is a frame whose Model Output
    // section says the container never answered, verified through the same always-present Add
    // Egg section every other frame uses. One path, so there is one place for it to be wrong.
    //
    // onQ4Selected went with them. Q4 is derived from the findings now - see
    // VerificationUiState.missedEgg - because recording an egg the model never boxed already
    // says the model missed one, and asking again lets the two disagree.

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

    /**
     * Starts drawing a box for the finding at [index].
     *
     * Two call sites, one capability: replacing the model's box after Q2 is answered "No", and
     * giving an added egg a box. Both are optional — answering "No" without redrawing is a
     * complete, valid answer that records a localisation error, and an added egg with no box is
     * a complete finding whose drawing may be deferred to the Sample Data Screen entirely.
     */
    fun onBeginDraw(findingIndex: Int, slot: Int? = null) {
        val findings = _state.value.findings
        val finding = findings.getOrNull(findingIndex) ?: return
        // A slot may sit one past the drawn boxes (the next egg to locate) but never beyond the
        // eggs that species actually claims, and a prediction-backed row has no slots at all.
        val slotIsValid = when {
            slot == null -> finding.prediction != null
            finding.prediction != null -> false
            else -> slot in 0 until findings.unboxedCountOf(finding.answers.speciesLabel)
        }
        if (slotIsValid) {
            _state.update { it.copy(drawTarget = DrawTarget(findingIndex, slot)) }
        }
    }

    fun onCancelDraw() {
        _state.update { it.copy(drawTarget = null) }
    }

    /**
     * Records a box the medtech drew.
     *
     * The geometry arrives already in the model's coordinate space — centre-based pixels in the
     * source image — from `FrameWithBoxes`. **Nothing is converted here.** If a box lands half
     * its own size out of place, the bug is in that transform, and patching it at this layer
     * would hide it behind a second, disagreeing convention.
     *
     * Committing a redraw sets Q2 to "No" and latches it there. On an added row there is no Q2
     * to set: there was never a model box to be wrong about.
     */
    fun onBoxDrawn(box: ImageBox) {
        val target = _state.value.drawTarget ?: return
        _state.update { current ->
            val updated = current.findings.toMutableList()
            val finding = updated.getOrNull(target.findingIndex)
            if (finding != null) {
                updated[target.findingIndex] = if (target.slot == null) {
                    finding.copy(
                        answers = finding.answers.copy(
                            drawnBox = box,
                            isBoxCorrect = false,
                            boxReplaced = true,
                        ),
                    )
                } else {
                    // Drawn boxes stay packed at the front, so a slot either replaces one that
                    // is already there or lands next in line. Nothing is ever written past the
                    // end, which is what keeps "lowering the count drops undrawn eggs first"
                    // true without a second rule to enforce it.
                    val boxes = finding.answers.drawnBoxes.toMutableList()
                    if (target.slot in boxes.indices) boxes[target.slot] = box else boxes += box
                    finding.copy(answers = finding.answers.copy(drawnBoxes = boxes))
                }
            }
            current.copy(findings = updated, drawTarget = null)
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

        /**
         * How long typing has to pause before the suggestion index is queried.
         *
         * Long enough that a species name typed straight through costs one lookup, short enough
         * that a medtech who pauses to think sees the list without wondering whether it works.
         */
        const val SUGGESTION_DEBOUNCE_MS = 250L
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
