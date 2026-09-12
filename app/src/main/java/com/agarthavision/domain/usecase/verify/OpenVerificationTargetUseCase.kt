package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.inference.toDomainPredictions
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant
import javax.inject.Inject

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
    private val gson: Gson,
) {
    suspend operator fun invoke(sampleId: String): Result<VerificationTarget> = runCatching {
        val entity = requireNotNull(sampleDao.getSampleById(sampleId)) {
            "Sample $sampleId not found."
        }
        val predictions = entity.predictionsJson
            ?.let { gson.fromJson<List<PredictionDto>>(it, PREDICTION_LIST).toDomainPredictions() }
            .orEmpty()
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

        val detections = detectionDao.getDetectionsForSample(sampleId).associateBy { it.detectionId }
        val boxFindings = predictions.mapIndexed { ordinal, prediction ->
            val stored = detections[detectionIdFor(sampleId, ordinal)]
            Finding(
                prediction = prediction,
                answers = stored?.toAnswers() ?: VerificationAnswers(
                    species = EggSpecies.fromClassLabel(prediction.classLabel),
                ),
            )
        }

        // Everything the medtech added on top of the model's boxes, recovered by subtracting
        // what the boxes already account for from the stored per-species totals.
        val boxedCounts = boxFindings
            .filter { it.eggContribution > 0 }
            .groupingBy { it.answers.speciesLabel to it.answers.stage }
            .eachCount()
        val addedFindings = findingDao.getFindingsForSample(sampleId).mapNotNull { row ->
            val stage = row.stage?.let(EggStage::fromValue)
            val remainder = row.eggCount - (boxedCounts[row.species to stage] ?: 0)
            if (remainder <= 0) return@mapNotNull null
            Finding(
                prediction = null,
                answers = VerificationAnswers(
                    species = EggSpecies.fromClassLabel(row.species) ?: EggSpecies.OTHER,
                    otherSpeciesText = row.species.takeIf {
                        EggSpecies.fromClassLabel(it) == null
                    }.orEmpty(),
                    stage = stage,
                    eggCount = remainder,
                    speciesTouched = true,
                ),
            )
        }

        VerificationTarget(
            frame = frame,
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
    private fun DetectionEntity.toAnswers(): VerificationAnswers {
        val label = expertClass ?: classLabel
        val species = EggSpecies.fromClassLabel(label)
        return when (DetectionVerdict.fromValue(verdict)) {
            DetectionVerdict.FALSE_POSITIVE -> VerificationAnswers(isEgg = false)
            DetectionVerdict.BOX_INCORRECT -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                stage = stage?.let(EggStage::fromValue),
                speciesTouched = speciesTouched,
            )
            else -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                stage = stage?.let(EggStage::fromValue),
                speciesTouched = speciesTouched,
            )
        }
    }

    private companion object {
        private val PREDICTION_LIST = object : TypeToken<List<PredictionDto>>() {}.type
    }
}

/** A frame plus whatever the medtech has already said about it. */
data class VerificationTarget(
    val frame: FlaggedFrame,
    val findings: List<Finding>,
    val missedEgg: Boolean?,
    val userNote: String,
)
