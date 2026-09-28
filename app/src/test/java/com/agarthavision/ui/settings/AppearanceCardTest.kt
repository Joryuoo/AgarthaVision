package com.agarthavision.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [AppearanceCard].
 *
 * Verifies that the Theme card renders the Light, Dark, and System options,
 * and clicking each option dispatches the appropriate [ThemeMode] selection.
 *
 * Runs on the JVM under Robolectric inside `:app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppearanceCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme card displays theme label and all three options`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(themeMode = ThemeMode.LIGHT, onSelectTheme = {})
            }
        }

        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
        composeRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun `clicking dark option invokes onSelectTheme with DARK`() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(themeMode = ThemeMode.LIGHT, onSelectTheme = { selectedMode = it })
            }
        }

        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.DARK, selectedMode)
    }

    @Test
    fun `clicking light option invokes onSelectTheme with LIGHT`() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(themeMode = ThemeMode.DARK, onSelectTheme = { selectedMode = it })
            }
        }

        composeRule.onNodeWithText("Light").performClick()
        assertEquals(ThemeMode.LIGHT, selectedMode)
    }

    @Test
    fun `clicking system option invokes onSelectTheme with SYSTEM`() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                AppearanceCard(themeMode = ThemeMode.LIGHT, onSelectTheme = { selectedMode = it })
            }
        }

        composeRule.onNodeWithText("System").performClick()
        assertEquals(ThemeMode.SYSTEM, selectedMode)
    }
}
