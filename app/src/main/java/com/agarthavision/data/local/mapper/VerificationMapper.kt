package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.FindingRow
import com.agarthavision.domain.usecase.verify.unboxedCountOf
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
 * the same species lands on the same row rather than duplicating it.
 */
/**
 * The id an added egg's detection row gets: its species and its slot within that species.
 *
 * **The slot is what this change added, and it was a data-loss bug without it.** The key used to
 * be the species alone, so two eggs of one species derived the same id and
 * `OnConflictStrategy.REPLACE` silently kept one. That was invisible while added rows carried no
 * geometry; the moment they could (86d4bk534), two hand-drawn boxes on two Ascaris eggs became
 * one box on submit, with no error anywhere.
 *
 * Exposed so the reopen path can find the rows an added species produced without re-deriving the
 * rule in a second place — two derivations of one key is how an edit starts appending instead of
 * replacing.
 */
fun addedDetectionIdFor(sampleId: String, species: String, slot: Int): String =
    derive("$sampleId#finding#$species#$slot")

/**
 * The id a prediction-backed detection gets, by its ordinal within the frame.
 *
 * Exposed so the reopen path can find the row a box produced without re-deriving the rule in a
 * second place - two derivations of the same key is how an edit silently starts appending
 * instead of replacing.
 */
fun detectionIdFor(sampleId: String, ordinal: Int): String = derive("$sampleId#box#$ordinal")

private fun derive(key: String): String =
    UUID.nameUUIDFromBytes(key.toByteArray()).toString()

/**
 * Stable finding-row id, keyed to match the table's uniqueness rule.
 *
 * `sample_species_findings` is unique on `(sample_id, species)` while `stage` is null, which it
 * always is — nothing writes a stage since 86d4a6jwy was reverted. If the stage work returns,
 * this key and `sample_species_findings_unique_staged` have to move together.
 */
private fun findingId(sampleId: String, row: FindingRow): String =
    UUID.nameUUIDFromBytes("$sampleId#row#${row.species}#${row.stage?.name.orEmpty()}".toByteArray()).toString()

/**
 * Every detection row a reviewed frame writes — **one row per egg**, which is what the table
 * says it is (`0001_init.sql`: "One row per detected egg").
 *
 * A list rather than a per-finding mapping, because neither half can be decided on one row any
 * more. An added row carries a field total for its whole species, so how many *unboxed* eggs it
 * is worth depends on how many boxes its siblings kept, and one such row therefore produces
 * however many rows that leaves.
 *
 * Both shapes land in the same table. A prediction-backed finding carries the model's box and
 * confidence; an added egg carries a box only if the medtech drew one, and otherwise all four
 * bbox columns are null at confidence 1.0 — the shape a manual capture has always been written
 * with, and why `bbox_*` was made nullable in `0007_detection_bbox_nullable.sql`.
 *
 * **The null-bbox rows are load-bearing for the corpus, and so is being able to spot them.** A
 * frame whose eggs are counted but not located cannot be used for detection training as it
 * stands: the usual pipeline treats un-annotated image regions as background, so fourteen
 * unboxed Ascaris teach the model fourteen times that an Ascaris egg is background. Writing a
 * row per egg either way is what makes the difference queryable with no extra column — a frame
 * is exhaustively localised iff
 *
 * ```sql
 * not exists (select 1 from public.detections d
 *             where d.sample_id = s.id and d.bbox_x is null and d.verdict <> 'FALSE_POSITIVE')
 * ```
 *
 * and a frame that fails it belongs in classification crops and hard-negative mining, never in
 * background sampling.
 *
 * Added rows are emitted for distinct species only. The UI merges two cards that land on one
 * species, and this is the backstop: emitting both would derive the same slot ids twice and
 * REPLACE would keep whichever came last.
 */
fun List<Finding>.toDetectionEntities(sampleId: String): List<DetectionEntity> {
    val emitted = mutableSetOf<String>()
    return flatMapIndexed { ordinal, finding ->
        val label = finding.answers.speciesLabel
        when {
            finding.prediction != null -> listOf(finding.toDetectionEntity(sampleId, ordinal))
            label == null || !emitted.add(label) -> emptyList()
            else -> (0 until unboxedCountOf(label)).map { slot ->
                finding.toDetectionEntity(sampleId, ordinal, slot)
            }
        }
    }
}

/** Persists one finding as one detection row. See [toDetectionEntities] for the shapes. */
// The branch count is the answer matrix itself - verdict, species, box origin and the
// touched flags each read from a different answer. Flattening it would hide the mapping
// that C7 depends on being auditable.
@Suppress("CyclomaticComplexMethod")
private fun Finding.toDetectionEntity(
    sampleId: String,
    ordinal: Int,
    slot: Int? = null,
): DetectionEntity {
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
    // An added egg takes its own slot's box; a model box takes the replacement, if there was one.
    val box = if (slot == null) answers.drawnBox else answers.drawnBoxes.getOrNull(slot)
    return DetectionEntity(
        detectionId = if (slot == null) {
            detectionIdFor(sampleId, ordinal)
        } else {
            addedDetectionIdFor(sampleId, label.orEmpty(), slot)
        },
        sampleId = sampleId,
        classLabel = modelClass?.let { EggSpecies.fromClassLabel(it)?.canonicalClass ?: it }
            ?: label.orEmpty(),
        // A hand-drawn box wins over the model's, because that is what replacing one means.
        // The model's confidence is NOT overwritten with 1.0 on a replaced box: "the model was
        // this sure and still localised it wrong" is the training signal, and a BOX_INCORRECT
        // verdict already says a human supplied the geometry. An added row has no prediction to
        // take a confidence from and so is written at 1.0 anyway, which is the shape a manual
        // finding has always had.
        confidence = prediction?.confidence ?: 1.0f,
        bboxX = box?.x ?: prediction?.x,
        bboxY = box?.y ?: prediction?.y,
        bboxW = box?.width ?: prediction?.width,
        bboxH = box?.height ?: prediction?.height,
        verdict = verdict.value,
        expertClass = expertClass,
        verifiedByUser = true,
        speciesTouched = answers.speciesTouched,
        stage = answers.stage?.name,
    )
}

/** Persists the collapsed per-species counts for a sample. */
fun FindingRow.toFindingEntity(sampleId: String): SampleSpeciesFindingEntity =
    SampleSpeciesFindingEntity(
        findingId = findingId(sampleId, this),
        sampleId = sampleId,
        species = species,
        stage = stage?.name,
        eggCount = eggCount,
    )
