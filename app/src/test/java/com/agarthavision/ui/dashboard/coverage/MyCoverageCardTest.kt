package com.agarthavision.ui.dashboard.coverage

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.ui.theme.AgarthaVisionTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyCoverageCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun identityUseCase() = mock<ObserveLocalIdentityUseCase>().also {
        whenever(it.invoke()).thenReturn(
            flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")),
        )
    }

    @Test
    fun `loading shows a skeleton`() {
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any())).thenReturn(
            kotlinx.coroutines.flow.flow { }, // never emits -> stays Loading
        )
        val vm = MyCoverageCardViewModel(
            clock = Clock.systemUTC(),
            observeLocalIdentityUseCase = identityUseCase(),
            observeMyCoverageUseCase = observeMyCoverageUseCase,
            loadProvinceBoundariesUseCase = mock(),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("myCoverageCard").assertExists()
    }

    @Test
    fun `empty shows the empty state`() {
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any())).thenReturn(
            flowOf(
                Result.success(
                    MyCoverage(
                        period = HomePeriod.TODAY,
                        totals = AreaCount(0, 0),
                        unlocatedSmears = 0,
                        provinces = emptyList(),
                        islandGroupCounts = emptyMap(),
                        framing = CoverageFraming.Empty,
                    ),
                ),
            ),
        )
        val vm = MyCoverageCardViewModel(
            clock = Clock.systemUTC(),
            observeLocalIdentityUseCase = identityUseCase(),
            observeMyCoverageUseCase = observeMyCoverageUseCase,
            loadProvinceBoundariesUseCase = mock(),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("No smears in this period").assertExists()
    }

    @Test
    fun `a Ready state with SingleProvince framing renders the province name and rate`() {
        val cebuShape = AreaShape(
            code = "CEB",
            name = "Cebu",
            parentCode = "",
            bounds = GeoBounds(0f, 0f, 1f, 1f),
            labelX = 0.5f,
            labelY = 0.5f,
            rings = listOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)),
        )
        val provinces = BoundarySet(areas = listOf(cebuShape), bounds = GeoBounds(0f, 0f, 1f, 1f))
        val coverage = MyCoverage(
            period = HomePeriod.TODAY,
            totals = AreaCount(smears = 10, positives = 4),
            unlocatedSmears = 0,
            provinces = listOf(
                ProvinceCoverage(
                    code = "CEB",
                    name = "Cebu",
                    islandGroup = IslandGroup.VISAYAS,
                    count = AreaCount(smears = 10, positives = 4),
                ),
            ),
            islandGroupCounts = mapOf(IslandGroup.VISAYAS to 1),
            framing = CoverageFraming.SingleProvince("CEB"),
        )

        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.success(coverage)))
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase()).thenReturn(Result.success(provinces))
        }

        val vm = MyCoverageCardViewModel(
            clock = Clock.systemUTC(),
            observeLocalIdentityUseCase = identityUseCase(),
            observeMyCoverageUseCase = observeMyCoverageUseCase,
            loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("Cebu").assertExists()
        composeRule.onNodeWithText("40% positive").assertExists()
    }
}
