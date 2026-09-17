package com.agarthavision.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class EpgCalculatorTest {
    @Test
    fun `epg returns count multiplied by 24`() {
        assertEquals(24, EpgCalculator.epg(1))
        assertEquals(240, EpgCalculator.epg(10))
    }
}
