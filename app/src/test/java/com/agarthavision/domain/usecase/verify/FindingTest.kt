package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.EggStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingTest {

    private fun prediction(label: String = "Ascaris") = Prediction(
        classLabel = label,
        confidence = 0.9f,
        x = 1f,
        y = 2f,
        width = 3f,
        height = 4f,
    )

    // ── completeness ────────────────────────────────────────────────────────

    @Test
    fun `a rejected box is complete with nothing else answered`() {
        // Saying "not an egg" ends the questions: there is no species to name.
        assertTrue(Finding(prediction(), VerificationAnswers(isEgg = false)).isComplete)
    }

    @Test
    fun `a misplaced box still demands a species`() {
        // Regression guard. isComplete used to short-circuit on isBoxCorrect = false, which
        // under a counting model silently dropped a real egg from the low-power-field count.
        val boxWrongNoSpecies = Finding(
            prediction(),
            VerificationAnswers(isEgg = true, isBoxCorrect = false),
        )
        assertFalse(
            "A misplaced box still contains a countable egg - the species question stands.",
            boxWrongNoSpecies.isComplete,
        )

        val boxWrongWithSpecies = Finding(
            prediction(),
            VerificationAnswers(
                isEgg = true,
                isBoxCorrect = false,
                species = EggSpecies.ASCARIS,
                stage = EggStage.UNFERTILIZED,
            ),
        )
        assertTrue(boxWrongWithSpecies.isComplete)
    }

    @Test
    fun `a stage is optional and never blocks submit`() {
        // Offered wherever the species defines one, persisted when given, but not a gate -
        // the rule 86d4a6jwy set when the dropdown was introduced. The reading that matters
        // for surveillance is the infectivity level, tracked separately (86d3fzd28).
        val noStage = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.TRICHURIS,
        )
        assertTrue(Finding(prediction(), noStage).isComplete)
        assertTrue(
            Finding(prediction(), noStage.copy(stage = EggStage.EMBRYONATED)).isComplete,
        )
    }

    @Test
    fun `OTHER needs its free text`() {
        // EggStage.validFor(OTHER) is empty, so there is no stage question to answer.
        val blank = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.OTHER,
        )
        assertFalse(Finding(prediction(), blank).isComplete)
        assertTrue(Finding(prediction(), blank.copy(otherSpeciesText = "Enterobius")).isComplete)
    }

    @Test
    fun `an added finding needs a species and a positive count, and asks no box questions`() {
        val speciesOnly = VerificationAnswers(species = EggSpecies.ASCARIS)
        assertFalse("No count yet.", Finding(answers = speciesOnly).isComplete)
        assertFalse("Zero is not a count.", Finding(answers = speciesOnly.copy(eggCount = 0)).isComplete)
        // isEgg / isBoxCorrect are never asked of a row with no box, and their absence must
        // not block it.
        assertTrue(Finding(answers = speciesOnly.copy(eggCount = 3)).isComplete)
    }

    // ── egg contribution ────────────────────────────────────────────────────

    @Test
    fun `a confirmed box is worth exactly one egg and a rejected box none`() {
        val confirmed = Finding(
            prediction(),
            VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = EggSpecies.ASCARIS,
                stage = EggStage.UNFERTILIZED,
                // A typed count on a box row is ignored - the box is one egg.
                eggCount = 99,
            ),
        )
        assertEquals(1, confirmed.eggContribution)
        assertEquals(0, Finding(prediction(), VerificationAnswers(isEgg = false)).eggContribution)
    }

    // ── grouping ────────────────────────────────────────────────────────────

    @Test
    fun `boxes and added rows of the same species and stage sum into one row`() {
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
            stage = EggStage.UNFERTILIZED,
        )
        val findings = listOf(
            Finding(prediction(), ascaris),
            Finding(prediction(), ascaris),
            // The medtech saw three more Ascaris the model never boxed.
            Finding(answers = ascaris.copy(eggCount = 3)),
        )

        val rows = findings.toFindingRows()

        assertEquals(1, rows.size)
        assertEquals("Ascaris lumbricoides", rows[0].species)
        assertEquals(EggStage.UNFERTILIZED, rows[0].stage)
        assertEquals(5, rows[0].eggCount)
    }

    @Test
    fun `the same species at different stages stays two rows`() {
        // The whole point of the table: a stage is part of the key, not a detail.
        val hookworm = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.HOOKWORM,
        )
        val rows = listOf(
            Finding(prediction("Hookworm"), hookworm.copy(stage = EggStage.UNEMBRYONATED)),
            Finding(prediction("Hookworm"), hookworm.copy(stage = EggStage.LARVATED)),
        ).toFindingRows()

        assertEquals(2, rows.size)
        assertEquals(setOf(EggStage.UNEMBRYONATED, EggStage.LARVATED), rows.map { it.stage }.toSet())
    }

    @Test
    fun `a mixed field produces one row per species`() {
        val rows = listOf(
            Finding(
                prediction("Ascaris"),
                VerificationAnswers(
                    isEgg = true,
                    isBoxCorrect = true,
                    species = EggSpecies.ASCARIS,
                    stage = EggStage.UNFERTILIZED,
                ),
            ),
            Finding(
                prediction("Hookworm"),
                VerificationAnswers(
                    isEgg = true,
                    isBoxCorrect = true,
                    species = EggSpecies.HOOKWORM,
                    stage = EggStage.LARVATED,
                ),
            ),
        ).toFindingRows()

        assertEquals(2, rows.size)
        // Sorted by species, so the order is stable for display and for the id derivation.
        assertEquals(listOf("Ascaris lumbricoides", "Hookworm"), rows.map { it.species })
    }

    @Test
    fun `a rejected box leaves no findings row`() {
        // It still persists as a labelled FALSE_POSITIVE detection - it just counts nothing.
        assertTrue(listOf(Finding(prediction(), VerificationAnswers(isEgg = false))).toFindingRows().isEmpty())
    }

    @Test
    fun `a clean field produces no rows at all`() {
        // Zero rows is how a clean field is recorded. There is no sentinel species.
        assertTrue(emptyList<Finding>().toFindingRows().isEmpty())
    }

    @Test
    fun `free-text species groups under its typed label`() {
        val other = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.OTHER,
            otherSpeciesText = "  Enterobius  ",
        )
        val rows = listOf(Finding(prediction(), other)).toFindingRows()
        assertEquals(1, rows.size)
        assertEquals("Enterobius", rows[0].species)
    }
}
