package com.agarthavision.ui.verify

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Verification Screen's box colour rule: **current is coloured, every other box is gray**.
 *
 * Worth pinning rather than reading off the composable, because the rule this replaced gave the
 * other boxes `accent` — a second colour that also read as meaningful, competing with the one box
 * the medtech was answering questions about. A regression here is invisible to every
 * semantics-based test in the suite and changes nothing that throws.
 */
class BoxColorRuleTest {

    private val active = Color.Red
    private val inactive = Color.Gray

    private fun colorsFor(count: Int, highlighted: Int): List<Color> =
        (0 until count).map { boxColorAt(it, highlighted, active, inactive) }

    @Test
    fun `exactly one box is coloured and the rest are gray`() {
        assertEquals(listOf(inactive, active, inactive), colorsFor(count = 3, highlighted = 1))
    }

    @Test
    fun `cycling moves the colour and leaves the others gray throughout`() {
        assertEquals(listOf(active, inactive, inactive), colorsFor(count = 3, highlighted = 0))
        assertEquals(listOf(inactive, active, inactive), colorsFor(count = 3, highlighted = 1))
        assertEquals(listOf(inactive, inactive, active), colorsFor(count = 3, highlighted = 2))
    }

    /**
     * The case that is easy to get wrong.
     *
     * Once a medtech adds an egg through Add Egg, the answer list is longer than the prediction
     * list and the current index runs past its end. No box is coloured, which is right: the
     * subject of the questions is a finding with no drawn box, and colouring an arbitrary
     * prediction instead would point the medtech at the wrong specimen. A `coerceIn` here — the
     * obvious-looking safety net — is precisely the bug.
     */
    @Test
    fun `an index past the predictions colours nothing`() {
        assertEquals(listOf(inactive, inactive), colorsFor(count = 2, highlighted = 2))
        assertEquals(listOf(inactive, inactive), colorsFor(count = 2, highlighted = 7))
    }

    @Test
    fun `a negative index colours nothing`() {
        assertEquals(listOf(inactive, inactive), colorsFor(count = 2, highlighted = -1))
    }
}
