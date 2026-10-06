package com.agarthavision.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun isAnimationOff_returnsTrueWhenZero() {
        assertTrue(isAnimationOff(0f))
    }

    @Test
    fun isAnimationOff_returnsFalseWhenNonZero() {
        assertFalse(isAnimationOff(1f))
    }
}
