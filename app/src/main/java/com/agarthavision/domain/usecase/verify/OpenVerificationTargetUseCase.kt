package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.inference.toDomainPredictions
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.records.ResolveSampleImageSourceUseCase
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/**
 * What the verification screen opens with, for any sample in the queue.
 *
 * A verified sample stays in the queue and stays editable (86d4ab4vm), so this has to reopen it
 * showing **the medtech's own previous answers**, not a blank questionnaire. Reconstructing
 * those from the detection rows is what makes an edit a correction rather than a re-review.
 */
class OpenVerificationTargetUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val detectionDao: DetectionDao,
    private val findingDao: SampleSpeciesFindingDao,
    private val resolveSampleImageSource: ResolveSampleImageSourceUseCase,
    private val gson: Gson,
) {
    suspend operator fun invoke(sampleId: String): Result<VerificationTarget> = runCatching {
        val entity = requireNotNull(sampleDao.getSampleById(sampleId)) {
            "Sample $sampleId not found."
        }
        val storedDetections = detectionDao.getDetectionsForSample(sampleId)

        // predictions_json is written on capture and survives verification, but it is **not**
        // pulled down from Supabase - the remote samples table has no such column. A sample
        // synced from another device therefore arrives with no model output at all, and this
        // screen used to open it with no boxes, silently. The detection rows *are* pulled down
        // (FetchRemoteDataUseCase), carry the same centre-based geometry, and keep the model's
        // own class label and confidence, so they reconstruct what was lost rather than
        // inventing it.
        val storedById = storedDetections.associateBy { it.detectionId }
        val fromJson = entity.predictionsJson
            ?.let { gson.fromJson<List<PredictionDto>>(it, PREDICTION_LIST).toDomainPredictions() }
        val predictions = fromJson ?: reconstructPredictions(sampleId, storedById)
        val reconstructed = fromJson == null && predictions.isNotEmpty()

        val frame = FlaggedFrame(
            sampleId = entity.sampleId,
            sessionId = entity.sessionId,
            capturedAt = Instant.ofEpochMilli(entity.timestamp),
            jpegBytes = runCatching { File(entity.imagePath).readBytes() }
                .getOrDefault(ByteArray(0)),
            predictions = predictions,
            source = if (entity.isManual) FrameSource.MANUAL else FrameSource.MODEL,
            inferenceModelVersion = entity.inferenceModelVersion,
            imageWidth = entity.imageWidth,
            imageHeight = entity.imageHeight,
        )

        val detections = storedById
        val boxFindings = predictions.mapIndexed { ordinal, prediction ->
            val stored = detections[detectionIdFor(sampleId, ordinal)]
            Finding(
                prediction = prediction,
                answers = stored?.toAnswers(prediction, reconstructed) ?: VerificationAnswers(
                    species = EggSpecies.fromClassLabel(prediction.classLabel),
                ),
            )
        }

        // Everything the medtech added on top of the model's boxes, recovered by subtracting
        // what the boxes already account for from the stored per-species totals.
        val boxedCounts = boxFindings
            .filter { it.eggContribution > 0 }
            .groupingBy { it.answers.speciesLabel }
            .eachCount()
        val addedFindings = findingDao.getFindingsForSample(sampleId).mapNotNull { row ->
            val remainder = row.eggCount - (boxedCounts[row.species] ?: 0)
            if (remainder <= 0) return@mapNotNull null
            Finding(
                prediction = null,
                answers = VerificationAnswers(
                    species = EggSpecies.fromClassLabel(row.species) ?: EggSpecies.OTHER,
                    otherSpeciesText = row.species.takeIf {
                        EggSpecies.fromClassLabel(it) == null
                    }.orEmpty(),
                    eggCount = remainder,
                    speciesTouched = true,
                ),
            )
        }

        VerificationTarget(
            frame = frame,
            // Local file first, then a 15-minute signed Storage URL - the same source the Sample
            // Data Screen uses, so a sample synced from another device has a frame to draw at
            // all. Reading entity.imagePath directly is what made the cross-device case open a
            // blank canvas: File("").readBytes() throws, getOrDefault swallows it, and a missing
            // image becomes indistinguishable from an empty one.
            imageSource = resolveSampleImageSource(entity.toDomain()),
            findings = boxFindings + addedFindings,
            // A verified sample carries a real answer here; an unverified one has none yet, and
            // must not have one invented on the medtech's behalf.
            missedEgg = entity.needsReannotation.takeIf { detections.isNotEmpty() },
            userNote = entity.userNote.orEmpty(),
        )
    }

    /**
     * Rebuilds the answers that produced a stored detection.
     *
     * One edge is lossy and is documented rather than papered over: `computeVerdict` maps both
     * "not an egg" and "an egg, box correct, no species chosen" to `FALSE_POSITIVE`. This picks
     * the first, which is the canonical round-trip and stable under a re-submit; if it was
     * really the second, the medtech sees an answer they can correct.
     */
    /**
     * Rebuilds the model's own output from the detection rows it produced.
     *
     * Walks ordinals from zero using the same derivation `VerificationMapper` writes with, and
     * stops at the first gap: only prediction-backed rows key that way, so an egg the medtech
     * added never lands here and is recovered as an added finding instead.
     *
     * The class label and confidence are the model's, persisted at verification time. Nothing is
     * invented - this recovers data the sync does not carry, it does not synthesise it.
     */
    private fun reconstructPredictions(
        sampleId: String,
        storedById: Map<String, DetectionEntity>,
    ): List<Prediction> = generateSequence(0) { it + 1 }
        .map { ordinal -> storedById[detectionIdFor(sampleId, ordinal)] }
        .takeWhile { it != null }
        .filterNotNull()
        .mapNotNull { detection ->
            Prediction(
                classLabel = detection.classLabel,
                confidence = detection.confidence,
                x = detection.bboxX ?: return@mapNotNull null,
                y = detection.bboxY ?: return@mapNotNull null,
                width = detection.bboxW ?: return@mapNotNull null,
                height = detection.bboxH ?: return@mapNotNull null,
            )
        }
        .toList()

    /**
     * @param reconstructed true when [prediction] came from this very detection row rather than
     *   from `predictions_json`. The model's original geometry is then unknown on this device,
     *   so the two compare equal and the geometry test cannot see a replacement. A BOX_INCORRECT
     *   verdict is the durable statement that the model localised this wrong, so it locks Q2 on
     *   its own — stricter than the comparison, never looser, and the training label survives.
     *   A medtech who answered "No" by mistake on the capturing device can still correct it
     *   there, where `predictions_json` lives.
     */
    private fun DetectionEntity.toAnswers(
        prediction: Prediction,
        reconstructed: Boolean,
    ): VerificationAnswers {
        val label = expertClass ?: classLabel
        val species = EggSpecies.fromClassLabel(label)
        val replaced = reconstructed || replacesBoxOf(prediction)
        return when (DetectionVerdict.fromValue(verdict)) {
            DetectionVerdict.FALSE_POSITIVE -> VerificationAnswers(isEgg = false)
            DetectionVerdict.BOX_INCORRECT -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                speciesTouched = speciesTouched,
                drawnBox = if (replaced) storedBox() else null,
                boxReplaced = replaced,
            )
            else -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                speciesTouched = speciesTouched,
            )
        }
    }

    /**
     * The stored geometry, when this detection carries any.
     *
     * Null for a row written with no box at all — an egg the medtech added and did not draw.
     */
    private fun DetectionEntity.storedBox(): ImageBox? {
        val x = bboxX ?: return null
        val y = bboxY ?: return null
        val w = bboxW ?: return null
        val h = bboxH ?: return null
        return ImageBox(x, y, w, h)
    }

    /**
     * Whether the stored box is somewhere other than where the model put it.
     *
     * This is the durable record that a box was **replaced**, and it is what re-locks Q2 to "No"
     * on reopen. There is no column for it, and adding one would mean a Room version bump — the
     * one change this project has already been burned by. The comparison is safe because both
     * sides are the same numbers: `VerificationMapper` copies a prediction's geometry through
     * unchanged unless a drawn box overrides it, so an untouched box compares exactly equal and
     * only a redraw moves it.
     *
     * [BOX_TOLERANCE_PX] absorbs the float round-trip through Room and Postgres rather than any
     * real movement; a hand-drawn box is never within half a pixel of the model's.
     */
    private fun DetectionEntity.replacesBoxOf(prediction: Prediction): Boolean {
        val stored = storedBox() ?: return false
        return abs(stored.x - prediction.x) > BOX_TOLERANCE_PX ||
            abs(stored.y - prediction.y) > BOX_TOLERANCE_PX ||
            abs(stored.width - prediction.width) > BOX_TOLERANCE_PX ||
            abs(stored.height - prediction.height) > BOX_TOLERANCE_PX
    }

    private companion object {
        private val PREDICTION_LIST = object : TypeToken<List<PredictionDto>>() {}.type

        /** Float round-trip slack, not a movement threshold. */
        private const val BOX_TOLERANCE_PX = 0.5f
    }
}

/** A frame plus whatever the medtech has already said about it. */
data class VerificationTarget(
    val frame: FlaggedFrame,
    /**
     * Where the frame's image can actually be got from.
     *
     * Carried alongside [frame] rather than baked into its bytes, because the bytes are only
     * there on the device that captured the sample. `SampleRemoteDataSource` writes
     * `imagePath = ""` for everything it pulls down, so on any other device the frame is zero
     * bytes and the screen has to load it from Storage instead — or say plainly that it cannot,
     * rather than opening a blank canvas the medtech might annotate into the void.
     */
    val imageSource: SampleImageSource,
    val findings: List<Finding>,
    /**
     * What `samples.needs_reannotation` holds for this sample.
     *
     * Kept as a faithful record of the stored row, but **not what the screen renders**: Q4 is
     * derived from the findings now, so a reopened sample re-derives it from the rows it carries
     * and the two cannot disagree. Null when the sample has no detections to have missed
     * anything alongside.
     */
    val missedEgg: Boolean?,
    val userNote: String,
)
