package com.agarthavision.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose unit tests for [SheetInput]'s maxLength clamping and character counter.
 *
 * Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 *
 * [SheetInput] exposes no test tag; the single `hasSetTextAction()` selector is sufficient
 * because each test renders exactly one input field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SheetInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Renders a single [SheetInput] whose value is backed by a [mutableStateOf], and
     * returns a lambda that reads the current state value.
     */
    private fun setContent(
        initial: String = "",
        maxLength: Int = 40,
    ): () -> String {
        var value by mutableStateOf(initial)
        composeRule.setContent {
            AgarthaVisionTheme {
                SheetInput(
                    value = value,
                    onValueChange = { value = it },
                    config = SheetInputConfig(
                        label = "Label",
                        placeholder = "placeholder",
                        isError = false,
                        maxLength = maxLength,
                    ),
                )
            }
        }
        return { value }
    }

    /**
     * Stops the main clock before typing so the caret-blink animation does not prevent
     * `waitForIdle` from returning. Pattern lifted from [VerificationSheetContentTest].
     */
    private fun typeInto(text: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    // ── Clamping ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `typing a 45-char string into a maxLength-40 field leaves value at exactly 40 chars`() {
        val getValue = setContent(initial = "", maxLength = 40)

        typeInto("a".repeat(45))

        assertEquals(40, getValue().length)
        assertEquals("a".repeat(40), getValue())
    }

    /**
     * A full field rejects any edit that would exceed the cap, wherever the cursor sits.
     * Seeding `initial` via `mutableStateOf` places the cursor at position 0 in Robolectric,
     * so the extra character is inserted at the front - a clamp that truncated the tail
     * would silently mutate the value here; [limitInput] leaves it untouched.
     */
    @Test
    fun `typing one extra char when already at cap leaves value unchanged`() {
        val full = "b".repeat(40)
        val getValue = setContent(initial = full, maxLength = 40)

        composeRule.onNode(hasSetTextAction()).performTextInput("x")

        assertEquals(full, getValue())
    }

    // ── Counter display ────────────────────────────────────────────────────────────────────

    @Test
    fun `counter shows correct text for a 10-char value against maxLength 40`() {
        setContent(initial = "c".repeat(10), maxLength = 40)

        val expected = context.getString(R.string.session_new_char_counter, 10, 40)
        composeRule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `counter shows cap text when value equals maxLength`() {
        setContent(initial = "d".repeat(40), maxLength = 40)

        val expected = context.getString(R.string.session_new_char_counter, 40, 40)
        composeRule.onNodeWithText(expected).assertExists()
    }
}
