package com.agarthavision.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
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
            Host(query = "", onQueryChange = { typed = it })
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
            Host(query = "lahug", onSelect = { selected = it })
        }

        composeRule.onNodeWithText("Lahug").performClick()

        assertEquals(LAHUG, selected)
    }

    @Test
    fun `a selection replaces the search field`() {
        composeRule.setContent { Host(selected = LAHUG, query = "") }

        composeRule.onNodeWithText("Lahug").assertIsDisplayed()
        composeRule.onNodeWithText(PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun `clearing a selection reports back to the caller`() {
        var cleared = false
        composeRule.setContent {
            Host(selected = LAHUG, query = "", onClear = { cleared = true })
        }

        composeRule.onNodeWithContentDescription(CLEAR_LABEL).performClick()

        assertEquals(true, cleared)
    }

    @Composable
    private fun Host(
        selected: SearchableOption? = null,
        query: String,
        options: List<SearchableOption> = listOf(LAHUG),
        onQueryChange: (String) -> Unit = {},
        onSelect: (SearchableOption) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        AgarthaVisionTheme {
            SearchableDropdown(
                selected = selected,
                query = query,
                options = options,
                config = SearchableDropdownConfig(
                    label = "Patient barangay",
                    placeholder = PLACEHOLDER,
                    hint = HINT,
                    noMatches = NO_MATCHES,
                    clearLabel = CLEAR_LABEL,
                    minQueryLength = 2,
                ),
                actions = SearchableDropdownActions(
                    onQueryChange = onQueryChange,
                    onSelect = onSelect,
                    onClear = onClear,
                ),
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
    }
}
