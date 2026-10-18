package com.agarthavision.ui.records

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for Session Detail's LPF card (14zcqntk7bg).
 *
 * A session exists from the moment it is named, before any field is verified. "No parasites
 * found" is a negative result, so it may only appear once a field has been read and nothing was
 * counted; before that the card says there is no result yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LpfHeroCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a session with no verified field makes no result claim`() {
        render(samplesTotal = 0)

        composeRule.onNodeWithTag(SessionDetailTestTags.NOT_READ).assertExists()
        composeRule.onNodeWithTag(SessionDetailTestTags.NO_PARASITES).assertDoesNotExist()
        composeRule.onNodeWithText("No parasites found").assertDoesNotExist()
        composeRule.onNodeWithText("No confirmed eggs in 0 fields").assertDoesNotExist()
        composeRule.onNodeWithText("No fields verified yet").assertExists()
    }

    @Test
    fun `a verified session with nothing counted still says no parasites found`() {
        render(samplesTotal = 2)

        composeRule.onNodeWithTag(SessionDetailTestTags.NO_PARASITES).assertExists()
        composeRule.onNodeWithText("No parasites found").assertExists()
        composeRule.onNodeWithText("No confirmed eggs in 2 fields").assertExists()
        composeRule.onNodeWithTag(SessionDetailTestTags.NOT_READ).assertDoesNotExist()
    }

    @Test
    fun `a session with detections shows its ranges and neither statement`() {
        render(
            samplesTotal = 3,
            confirmedEggs = 4,
            lpfPerSpecies = mapOf(ASCARIS to LpfDensity(min = 0, max = 2)),
        )

        composeRule.onNodeWithTag(SessionDetailTestTags.lpfRow(ASCARIS)).assertExists()
        composeRule.onNodeWithTag(SessionDetailTestTags.NO_PARASITES).assertDoesNotExist()
        composeRule.onNodeWithTag(SessionDetailTestTags.NOT_READ).assertDoesNotExist()
    }

    private fun render(
        samplesTotal: Int,
        confirmedEggs: Int = 0,
        lpfPerSpecies: Map<String, LpfDensity> = emptyMap(),
    ) {
        val session = SessionDetailUi(
            id = "abcd",
            label = null,
            dateLabel = "2026-09-25",
            timeLabel = "09:41",
            confirmedEggs = confirmedEggs,
            speciesCount = lpfPerSpecies.size,
            samplesTotal = samplesTotal,
            lpfPerSpecies = lpfPerSpecies,
            detectedSpecies = lpfPerSpecies.keys.toList(),
            verifiedSamples = emptyList(),
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                LpfHeroCard(session = session)
            }
        }
    }

    private companion object {
        const val ASCARIS = "Ascaris lumbricoides"
    }
}
