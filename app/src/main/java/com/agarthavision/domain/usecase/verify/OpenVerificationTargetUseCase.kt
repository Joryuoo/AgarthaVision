package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.inference.decodePredictions
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.mapper.addedDetectionIdFor
import com.agarthavision.data.local.mapper.detectionIdFor
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.usecase.records.ResolveSampleImageSourceUseCase
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.google.gson.Gson
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
    private val resolveSampleImageSource: ResolveSampleImageSourceUseCase,
    private val gson: Gson,
) {
    suspend operator fun invoke(sampleId: String): Result<VerificationTarget> = runCatching {
        val entity = requireNotNull(sampleDao.getSampleById(sampleId)) {
            "Sample $sampleId not found."
        }
        val storedDetections = detectionDao.getDetectionsForSample(sampleId)

        // predictions_json is written on capture, survives verification, and on any other device
        // is restored by the pull from the `predictions` table (FetchRemoteDataUseCase). It can
        // still be missing - a sample whose model output never reached the server, such as one
        // pushed before that table existed - and then the detection rows stand in: they carry
        // the same centre-based geometry and keep the model's own class label and confidence,
        // so they reconstruct what was lost rather than inventing it.
        val storedById = storedDetections.associateBy { it.detectionId }
        val predictions = gson.decodePredictions(entity.predictionsJson)
            ?: reconstructPredictions(sampleId, storedById)

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
                // The fallback covers an ordinal with no stored row — a sample opened before
                // it was ever submitted. It seeds the same way `initialFindings` does rather
                // than leaving the booleans null, because the questions are checkboxes now and
                // a checkbox has no way to draw "unanswered": a null would render unchecked,
                // which reads as "not an egg" — an answer nobody gave.
                answers = stored?.toAnswers(prediction) ?: VerificationAnswers(
                    isEgg = true,
                    isBoxCorrect = true,
                    speciesConfirmed = EggSpecies.fromClassLabel(prediction.classLabel)
                        ?.let { true },
                    species = EggSpecies.fromClassLabel(prediction.classLabel),
                ),
            )
        }

        // Everything the medtech added on top of the model's boxes. `sample_species_findings`
        // stores the field total per species+stage — which is exactly what the added row now
        // holds, so it is carried across rather than subtracted down to a remainder. A
        // species+stage whose total the boxes already account for needs no added row at all.
        val boxedCounts = boxFindings
            .filter { it.countsAsEgg }
            .mapNotNull { it.answers.speciesStageKey }
            .groupingBy { it }
            .eachCount()
        val findingRows = findingDao.getFindingsForSample(sampleId)
        // Only the rows that survive the boxedCounts filter below are genuine added-card rows -
        // a species+stage the boxes already fully account for produces no added row at all, so
        // counting every raw row (including those) would overcount how many added cards a
        // species really has and switch the legacy-id fallback off when it is still safe.
        val survivingRows = findingRows.mapNotNull { row ->
            val (parsedStage, otherStage) = parseStage(row.stage)
            val key = SpeciesStageKey(row.species, parsedStage, otherStage.trim())
            row.takeIf { row.eggCount > (boxedCounts[key] ?: 0) }?.let { it to key }
        }
        val rowCountBySpecies = survivingRows.groupingBy { (row, _) -> row.species }.eachCount()
        val addedFindings = survivingRows.map { (row, _) ->
            recoverAddedFinding(sampleId, row, rowCountBySpecies, storedById)
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
     * Rebuilds one added finding from its `sample_species_findings` row.
     *
     * A row's own on-disk id is checked first: a row whose stage-aware slot-0 id is already
     * present in `storedById` already carries a stage segment and is pinned non-primary,
     * regardless of how many rows of that species currently survive. This matters because a
     * species can genuinely end up with exactly one surviving row that already owns a
     * stage-aware id - e.g. a sibling that used to pin it non-primary was later removed - and
     * that row must stay pinned non-primary so its id does not flip back to plain on the next
     * unedited resubmit (which would leave its old stage-aware rows stranded remotely while a
     * duplicate set gets written under the plain id).
     *
     * Only once a row does not already own a stage-aware id does row count decide the pin: a
     * lone row (`rowCountBySpecies == 1`) with no stage-aware id of its own is pinned primary -
     * being alone with no stage segment already on disk is what makes primary correct, whether
     * it is a brand-new species or a legacy plain-id row. With two-or-more such rows, each row's
     * own on-disk id is checked directly: the one row with no stage-aware id of its own is the
     * one whose boxes (if any) are still filed under the old, stage-less id, so it is pinned
     * primary and keeps that id. A pinned-non-primary row is never re-elected primary later just
     * because siblings are removed - see [VerificationAnswers.isPrimaryAdded].
     */
    private fun recoverAddedFinding(
        sampleId: String,
        row: SampleSpeciesFindingEntity,
        rowCountBySpecies: Map<String, Int>,
        storedById: Map<String, DetectionEntity>,
    ): Finding {
        val (parsedStage, otherStage) = parseStage(row.stage)
        val answers = VerificationAnswers(
            species = EggSpecies.fromClassLabel(row.species) ?: EggSpecies.OTHER,
            otherSpeciesText = row.species.takeIf {
                EggSpecies.fromClassLabel(it) == null
            }.orEmpty(),
            stage = parsedStage,
            otherStageText = otherStage,
            fieldTotal = row.eggCount,
        )
        val isLoneRow = rowCountBySpecies[row.species] == 1
        val ownsStageAwareId = answers.stageLabel?.let { stageKey ->
            storedById.containsKey(addedDetectionIdFor(sampleId, row.species, 0, stageKey))
        } ?: false
        val isPrimaryAdded = when {
            ownsStageAwareId -> false
            isLoneRow -> true
            else -> storedById.containsKey(addedDetectionIdFor(sampleId, row.species, 0, stageKey = null))
        }
        return Finding(
            prediction = null,
            answers = answers.copy(
                isPrimaryAdded = isPrimaryAdded,
                drawnBoxes = recoverDrawnBoxes(
                    sampleId,
                    row.species,
                    answers.stageLabel,
                    isPrimaryAdded,
                    storedById,
                ),
            ),
        )
    }

    /**
     * Rebuilds the model's own output from the detection rows it produced.
     *
     * Walks ordinals from zero using the same derivation `VerificationMapper` writes with, and
     * stops at the first gap: only prediction-backed rows key that way, so an egg the medtech
     * added never lands here and is recovered as an added finding instead.
     *
     * The class label and confidence are the model's, persisted at verification time. Nothing is
     * invented - this recovers data the sync does not carry, it does not synthesise it.
     *
     * **It also stops at the first row with no box**, and must not skip it. A `BOX_INCORRECT` row
     * the medtech did not redraw carries no geometry, so there is no prediction to rebuild from
     * it — and dropping it from the middle of the list would slide every later prediction one
     * ordinal early, reopen each against its neighbour's detection, and write their rulings onto
     * the wrong rows on re-submit. Stopping leaves the later boxes off the screen but every row
     * intact. It is a fallback in any case: the pull restores `predictions_json` from the
     * `predictions` table, so this runs only for a sample whose model output never reached the
     * server.
     */
    private fun reconstructPredictions(
        sampleId: String,
        storedById: Map<String, DetectionEntity>,
    ): List<Prediction> = generateSequence(0) { it + 1 }
        .map { ordinal -> storedById[detectionIdFor(sampleId, ordinal)] }
        .map { detection ->
            val box = detection?.storedBox() ?: return@map null
            Prediction(
                classLabel = detection.classLabel,
                confidence = detection.confidence,
                x = box.x,
                y = box.y,
                width = box.width,
                height = box.height,
            )
        }
        .takeWhile { it != null }
        .filterNotNull()
        .toList()

    /**
     * Rebuilds the answers that produced a stored detection.
     *
     * One edge is lossy and is documented rather than papered over: `computeVerdict` maps both
     * "not an egg" and "an egg, box correct, no species chosen" to `FALSE_POSITIVE`. This picks
     * the first, which is the canonical round-trip and stable under a re-submit; if it was
     * really the second, the medtech sees an answer they can correct.
     *
     * **[VerificationAnswers.speciesConfirmed] has to be rebuilt here, not left to its default.**
     * It is not persisted — nothing in `detections` holds it — so it is derived the one way it
     * can be: the medtech kept the model's species iff the species this row carries is the one
     * the model suggested. Leaving it null looked harmless while Q3 was a Yes/No pair, which
     * draws null as "unanswered" and is at least honest. It is not harmless against a checkbox:
     * null renders **unchecked**, so every reopened row read "this is not an Ascaris egg" about
     * a species the medtech had confirmed, and the picker stayed hidden (it opens on `false`,
     * not on null), leaving an answer that looked wrong and no control to correct it with.
     *
     * **A replaced box is read off the row, not compared against the model's (14zcqnthrx8).**
     * `VerificationMapper` writes a `BOX_INCORRECT` row with a box only when the medtech drew
     * one — the one they rejected is not kept — so on that verdict a stored box *is* the redraw
     * and a null box is a rejection nobody redrew. This used to be a geometry comparison against
     * `predictions_json` with a half-pixel tolerance, which was right only while both sides
     * survived every round trip, and could not see anything at all on a device that had to
     * rebuild its predictions from these same rows.
     *
     * Rows written before that rule may carry the rejected model box on a `BOX_INCORRECT`
     * verdict, and they now reopen as redrawn. That is the direction that keeps the label:
     * Q2 stays locked at "No", and a re-submit writes back the same geometry it read.
     */
    private fun DetectionEntity.toAnswers(prediction: Prediction): VerificationAnswers {
        val label = expertClass ?: classLabel
        val species = EggSpecies.fromClassLabel(label)
        val (parsedStage, otherStage) = parseStage(stage)
        val replaced = storedBox() != null
        // Null when the model's class maps to no EggSpecies: there was never anything to
        // confirm, so the picker is offered directly and the checkbox never renders. Compared
        // against the coerced species rather than the raw one, so that a free-text expert_class
        // - which lands on OTHER - reads as an override of the model rather than as agreement.
        val suggested = EggSpecies.fromClassLabel(prediction.classLabel)
        val confirmed = suggested?.let { (species ?: EggSpecies.OTHER) == it }
        return when (DetectionVerdict.fromValue(verdict)) {
            DetectionVerdict.FALSE_POSITIVE -> VerificationAnswers(isEgg = false)
            DetectionVerdict.BOX_INCORRECT -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                speciesConfirmed = confirmed,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                stage = parsedStage,
                otherStageText = otherStage,
                drawnBox = if (replaced) storedBox() else null,
                boxReplaced = replaced,
            )
            else -> VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                speciesConfirmed = confirmed,
                species = species ?: EggSpecies.OTHER,
                otherSpeciesText = if (species == null) label else "",
                stage = parsedStage,
                otherStageText = otherStage,
            )
        }
    }

    private fun parseStage(storedStage: String?): Pair<EggStage?, String> {
        if (storedStage.isNullOrBlank()) return null to ""
        val stageEnum = EggStage.fromName(storedStage)
        return when {
            stageEnum != null && stageEnum != EggStage.OTHER -> stageEnum to ""
            stageEnum == EggStage.OTHER -> EggStage.OTHER to ""
            else -> EggStage.OTHER to storedStage
        }
    }

    /**
     * The stored geometry, when this detection carries any.
     *
     * Null for a row written with no box at all — an egg the medtech added and did not draw.
     */
    // Four guard-clause returns for the four nullable columns read far more clearly than a
    // nested let-chain, and any one being null means the same thing: no stored box.
    @Suppress("ReturnCount")
    private fun DetectionEntity.storedBox(): ImageBox? {
        val x = bboxX ?: return null
        val y = bboxY ?: return null
        val w = bboxW ?: return null
        val h = bboxH ?: return null
        return ImageBox(x, y, w, h)
    }

    /**
     * The boxes the medtech drew for an added species+stage, in slot order.
     *
     * Added eggs are one detection row each, keyed `#finding#<species>#<stage>#<slot>`, and the
     * drawn ones occupy the front slots — so this walks up from zero and stops at the first row
     * with no geometry, which is where the drawn ones end. Stopping there rather than scanning
     * the whole species is what keeps the round trip stable: the list that comes back is the
     * list that went out, and re-submitting writes the same slots to the same ids.
     *
     * **Falls back to the old species-only id when the stage-aware lookup finds nothing and
     * [allowLegacyFallback] says it is safe.** Rows written before this change (or by an earlier
     * build of this feature) derived their id with no stage segment at all; without the
     * fallback, a card reopened after that would show its total but silently lose the boxes
     * already drawn on it. [allowLegacyFallback] is the row's own primary pin
     * (`recoverAddedFinding`'s `isPrimaryAdded`) — the plain id can only ever belong to the one
     * row of a species that owns it, so only that row is allowed to go looking for boxes under
     * it; a non-primary sibling's own id is already stage-aware, and guessing at the plain id on
     * its behalf would risk handing it boxes that belong to a different card.
     */
    private fun recoverDrawnBoxes(
        sampleId: String,
        species: String,
        stageKey: String?,
        allowLegacyFallback: Boolean,
        storedById: Map<String, DetectionEntity>,
    ): List<ImageBox> {
        val staged = generateSequence(0) { it + 1 }
            .map { slot -> storedById[addedDetectionIdFor(sampleId, species, slot, stageKey)]?.storedBox() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
        if (staged.isNotEmpty() || stageKey == null || !allowLegacyFallback) return staged
        return generateSequence(0) { it + 1 }
            .map { slot -> storedById[addedDetectionIdFor(sampleId, species, slot)]?.storedBox() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
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
