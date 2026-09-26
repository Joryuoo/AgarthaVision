package com.agarthavision.ui.coverage

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProvinceSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun townShape(code: String, name: String) = AreaShape(
        code = code,
        name = name,
        parentCode = "CEB",
        bounds = GeoBounds(0f, 0f, 1f, 1f),
        labelX = 0.5f,
        labelY = 0.5f,
        rings = listOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)),
    )

    @Test
    fun `a TooFew province shows Too few with no digits`() {
        val selected = SelectedProvince(
            code = "CEB",
            name = "Cebu",
            coverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(smears = 2, positives = 0)),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                ProvinceSheetContent(selected = selected, showAllTowns = false, onShowAllTowns = {})
            }
        }

        composeRule.onNodeWithText("Too few smears to show a rate").assertExists()
    }

    @Test
    fun `a NoData province shows the right message`() {
        val selected = SelectedProvince(code = "CEB", name = "Cebu", coverage = null)

        composeRule.setContent {
            AgarthaVisionTheme {
                ProvinceSheetContent(selected = selected, showAllTowns = false, onShowAllTowns = {})
            }
        }

        composeRule.onNodeWithText("No smears from this province in this period").assertExists()
    }

    @Test
    fun `tapping View all towns requests showAllTowns true`() {
        val towns = BoundarySet(
            areas = (1..8).map { townShape("T$it", "Town $it") },
            bounds = GeoBounds(0f, 0f, 1f, 1f),
        )
        val selected = SelectedProvince(
            code = "CEB",
            name = "Cebu",
            coverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(smears = 10, positives = 4)),
            towns = TownGeometry.Available(towns),
        )

        var requestedShowAll: Boolean? = null
        composeRule.setContent {
            AgarthaVisionTheme {
                ProvinceSheetContent(
                    selected = selected,
                    showAllTowns = false,
                    onShowAllTowns = { requestedShowAll = it },
                )
            }
        }

        composeRule.onNodeWithText("View all 8 towns").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()

        assert(requestedShowAll == true)
    }

    @Test
    fun `showAllTowns true renders the full list instead of just the top 5`() {
        val towns = BoundarySet(
            areas = (1..8).map { townShape("T$it", "Town $it") },
            bounds = GeoBounds(0f, 0f, 1f, 1f),
        )
        val selected = SelectedProvince(
            code = "CEB",
            name = "Cebu",
            coverage = ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(smears = 10, positives = 4)),
            towns = TownGeometry.Available(towns),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                ProvinceSheetContent(selected = selected, showAllTowns = true, onShowAllTowns = {})
            }
        }

        composeRule.onNodeWithText("View all 8 towns").assertDoesNotExist()
        composeRule.onNodeWithText("Town 1").performScrollTo().assertExists()
    }
}
