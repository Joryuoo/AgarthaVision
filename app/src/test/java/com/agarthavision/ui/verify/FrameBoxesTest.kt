package com.agarthavision.ui.verify

import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.Finding
import com.agarthavision.domain.usecase.verify.VerificationAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Verification Screen actually draws on the frame.
 *
 * This replaced `BoxColorRuleTest`, which pinned a colour lookup keyed on an index into
 * `frame.predictions`. That rule outlived its subject: boxes no longer come from one list, so an
 * index cannot address them, and the colour was never the part that was broken — **the
 * hand-drawn boxes were not in the list at all** (86d4by5n4). Its one irreplaceable case, an
 * active index past the model's boxes colouring nothing, is kept below.
 *
 * Worth pinning rather than reading off the composable: every rule here is invisible to a
 * semantics-based test and none of them throws when wrong.
 */
class FrameBoxesTest {

    private fun prediction(x: Float = 10f) =
        Prediction(classLabel = "ascaris", confidence = 0.9f, x = x, y = 10f, width = 4f, height = 4f)

    private fun box(x: Float) = ImageBox(x = x, y = 50f, width = 6f, height = 6f)

    private fun kept(species: EggSpecies = EggSpecies.ASCARIS) = VerificationAnswers(
        isEgg = true,
        isBoxCorrect = true,
        species = species,
    )

    // --- whose geometry gets drawn -------------------------------------------------------

    @Test
    fun `a kept model box is drawn solid from the model's own geometry`() {
        val boxes = listOf(Finding(prediction(x = 12f), kept())).frameBoxes(active = null)

        assertEquals(1, boxes.size)
        assertEquals(12f, boxes[0].box.x, 0f)
        assertTrue(boxes[0].trusted)
    }

    /**
     * The case that made replacing a box look like it had done nothing: the drawn box was
     * invisible *and* the model's original stayed on screen in its wrong place, which reads as
     * authoritative rather than as missing.
     */
    @Test
    fun `a replaced box shows the drawn geometry and hides the model's`() {
        val answers = kept().copy(isBoxCorrect = false, boxReplaced = true, drawnBox = box(x = 80f))
        val boxes = listOf(Finding(prediction(x = 12f), answers)).frameBoxes(active = null)

        assertEquals(listOf(80f), boxes.map { it.box.x })
        assertTrue(boxes.single().trusted)
    }

    @Test
    fun `a box called misplaced but never redrawn stays on the frame, dashed`() {
        val answers = kept().copy(isBoxCorrect = false)
        val boxes = listOf(Finding(prediction(), answers)).frameBoxes(active = null)

        assertEquals(1, boxes.size)
        assertEquals(false, boxes.single().trusted)
    }

    @Test
    fun `a rejected detection stays on the frame, dashed`() {
        val boxes = listOf(Finding(prediction(), VerificationAnswers(isEgg = false)))
            .frameBoxes(active = null)

        assertEquals(1, boxes.size)
        assertEquals(false, boxes.single().trusted)
    }

    /**
     * Rejecting the detection outranks having replaced its box. A medtech who redraws and then
     * unchecks "there is an egg here" has disowned the row; drawing their rectangle solid would
     * state the opposite of what they last said.
     */
    @Test
    fun `rejecting a detection after redrawing it still dashes the box`() {
        val answers = VerificationAnswers(isEgg = false, isBoxCorrect = false, drawnBox = box(x = 80f))
        val boxes = listOf(Finding(prediction(), answers)).frameBoxes(active = null)

        assertEquals(false, boxes.single().trusted)
    }

    @Test
    fun `an unanswered model box is solid, because nobody has rejected it`() {
        val boxes = listOf(Finding(prediction(), VerificationAnswers())).frameBoxes(active = null)

        assertTrue(boxes.single().trusted)
    }

    // --- added species -------------------------------------------------------------------

    /** The regression the ticket is named for: two eggs of one species, two distinct boxes. */
    @Test
    fun `two eggs located on one added species produce two distinct boxes`() {
        val added = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                fieldTotal = 3,
                drawnBoxes = listOf(box(x = 20f), box(x = 60f)),
            ),
        )

        val boxes = listOf(added).frameBoxes(active = null)

        assertEquals(listOf(20f, 60f), boxes.map { it.box.x })
        assertTrue(boxes.all { it.trusted })
    }

    @Test
    fun `an added egg with no box drawn contributes nothing and is not an error`() {
        val added = Finding(
            prediction = null,
            answers = VerificationAnswers(species = EggSpecies.ASCARIS, fieldTotal = 4),
        )

        assertEquals(emptyList<FrameBox>(), listOf(added).frameBoxes(active = null))
    }

    /**
     * A total typed below what the frame already holds is a contradiction submit refuses rather
     * than clamps, so it does reach the renderer. A drawn box must not blink out of existence
     * while the medtech is mid-keystroke — that is the exact failure this ticket ends.
     */
    @Test
    fun `a total below the drawn boxes still draws every one of them`() {
        val added = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                fieldTotal = 1,
                drawnBoxes = listOf(box(x = 20f), box(x = 60f)),
            ),
        )

        assertEquals(2, listOf(added).frameBoxes(active = null).size)
    }

    // --- which one is coloured -----------------------------------------------------------

    @Test
    fun `exactly one box is active and the rest are not`() {
        val findings = List(3) { Finding(prediction(x = it.toFloat()), kept()) }

        val boxes = findings.frameBoxes(active = DrawTarget(findingIndex = 1))

        assertEquals(listOf(false, true, false), boxes.map { it.active })
    }

    /**
     * Kept from `BoxColorRuleTest`. Once a medtech adds a species, the finding list is longer
     * than the model's box list and the current detection index can run past its end. Nothing is
     * coloured, which is right: colouring an arbitrary box instead would point the medtech at the
     * wrong specimen. A `coerceIn` here — the obvious-looking safety net — is precisely the bug.
     */
    @Test
    fun `an active index past the model's boxes colours nothing`() {
        val findings = List(2) { Finding(prediction(x = it.toFloat()), kept()) }

        assertTrue(findings.frameBoxes(active = DrawTarget(findingIndex = 7)).none { it.active })
        assertTrue(findings.frameBoxes(active = DrawTarget(findingIndex = -1)).none { it.active })
    }

    @Test
    fun `drawing dims every existing box`() {
        val findings = List(3) { Finding(prediction(x = it.toFloat()), kept()) }

        assertTrue(findings.frameBoxes(active = null).none { it.active })
    }

    /** A slot address colours that egg, not the whole species and not the row's first box. */
    @Test
    fun `a slot address colours only that egg`() {
        val added = Finding(
            prediction = null,
            answers = VerificationAnswers(
                species = EggSpecies.ASCARIS,
                fieldTotal = 3,
                drawnBoxes = listOf(box(x = 20f), box(x = 60f)),
            ),
        )

        val boxes = listOf(added).frameBoxes(active = DrawTarget(findingIndex = 0, slot = 1))

        assertEquals(listOf(false, true), boxes.map { it.active })
    }

    /** A model box is addressed with a null slot, so a slot address must not match it. */
    @Test
    fun `a slot address never colours a model box`() {
        val findings = listOf(Finding(prediction(), kept()))

        val boxes = findings.frameBoxes(active = DrawTarget(findingIndex = 0, slot = 0))

        assertTrue(boxes.none { it.active })
    }
}
