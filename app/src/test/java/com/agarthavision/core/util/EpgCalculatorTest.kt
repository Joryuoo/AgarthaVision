package com.agarthavision.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgCalculatorTest {
    @Test
    fun `epg multiplies egg count by multiplier`() {
        assertEquals(24, EpgCalculator.epg(1))
        assertEquals(240, EpgCalculator.epg(10))
    }
}
