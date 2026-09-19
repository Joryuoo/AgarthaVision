package com.agarthavision.domain.usecase.verify

import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
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
            ),
        )
        assertTrue(boxWrongWithSpecies.isComplete)
    }

    @Test
    fun `OTHER needs its free text`() {
        val blank = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.OTHER,
        )
        assertFalse(Finding(prediction(), blank).isComplete)
        assertTrue(Finding(prediction(), blank.copy(otherSpeciesText = "Enterobius")).isComplete)
    }

    @Test
    fun `an added finding needs a species and a positive total, and asks no box questions`() {
        val speciesOnly = VerificationAnswers(species = EggSpecies.ASCARIS)
        assertFalse("No total yet.", Finding(answers = speciesOnly).isComplete)
        assertFalse("Zero is not a total.", Finding(answers = speciesOnly.copy(fieldTotal = 0)).isComplete)
        // isEgg / isBoxCorrect are never asked of a row with no box, and their absence must
        // not block it.
        assertTrue(Finding(answers = speciesOnly.copy(fieldTotal = 3)).isComplete)
    }

    // ── what a row is worth ─────────────────────────────────────────────────

    @Test
    fun `only a kept box counts as an egg`() {
        val confirmed = Finding(
            prediction(),
            VerificationAnswers(
                isEgg = true,
                isBoxCorrect = true,
                species = EggSpecies.ASCARIS,
                // A total on a box row is meaningless - the box is one egg, and the medtech
                // does not get to edit that.
                fieldTotal = 99,
            ),
        )
        assertTrue(confirmed.countsAsEgg)
        assertFalse(Finding(prediction(), VerificationAnswers(isEgg = false)).countsAsEgg)
        // An added row is never "one egg" - it speaks for its whole species.
        assertFalse(Finding(answers = VerificationAnswers(fieldTotal = 3)).countsAsEgg)
    }

    // ── grouping ────────────────────────────────────────────────────────────

    @Test
    fun `an added total speaks for the whole species, boxes included`() {
        // The regression this guards: the total used to be added to the boxes, so a medtech
        // who counted 5 Ascaris against 2 boxed ones got a row of 7.
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val findings = listOf(
            Finding(prediction(), ascaris),
            Finding(prediction(), ascaris),
            // The medtech counted five Ascaris in the field, two of which the model boxed.
            Finding(answers = ascaris.copy(fieldTotal = 5)),
        )

        val rows = findings.toFindingRows()

        assertEquals(1, rows.size)
        assertEquals("Ascaris lumbricoides", rows[0].species)
        assertEquals(5, rows[0].eggCount)
        assertEquals(2, findings.boxedCountOf("Ascaris lumbricoides"))
        assertEquals(3, findings.unboxedCountOf("Ascaris lumbricoides"))
    }

    @Test
    fun `a species with no total of its own falls back to its boxes`() {
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val findings = listOf(Finding(prediction(), ascaris), Finding(prediction(), ascaris))

        assertEquals(2, findings.fieldTotalOf("Ascaris lumbricoides"))
        assertEquals(0, findings.unboxedCountOf("Ascaris lumbricoides"))
        assertEquals(2, findings.toFindingRows()[0].eggCount)
    }

    @Test
    fun `a total below the boxes and drawn eggs holds submit`() {
        val ascaris = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.ASCARIS,
        )
        val drawn = ImageBox(x = 5f, y = 5f, width = 2f, height = 2f)
        val findings = listOf(
            Finding(prediction(), ascaris),
            Finding(prediction(), ascaris),
            Finding(answers = ascaris.copy(fieldTotal = 4, drawnBoxes = listOf(drawn))),
        )

        // Two boxes kept plus one egg the medtech located by hand.
        assertEquals(3, findings.floorFor("Ascaris lumbricoides"))
        assertTrue(findings.totalsAreConsistent())

        val lowered = findings.map { finding ->
            if (finding.prediction == null) {
                finding.copy(answers = finding.answers.copy(fieldTotal = 2))
            } else {
                finding
            }
        }
        assertFalse(
            "Two eggs cannot be fewer than the two boxed plus the one drawn.",
            lowered.totalsAreConsistent(),
        )
    }

    @Test
    fun `the row key is the species alone`() {
        // This used to assert the opposite - two rows, split by developmental stage. 86d4a6jwy
        // was reverted on staging (9dcfd5d) and the stage went with it, so two boxes of the
        // same species are now one row of two eggs. `sample_species_findings.stage` survives
        // as a dormant column (C6: 0012 is applied), and if the ticket returns this test and
        // the id derivation in VerificationMapper have to move together.
        val hookworm = VerificationAnswers(
            isEgg = true,
            isBoxCorrect = true,
            species = EggSpecies.HOOKWORM,
        )
        val rows = listOf(
            Finding(prediction("Hookworm"), hookworm),
            Finding(prediction("Hookworm"), hookworm),
        ).toFindingRows()

        assertEquals(1, rows.size)
        assertEquals(2, rows[0].eggCount)
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
                ),
            ),
            Finding(
                prediction("Hookworm"),
                VerificationAnswers(
                    isEgg = true,
                    isBoxCorrect = true,
                    species = EggSpecies.HOOKWORM,
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
