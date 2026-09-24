package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.FindingRow
import com.agarthavision.domain.usecase.verify.SpeciesStageKey
import com.agarthavision.domain.usecase.verify.speciesStageKey
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
 * The id an added egg's detection row gets: its species, stage, and its slot within that
 * species+stage.
 *
 * **The slot is what this change added, and it was a data-loss bug without it.** The key used to
 * be the species alone, so two eggs of one species derived the same id and
 * `OnConflictStrategy.REPLACE` silently kept one. That was invisible while added rows carried no
 * geometry; the moment they could (86d4bk534), two hand-drawn boxes on two Ascaris eggs became
 * one box on submit, with no error anywhere.
 *
 * **[stageKey] is what a later change added, for the same reason.** Two added cards of one
 * species at different stages used to derive the same id too, so the second stage silently
 * overwrote the first (14zcqnthz6e). A null [stageKey] — an unstaged card, or a species the stage
 * question does not apply to — keeps the *old* key with no stage segment, so rows a build before
 * this change already wrote keep resolving to the same id and are not silently duplicated.
 *
 * Exposed so the reopen path can find the rows an added species produced without re-deriving the
 * rule in a second place — two derivations of one key is how an edit starts appending instead of
 * replacing.
 */
fun addedDetectionIdFor(sampleId: String, species: String, slot: Int, stageKey: String? = null): String =
    if (stageKey == null) {
        derive("$sampleId#finding#$species#$slot")
    } else {
        derive("$sampleId#finding#$species#$stageKey#$slot")
    }

/**
 * The id a prediction-backed detection gets, by its ordinal within the frame.
 *
 * Exposed so the reopen path can find the row a box produced without re-deriving the rule in a
 * second place - two derivations of the same key is how an edit silently starts appending
 * instead of replacing.
 */
fun detectionIdFor(sampleId: String, ordinal: Int): String = derive("$sampleId#box#$ordinal")

/**
 * The id the model's own prediction at [ordinal] gets in `predictions`.
 *
 * Same derivation as [detectionIdFor] on a different key, so the prediction and the detection
 * that rules on it are both stable under a re-push. `0004_predictions.sql` recomputes this in
 * SQL for its backfill, so the key string is part of the schema contract: change it and every
 * link the migration made stops matching what the app pushes.
 */
fun predictionIdFor(sampleId: String, ordinal: Int): String =
    derive("$sampleId#prediction#$ordinal")

private fun derive(key: String): String =
    UUID.nameUUIDFromBytes(key.toByteArray()).toString()

/**
 * Stable finding-row id, keyed to match the table's uniqueness rule.
 *
 * `sample_species_findings` is unique on `(sample_id, species, stage)` (`stage` nullable), and
 * [row]'s own stage is written into the key here so two stages of one species land on distinct
 * rows instead of one REPLACE-ing the other.
 */
private fun findingId(sampleId: String, row: FindingRow): String {
    val stageKey = row.stageDisplayName ?: row.stage?.name.orEmpty()
    return UUID.nameUUIDFromBytes("$sampleId#row#${row.species}#$stageKey".toByteArray()).toString()
}

/**
 * Every detection row a reviewed frame writes — **one row per egg**, which is what the table
 * says it is (`0001_init.sql`: "One row per detected egg").
 *
 * A list rather than a per-finding mapping, because neither half can be decided on one row any
 * more. An added row carries a field total for its whole species, so how many *unboxed* eggs it
 * is worth depends on how many boxes its siblings kept, and one such row therefore produces
 * however many rows that leaves.
 *
 * Both shapes land in the same table. A prediction-backed finding carries the model's
 * confidence and the box a human stands behind — the model's, the medtech's redraw, or none at
 * all on a `BOX_INCORRECT` row that was not redrawn. An added egg carries a box only if the
 * medtech drew one, and otherwise all four bbox columns are null at confidence 1.0 — the shape a
 * manual capture has always been written with, and why `bbox_*` was made nullable in
 * `0007_detection_bbox_nullable.sql`.
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
 * Added rows are emitted for distinct species+stage only. The UI merges two cards that land on
 * one species+stage, and this is the backstop: emitting both would derive the same slot ids
 * twice and REPLACE would keep whichever came last.
 */
fun List<Finding>.toDetectionEntities(sampleId: String): List<DetectionEntity> {
    val emitted = mutableSetOf<SpeciesStageKey>()
    return flatMapIndexed { ordinal, finding ->
        val key = finding.answers.speciesStageKey
        when {
            finding.prediction != null -> listOf(finding.toDetectionEntity(sampleId, ordinal))
            key == null || !emitted.add(key) -> emptyList()
            else -> (0 until unboxedCountOf(key.species, finding.answers.stage, finding.answers.otherStageText))
                .map { slot -> finding.toDetectionEntity(sampleId, ordinal, slot) }
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
    val drawn = if (slot == null) answers.drawnBox else answers.drawnBoxes.getOrNull(slot)
    // The model's box is written only where a human stood behind it. On a BOX_INCORRECT row
    // the medtech did not redraw, the box is the one they said is in the wrong place, and
    // storing it here would record rejected geometry as where the egg is — the frame would
    // then pass the exhaustiveness rule below and go into background sampling (14zcqnthrx6).
    // The model's own geometry is not lost: it is the `predictions` row this detection links to.
    val box = when {
        drawn != null -> drawn
        prediction == null || verdict == DetectionVerdict.BOX_INCORRECT -> null
        else -> ImageBox(prediction.x, prediction.y, prediction.width, prediction.height)
    }
    return DetectionEntity(
        detectionId = if (slot == null) {
            detectionIdFor(sampleId, ordinal)
        } else {
            addedDetectionIdFor(sampleId, label.orEmpty(), slot, answers.stageLabel)
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
        bboxX = box?.x,
        bboxY = box?.y,
        bboxW = box?.width,
        bboxH = box?.height,
        verdict = verdict.value,
        expertClass = expertClass,
        stage = answers.stageLabel,
    )
}

/** Persists the collapsed per-species counts for a sample. */
fun FindingRow.toFindingEntity(sampleId: String): SampleSpeciesFindingEntity =
    SampleSpeciesFindingEntity(
        findingId = findingId(sampleId, this),
        sampleId = sampleId,
        species = species,
        stage = stageDisplayName ?: stage?.name,
        eggCount = eggCount,
    )
