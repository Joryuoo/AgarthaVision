package com.agarthavision.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class PositiveRateBinTest {

    @Test
    fun `boundary values`() {
        assertEquals(0, PositiveRateBin.of(0.0))
        assertEquals(1, PositiveRateBin.of(0.0999999))
        assertEquals(2, PositiveRateBin.of(0.10))
        assertEquals(2, PositiveRateBin.of(0.1999999))
        assertEquals(3, PositiveRateBin.of(0.20))
        assertEquals(3, PositiveRateBin.of(0.3999999))
        assertEquals(4, PositiveRateBin.of(0.40))
        assertEquals(4, PositiveRateBin.of(1.0))
    }

    @Test
    fun `just below zero still bins as 0 given real rates never go negative`() {
        // Defensive: a rate can't be negative in practice, but the <= 0.0 branch should not
        // misbehave if it ever is.
        assertEquals(0, PositiveRateBin.of(-0.01))
    }
}
