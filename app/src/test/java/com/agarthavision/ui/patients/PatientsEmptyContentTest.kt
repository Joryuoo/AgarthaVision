package com.agarthavision.ui.patients

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PatientsEmptyContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(narrowed: Boolean, downloading: Boolean) {
        composeRule.setContent {
            AgarthaVisionTheme {
                PatientsEmptyContent(narrowed = narrowed, downloading = downloading, onCreatePatient = {})
            }
        }
    }

    @Test
    fun `empty unfiltered and still downloading says so and offers no create action`() {
        show(narrowed = false, downloading = true)

        composeRule.onNodeWithText("Your patients are still downloading").assertIsDisplayed()
        composeRule.onNodeWithText("No patients yet").assertDoesNotExist()
        composeRule.onNodeWithText("New patient", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `empty and download done shows the normal empty state with create action`() {
        show(narrowed = false, downloading = false)

        composeRule.onNodeWithText("No patients yet").assertIsDisplayed()
        composeRule.onNodeWithText("Your patients are still downloading").assertDoesNotExist()
        composeRule.onNodeWithText("New patient", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `empty and filtered keeps the filtered empty state`() {
        show(narrowed = true, downloading = false)

        composeRule.onNodeWithText("No matching patients").assertIsDisplayed()
        composeRule.onNodeWithText("Your patients are still downloading").assertDoesNotExist()
        composeRule.onNodeWithText("New patient", useUnmergedTree = true).assertDoesNotExist()
    }
}
