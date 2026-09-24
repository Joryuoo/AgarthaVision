package com.agarthavision.data.local.mapper

import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationMapperTest {

    private val prediction = Prediction(
        classLabel = "Ascaris",
        confidence = 0.9f,
        x = 100f,
        y = 200f,
        width = 50f,
        height = 60f,
    )

    @Test
    fun `Q1 No yields FALSE_POSITIVE regardless of later answers`() {
        val answers = VerificationAnswers(isEgg = false)
        assertEquals(DetectionVerdict.FALSE_POSITIVE, computeVerdict(answers, "Ascaris"))
    }

    @Test
    fun `Q2 No yields BOX_INCORRECT without species`() {
        val answers = VerificationAnswers(isEgg = true, isBoxCorrect = false)
        assertEquals(DetectionVerdict.BOX_INCORRECT, computeVerdict(answers, "Ascaris"))
    }

    @Test
    fun `species match produces CONFIRMED`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        assertEquals(DetectionVerdict.CONFIRMED, computeVerdict(answers, "Ascaris"))
    }

    @Test
    fun `species mismatch produces WRONG_CLASS with expert_class set`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.TRICHURIS,
        )
        assertEquals(DetectionVerdict.WRONG_CLASS, computeVerdict(answers, "Ascaris"))
        val entity = listOf(Finding(prediction, answers)).toDetectionEntities("sample-1").single()
        assertEquals("Trichuris trichiura", entity.expertClass)
    }

    @Test
    fun `OTHER species sets expertClass from otherSpeciesText`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.OTHER,
            otherSpeciesText = "Enterobius",
        )
        val entity = listOf(Finding(prediction, answers)).toDetectionEntities("sample-1").single()
        assertEquals(DetectionVerdict.WRONG_CLASS.value, entity.verdict)
        assertEquals("Enterobius", entity.expertClass)
    }

    @Test
    fun `OTHER stage sets stage column to otherStageText`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
            stage = EggStage.OTHER,
            otherStageText = "Larvated",
        )
        val entity = listOf(Finding(prediction, answers)).toDetectionEntities("sample-1").single()
        assertEquals("Larvated", entity.stage)
    }

    @Test
    fun `confirmed entity has null expertClass and correct verdict`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val entity = listOf(Finding(prediction, answers)).toDetectionEntities("sample-1").single()
        assertNull(entity.expertClass)
        assertEquals(DetectionVerdict.CONFIRMED.value, entity.verdict)
    }

    // A box the medtech drew

    /**
     * A hand-drawn box wins over the model's, because that is what replacing one means.
     *
     * The geometry arrives in the same space the model uses — centre-based pixels in the source
     * image — so it goes into the same columns unchanged. Storing the drag's corner instead
     * would render every replaced box offset by half its size, with nothing failing.
     */
    @Test
    fun `a drawn box replaces the model's geometry in the detection row`() {
        val drawn = ImageBox(x = 310f, y = 240f, width = 44f, height = 38f)
        val finding = Finding(
            prediction = prediction,
            answers = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = EggSpecies.ASCARIS,
                drawnBox = drawn,
                boxReplaced = true,
            ),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(310f, entity.bboxX)
        assertEquals(240f, entity.bboxY)
        assertEquals(44f, entity.bboxW)
        assertEquals(38f, entity.bboxH)
        assertEquals(DetectionVerdict.BOX_INCORRECT.value, entity.verdict)
    }

    /**
     * The model's confidence survives a redraw.
     *
     * "The model was this sure and still localised it wrong" is the training signal, and
     * overwriting it with 1.0 would erase exactly that. The row is already marked as carrying
     * human geometry by its BOX_INCORRECT verdict.
     */
    @Test
    fun `replacing a box does not overwrite the model's confidence`() {
        val finding = Finding(
            prediction = prediction,
            answers = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = EggSpecies.ASCARIS,
                drawnBox = ImageBox(1f, 2f, 3f, 4f),
                boxReplaced = true,
            ),
        )

        assertEquals(0.9f, listOf(finding).toDetectionEntities("sample-1").single().confidence)
    }

    /** An untouched box keeps the model's own geometry, byte for byte. */
    @Test
    fun `a box nobody redrew keeps the model's geometry`() {
        val finding = Finding(
            prediction = prediction,
            answers = VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = EggSpecies.ASCARIS,
            ),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(prediction.x, entity.bboxX)
        assertEquals(prediction.y, entity.bboxY)
        assertEquals(prediction.width, entity.bboxW)
        assertEquals(prediction.height, entity.bboxH)
    }

    /**
     * An added egg the medtech drew a box for is still a human assertion: confidence 1.0, and no
     * prediction behind it, which is what distinguishes it in the corpus from a model box.
     */
    @Test
    fun `an added egg with a drawn box is written as a human assertion`() {
        val drawn = ImageBox(x = 90f, y = 90f, width = 20f, height = 20f)
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.HOOKWORM,
                fieldTotal = 1,
                drawnBoxes = listOf(drawn),
            ),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(1.0f, entity.confidence)
        assertEquals(90f, entity.bboxX)
    }

    /** And an added egg with no box still writes null geometry, which the column allows. */
    @Test
    fun `an added egg with no box writes no geometry`() {
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.HOOKWORM,
                fieldTotal = 1,
            ),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertNull(entity.bboxX)
        assertNull(entity.bboxY)
    }

    /**
     * **The regression this whole change exists for.**
     *
     * Two eggs of one species used to derive one detection id, because the key was the species
     * alone. While added rows carried no geometry that was invisible; the moment they could,
     * REPLACE turned two hand-drawn boxes into one, silently, on submit.
     */
    @Test
    fun `two eggs of one species keep two distinct rows and two distinct boxes`() {
        val first = ImageBox(x = 10f, y = 10f, width = 4f, height = 4f)
        val second = ImageBox(x = 80f, y = 80f, width = 4f, height = 4f)
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                fieldTotal = 2,
                drawnBoxes = listOf(first, second),
            ),
        )

        val entities = listOf(finding).toDetectionEntities("sample-1")

        assertEquals(2, entities.size)
        assertEquals(2, entities.map { it.detectionId }.distinct().size)
        assertEquals(listOf(10f, 80f), entities.map { it.bboxX })
    }

    /** One row per egg, boxed or not — which is what makes the exhaustiveness rule queryable. */
    @Test
    fun `an added species writes one row per unboxed egg`() {
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val findings = listOf(
            Finding(prediction, ascaris),
            Finding(prediction = null, answers = ascaris.copy(fieldTotal = 4)),
        )

        val entities = findings.toDetectionEntities("sample-1")

        // One for the model's box, three for the eggs it missed.
        assertEquals(4, entities.size)
        assertEquals(3, entities.count { it.bboxX == null })
    }

    /** A total the boxes already cover adds nothing — there is no missed egg to write. */
    @Test
    fun `a total matching the boxes writes no extra rows`() {
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val findings = listOf(
            Finding(prediction, ascaris),
            Finding(prediction = null, answers = ascaris.copy(fieldTotal = 1)),
        )

        assertEquals(1, findings.toDetectionEntities("sample-1").size)
    }

    // ── Box provenance (14zcqnthrx6) ─────────────────────────────────────────

    /**
     * **The fix.** A box the medtech called misplaced and did not redraw is not written as where
     * the egg is. Falling back to the model's geometry made the row pass the exhaustiveness rule
     * and sent a frame carrying rejected geometry into background sampling. The model's box is
     * not lost — it is the `predictions` row this detection links to.
     */
    @Test
    fun `a rejected box nobody redrew writes no geometry`() {
        val finding = Finding(
            prediction = prediction,
            answers = VerificationAnswers(isEgg = true, isBoxCorrect = false, species = EggSpecies.ASCARIS),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(DetectionVerdict.BOX_INCORRECT.value, entity.verdict)
        assertNull(entity.bboxX)
        assertNull(entity.bboxY)
        assertNull(entity.bboxW)
        assertNull(entity.bboxH)
        // The rest of the model's claim is untouched: still the corpus's "sure, and wrong".
        assertEquals(0.9f, entity.confidence)
        assertEquals("Ascaris lumbricoides", entity.classLabel)
    }

    /**
     * A false positive keeps the model's box. It is the region the model wrongly called an egg,
     * which is exactly what hard-negative mining needs — the nulling is for BOX_INCORRECT only.
     */
    @Test
    fun `a false positive keeps the model's box`() {
        val finding = Finding(prediction = prediction, answers = VerificationAnswers(isEgg = false))

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(DetectionVerdict.FALSE_POSITIVE.value, entity.verdict)
        assertEquals(prediction.x, entity.bboxX)
        assertEquals(prediction.height, entity.bboxH)
    }

    /** A wrong species on a well-placed box is still a well-placed box. */
    @Test
    fun `a wrong class keeps the model's box`() {
        val finding = Finding(
            prediction = prediction,
            answers = VerificationAnswers(isEgg = true, isBoxCorrect = true, species = EggSpecies.HOOKWORM),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(DetectionVerdict.WRONG_CLASS.value, entity.verdict)
        assertEquals(prediction.x, entity.bboxX)
    }

    /**
     * Pins both derivations to ids that exist on the live server. `0004_predictions.sql`
     * recomputes these in SQL for its backfill; the detection id below was found there by that
     * SQL, so this is the check that the two implementations agree. Change a key string and
     * this fails before every backfilled link silently stops matching.
     */
    // ── species+stage identity (14zcqnthz6e) ─────────────────────────────────

    /** Two added cards of one species at different stages write distinct, correctly-staged rows. */
    @Test
    fun `two added cards of different stages write distinct rows with their own stage`() {
        val cf = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                stage = EggStage.CORTICATED_FERTILIZED,
                fieldTotal = 2,
            ),
        )
        val df = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                stage = EggStage.DECORTICATED_FERTILIZED,
                fieldTotal = 1,
            ),
        )

        val entities = listOf(cf, df).toDetectionEntities("sample-1")

        assertEquals(3, entities.size)
        assertEquals(3, entities.map { it.detectionId }.distinct().size)
        assertEquals(2, entities.count { it.stage == EggStage.CORTICATED_FERTILIZED.name })
        assertEquals(1, entities.count { it.stage == EggStage.DECORTICATED_FERTILIZED.name })
    }

    /**
     * Compatibility check: an unstaged added card must still land on the id a build before
     * stage-aware ids existed already wrote, or a re-submit from an old queue starts duplicating
     * rows instead of replacing them.
     */
    @Test
    fun `an unstaged added card keeps the old species-only detection id`() {
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(species = EggSpecies.HOOKWORM, fieldTotal = 1),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(addedDetectionIdFor("sample-1", "Hookworm", 0), entity.detectionId)
        assertEquals(addedDetectionIdFor("sample-1", "Hookworm", 0, stageKey = null), entity.detectionId)
    }

    /**
     * **Regression guard for the remote double-count bug.** Old data was saved under a
     * species-only id. Deriving a stage-aware id unconditionally for a single staged card meant
     * resubmitting it with no edits wrote a brand-new id and deleted the old one locally - and
     * since the remote push only ever upserts, never deletes, Supabase ended up holding both and
     * double-counted the species. A lone staged card must keep resolving to the old, stage-less
     * id so an unedited resubmit writes nothing new.
     */
    @Test
    fun `a single staged card keeps the old species-only id, not a stage-aware one`() {
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                stage = EggStage.CORTICATED_FERTILIZED,
                fieldTotal = 1,
            ),
        )

        val entity = listOf(finding).toDetectionEntities("sample-1").single()

        assertEquals(
            addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 0, stageKey = null),
            entity.detectionId,
        )
    }

    /**
     * Two different stages of one species is the case the stage segment exists for at all
     * (14zcqnthz6e), and it must keep getting distinct, stage-aware ids even with the fix above
     * that spares a lone staged card from an unnecessary id change.
     */
    @Test
    fun `two cards of the same species at different stages still get distinct stage-aware ids`() {
        val cf = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                stage = EggStage.CORTICATED_FERTILIZED,
                fieldTotal = 1,
            ),
        )
        val df = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                stage = EggStage.DECORTICATED_FERTILIZED,
                fieldTotal = 1,
            ),
        )

        val entities = listOf(cf, df).toDetectionEntities("sample-1")
        val cfEntity = entities.single { it.stage == EggStage.CORTICATED_FERTILIZED.name }
        val dfEntity = entities.single { it.stage == EggStage.DECORTICATED_FERTILIZED.name }

        assertEquals(
            addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 0, stageKey = "CORTICATED_FERTILIZED"),
            cfEntity.detectionId,
        )
        assertEquals(
            addedDetectionIdFor("sample-1", "Ascaris lumbricoides", 0, stageKey = "DECORTICATED_FERTILIZED"),
            dfEntity.detectionId,
        )
        assertTrue(cfEntity.detectionId != dfEntity.detectionId)
    }

    @Test
    fun `derived ids match the SQL derivation in 0004`() {
        val sampleId = "f14f3504-0d9f-433e-a0c2-c960a4e5801b"

        assertEquals("fb7c1fae-2937-34cf-8865-fd335255184a", detectionIdFor(sampleId, 0))
        assertEquals("0e614a6e-1045-3bb1-8302-b0a16a9a92a2", detectionIdFor(sampleId, 1))
    }

    @Test
    fun `a prediction and its detection derive different ids from one ordinal`() {
        assertTrue(predictionIdFor("sample-1", 0) != detectionIdFor("sample-1", 0))
        assertEquals(predictionIdFor("sample-1", 3), predictionIdFor("sample-1", 3))
        assertTrue(predictionIdFor("sample-1", 0) != predictionIdFor("sample-1", 1))
    }
}
