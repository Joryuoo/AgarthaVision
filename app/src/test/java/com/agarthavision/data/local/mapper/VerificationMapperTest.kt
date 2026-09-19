package com.agarthavision.data.local.mapper

import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
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
        val entity = Finding(prediction, answers).toDetectionEntity("sample-1", ordinal = 0)
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
        val entity = Finding(prediction, answers).toDetectionEntity("sample-1", ordinal = 0)
        assertEquals(DetectionVerdict.WRONG_CLASS.value, entity.verdict)
        assertEquals("Enterobius", entity.expertClass)
    }

    @Test
    fun `confirmed entity has null expertClass and correct verdict`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val entity = Finding(prediction, answers).toDetectionEntity("sample-1", ordinal = 0)
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
                speciesTouched = true,
                drawnBox = drawn,
                boxReplaced = true,
            ),
        )

        val entity = finding.toDetectionEntity("sample-1", ordinal = 0)

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
                speciesTouched = true,
                drawnBox = ImageBox(1f, 2f, 3f, 4f),
                boxReplaced = true,
            ),
        )

        assertEquals(0.9f, finding.toDetectionEntity("sample-1", ordinal = 0).confidence)
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
                speciesTouched = true,
            ),
        )

        val entity = finding.toDetectionEntity("sample-1", ordinal = 0)

        assertEquals(prediction.x, entity.bboxX)
        assertEquals(prediction.y, entity.bboxY)
        assertEquals(prediction.width, entity.bboxW)
        assertEquals(prediction.height, entity.bboxH)
    }

    /**
     * An added egg the medtech drew a box for is still a human assertion: confidence 1.0 and
     * species_touched true, which is what distinguishes it in the corpus from a model box.
     */
    @Test
    fun `an added egg with a drawn box is written as a human assertion`() {
        val drawn = ImageBox(x = 90f, y = 90f, width = 20f, height = 20f)
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.HOOKWORM,
                eggCount = 1,
                speciesTouched = true,
                drawnBox = drawn,
            ),
        )

        val entity = finding.toDetectionEntity("sample-1", ordinal = 0)

        assertEquals(1.0f, entity.confidence)
        assertTrue(entity.speciesTouched)
        assertEquals(90f, entity.bboxX)
    }

    /** And an added egg with no box still writes null geometry, which the column allows. */
    @Test
    fun `an added egg with no box writes no geometry`() {
        val finding = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.HOOKWORM,
                eggCount = 1,
                speciesTouched = true,
            ),
        )

        val entity = finding.toDetectionEntity("sample-1", ordinal = 0)

        assertNull(entity.bboxX)
        assertNull(entity.bboxY)
    }
}
