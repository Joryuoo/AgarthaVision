package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.FindingRow
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import java.util.UUID

/**
 * The rule, and the single place it is decided.
 *
 * **A verdict is a statement about one model box, never about the frame.** It compares the
 * class the model asserted *for that box* against the species the medtech chose on *that box's*
 * finding. Polyparasitism does not enter it: a [Finding] carries exactly one species, and only
 * a prediction-backed finding produces a verdict by comparison. A finding with no prediction is
 * not a model claim, so there is nothing for it to be wrong about — it persists as `CONFIRMED`
 * with confidence 1.0 and no box.
 *
 * Precedence is first-match-wins and unchanged: not an egg → `FALSE_POSITIVE`; box misplaced →
 * `BOX_INCORRECT`; species `OTHER` or differing from the model's → `WRONG_CLASS`; otherwise
 * `CONFIRMED`. `BOX_INCORRECT` still outranks `WRONG_CLASS`, because the verdict is about the
 * box.
 *
 * What changed alongside the findings model is not this function but `expert_class`: it is now
 * populated whenever the medtech's species differs from the model's, **including on a
 * `BOX_INCORRECT` row**. An egg with a misplaced box is still an egg and still has to be
 * counted. `0002_verification_fields.sql` describes the narrower original rule; it is applied
 * and is not edited (C6), so `schema.ts` and `docs/map/objects/Detection.md` carry the current
 * one.
 *
 * `species_touched` is orthogonal to all of this and never alters a verdict. It is deliberately
 * not a new [DetectionVerdict] member — that would mean touching the Supabase CHECK constraint
 * and every raw query naming a verdict, for a training-weight signal a boolean carries.
 */
fun computeVerdict(answers: VerificationAnswers, modelClass: String): DetectionVerdict = when {
    answers.isEgg != true -> DetectionVerdict.FALSE_POSITIVE
    answers.isBoxCorrect != true -> DetectionVerdict.BOX_INCORRECT
    answers.species == null -> DetectionVerdict.FALSE_POSITIVE
    answers.species == EggSpecies.OTHER -> DetectionVerdict.WRONG_CLASS
    EggSpecies.fromClassLabel(modelClass) != answers.species -> DetectionVerdict.WRONG_CLASS
    else -> DetectionVerdict.CONFIRMED
}

/**
 * Stable detection id for a finding on a sample.
 *
 * Deliberately derived rather than random. A verified sample is re-editable (ticket 86d4ab4vm),
 * and a random id would make every re-submit *insert a second full set of detections* beside
 * the first — doubling every egg count with no error anywhere. With a derived id and
 * `OnConflictStrategy.REPLACE` the re-submit overwrites in place: no append, no delete, and
 * nothing for C8 to object to. It is also the conflict target the remote upsert needs.
 *
 * A prediction-backed row keys on its ordinal, which is its index into the frame's predictions
 * and is stable as long as `predictions_json` survives verification — which is why that column
 * is no longer nulled on verify. An added row keys on what the medtech asserted, so re-adding
 * the same species and stage lands on the same row rather than duplicating it.
 */
private fun detectionId(sampleId: String, finding: Finding, ordinal: Int): String {
    val key = if (finding.prediction != null) {
        "$sampleId#box#$ordinal"
    } else {
        "$sampleId#finding#${finding.answers.speciesLabel.orEmpty()}#${finding.answers.stage?.value.orEmpty()}"
    }
    return UUID.nameUUIDFromBytes(key.toByteArray()).toString()
}

/** Stable finding-row id, keyed to match the table's uniqueness rule. */
private fun findingId(sampleId: String, row: FindingRow): String =
    UUID.nameUUIDFromBytes(
        "$sampleId#row#${row.species}#${row.stage?.value.orEmpty()}".toByteArray(),
    ).toString()

/**
 * Persists one finding as a detection row.
 *
 * Both shapes land in the same table. A prediction-backed finding carries the model's box and
 * confidence; a finding the medtech added carries no box at all — all four bbox columns null,
 * confidence 1.0 — which is exactly the shape a manual capture has always been written with,
 * and is why `bbox_*` was made nullable in `0007_detection_bbox_nullable.sql`.
 */
fun Finding.toDetectionEntity(sampleId: String, ordinal: Int): DetectionEntity {
    val label = answers.speciesLabel
    val modelClass = prediction?.classLabel
    val verdict = if (modelClass != null) {
        computeVerdict(answers, modelClass)
    } else {
        DetectionVerdict.CONFIRMED
    }
    // Populated whenever the human's species differs from what the model called it — including
    // on a BOX_INCORRECT row. Null when they agree, or when there is nothing to disagree with.
    val expertClass: String? = when {
        modelClass == null -> label
        label == null -> null
        EggSpecies.fromClassLabel(modelClass)?.canonicalClass == label -> null
        else -> label
    }
    return DetectionEntity(
        detectionId = detectionId(sampleId, this, ordinal),
        sampleId = sampleId,
        classLabel = modelClass?.let { EggSpecies.fromClassLabel(it)?.canonicalClass ?: it }
            ?: label.orEmpty(),
        confidence = prediction?.confidence ?: 1.0f,
        bboxX = prediction?.x,
        bboxY = prediction?.y,
        bboxW = prediction?.width,
        bboxH = prediction?.height,
        verdict = verdict.value,
        expertClass = expertClass,
        verifiedByUser = true,
        stage = answers.stage?.value,
        speciesTouched = answers.speciesTouched,
    )
}

/** Persists the collapsed per-species counts for a sample. */
fun FindingRow.toFindingEntity(sampleId: String): SampleSpeciesFindingEntity =
    SampleSpeciesFindingEntity(
        findingId = findingId(sampleId, this),
        sampleId = sampleId,
        species = species,
        stage = stage?.value,
        eggCount = eggCount,
    )
