package com.agarthavision.ui.records

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.agarthavision.domain.model.LpfDensity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LpfFormattingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `single value formats correctly when min equals max`() {
        var result = ""
        composeRule.setContent {
            result = LpfDensity(min = 3, max = 3).displayText()
        }
        assertEquals("3 LPF", result)
    }

    @Test
    fun `range value formats correctly when min does not equal max`() {
        var result = ""
        composeRule.setContent {
            result = LpfDensity(min = 1, max = 3).displayText()
        }
        assertEquals("1\u20133 LPF", result)
    }
}
