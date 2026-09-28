package com.agarthavision.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [SearchableDropdown], driven through its state/action seam — no Hilt
 * graph and no ViewModel. Runs on the JVM under Robolectric, inside the
 * `:app:testDebugUnitTest` task Husky already enforces.
 *
 * What is worth guarding here is the three-way empty state (too short / no matches /
 * results) and the swap from search field to selection, since that is what a small edit
 * breaks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SearchableDropdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `typing reports the query back to the caller`() {
        var typed: String? = null
        composeRule.setContent {
            Host(query = "", actions = SearchableDropdownActions({ typed = it }, {}, {}))
        }

        composeRule.onNodeWithText(PLACEHOLDER).performTextInput("lahug")

        assertEquals("lahug", typed)
    }

    @Test
    fun `shows the keep-typing hint while the query is too short`() {
        composeRule.setContent { Host(query = "l", options = emptyList()) }

        composeRule.onNodeWithText(HINT).assertIsDisplayed()
    }

    @Test
    fun `shows no-matches once the query is long enough`() {
        composeRule.setContent { Host(query = "zzzz", options = emptyList()) }

        composeRule.onNodeWithText(NO_MATCHES).assertIsDisplayed()
    }

    @Test
    fun `renders a result with its parent path`() {
        composeRule.setContent { Host(query = "lahug") }

        composeRule.onNodeWithText("Lahug").assertIsDisplayed()
        composeRule.onNodeWithText("City of Cebu · Region VII").assertIsDisplayed()
    }

    @Test
    fun `tapping a result selects it`() {
        var selected: SearchableOption? = null
        composeRule.setContent {
            Host(query = "lahug", actions = SearchableDropdownActions({}, { selected = it }, {}))
        }

        composeRule.onNodeWithText("Lahug").performClick()

        assertEquals(LAHUG, selected)
    }

    @Test
    fun `a selection replaces the search field`() {
        composeRule.setContent { Host(query = "", selected = LAHUG) }

        composeRule.onNodeWithText("Lahug").assertIsDisplayed()
        composeRule.onNodeWithText(PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun `clearing a selection reports back to the caller`() {
        var cleared = false
        composeRule.setContent {
            Host(query = "", selected = LAHUG, actions = SearchableDropdownActions({}, {}, { cleared = true }))
        }

        composeRule.onNodeWithContentDescription(CLEAR_LABEL).performClick()

        assertEquals(true, cleared)
    }

    // --- BringIntoViewRequester / focus-hoisting tests (added for the scroll-into-view change) ---

    /**
     * Tapping (focusing) the search field must not crash even though the component now sets up
     * a [BringIntoViewRequester] and two LaunchedEffects. In a Robolectric environment there is
     * no real scroll container to respond to bringIntoView, but the call must be a no-op rather
     * than throwing.
     */
    @Test
    fun `focusing the search field does not crash`() {
        composeRule.setContent { Host(query = "") }

        // performClick drives onFocusChanged(true) on BasicTextField → propagates to the outer
        // isFocused state → LaunchedEffect(isFocused) fires bringIntoView() harmlessly.
        composeRule.onNodeWithText(PLACEHOLDER).performClick()

        // If we reach here without an exception, the focus→bringIntoView path is safe.
        composeRule.onNodeWithText(PLACEHOLDER).assertIsDisplayed()
    }

    /**
     * Options arriving while the field is not focused must not crash — the second LaunchedEffect
     * is guarded by `if (isFocused)` and should short-circuit silently.
     */
    @Test
    fun `options changing before focus does not crash`() {
        var options by mutableStateOf(emptyList<SearchableOption>())
        composeRule.setContent {
            AgarthaVisionTheme {
                SearchableDropdown(
                    state = SearchableDropdownState(selected = null, query = "lahug", options = options),
                    config = CONFIG,
                    actions = SearchableDropdownActions({}, {}, {}),
                )
            }
        }

        // Deliver results while the field has never been focused.
        options = listOf(LAHUG)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lahug").assertIsDisplayed()
    }

    /**
     * Options arriving while the field IS focused must not crash — this exercises the
     * `LaunchedEffect(state.options)` branch where `isFocused == true` and
     * `bringIntoView()` is actually called.
     */
    @Test
    fun `options changing while focused does not crash`() {
        var options by mutableStateOf(emptyList<SearchableOption>())
        composeRule.setContent {
            AgarthaVisionTheme {
                SearchableDropdown(
                    state = SearchableDropdownState(selected = null, query = "lahug", options = options),
                    config = CONFIG,
                    actions = SearchableDropdownActions({}, {}, {}),
                )
            }
        }

        // Focus first so isFocused becomes true in the outer composable. The query is already
        // "lahug" so the placeholder isn't rendered; use hasSetTextAction() to find the field.
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitForIdle()

        // Now deliver results — LaunchedEffect(state.options) fires with isFocused == true.
        options = listOf(LAHUG)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lahug").assertIsDisplayed()
    }

    /**
     * Verifies that the default `onFocusChanged` no-op parameter on `SearchField` doesn't
     * affect callers that do NOT pass the argument — existing call sites (PatientFormSheet)
     * are unaffected. This is essentially a compile-time guarantee, but exercising it at
     * runtime confirms the default wires a lambda that does nothing.
     */
    @Test
    fun `SearchField default onFocusChanged no-op is inert`() {
        // Host() never passes onFocusChanged to SearchableDropdown — it goes straight to the
        // private SearchField's default. Interacting with the field must still work normally.
        var typed: String? = null
        composeRule.setContent {
            Host(query = "", actions = SearchableDropdownActions({ typed = it }, {}, {}))
        }

        composeRule.onNodeWithText(PLACEHOLDER).performClick()
        composeRule.onNodeWithText(PLACEHOLDER).performTextInput("test")

        assertEquals("test", typed)
    }

    @Composable
    private fun Host(
        query: String,
        selected: SearchableOption? = null,
        options: List<SearchableOption> = listOf(LAHUG),
        actions: SearchableDropdownActions = SearchableDropdownActions({}, {}, {}),
    ) {
        AgarthaVisionTheme {
            SearchableDropdown(
                state = SearchableDropdownState(
                    selected = selected,
                    query = query,
                    options = options,
                ),
                config = SearchableDropdownConfig(
                    label = "Patient barangay",
                    placeholder = PLACEHOLDER,
                    hint = HINT,
                    noMatches = NO_MATCHES,
                    clearLabel = CLEAR_LABEL,
                    minQueryLength = 2,
                ),
                actions = actions,
            )
        }
    }

    private companion object {
        private const val PLACEHOLDER = "Search barangay, city or province"
        private const val HINT = "Type at least 2 characters to search"
        private const val NO_MATCHES = "No barangay matches that search"
        private const val CLEAR_LABEL = "Clear the selected barangay"

        private val LAHUG = SearchableOption(
            key = "0730600051",
            title = "Lahug",
            subtitle = "City of Cebu · Region VII",
        )

        private val CONFIG = SearchableDropdownConfig(
            label = "Patient barangay",
            placeholder = PLACEHOLDER,
            hint = HINT,
            noMatches = NO_MATCHES,
            clearLabel = CLEAR_LABEL,
            minQueryLength = 2,
        )
    }
}
