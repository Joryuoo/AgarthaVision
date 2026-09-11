package com.agarthavision.data.local.mapper

import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val entity = prediction.toDetectionEntity("sample-1", answers)
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
        val entity = prediction.toDetectionEntity("sample-1", answers)
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
        val entity = prediction.toDetectionEntity("sample-1", answers)
        assertNull(entity.expertClass)
        assertEquals(DetectionVerdict.CONFIRMED.value, entity.verdict)
    }

    @Test
    fun `stage carries through to entity when set`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
            stage = EggStage.UNFERTILIZED,
        )
        val entity = prediction.toDetectionEntity("sample-1", answers)
        assertEquals(EggStage.UNFERTILIZED.value, entity.stage)
    }

    @Test
    fun `stage is null on entity when unset`() {
        val answers = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val entity = prediction.toDetectionEntity("sample-1", answers)
        assertNull(entity.stage)
    }
}
