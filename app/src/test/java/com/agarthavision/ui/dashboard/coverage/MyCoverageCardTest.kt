package com.agarthavision.ui.dashboard.coverage

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import com.agarthavision.domain.model.FindingsResult
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.coverage.LoadProvinceBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.LoadTownBoundariesUseCase
import com.agarthavision.domain.usecase.coverage.ObserveMyCoverageUseCase
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import com.agarthavision.ui.theme.AgarthaVisionTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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

    /** Depth-first search for descendant semantics nodes, including ones with empty configs
     * (like SkeletonBox's `clearAndSetSemantics {}` wrapper), which are only visible via the
     * unmerged tree. Mirrors the helper in KpiGridLoadingTest. */
    private fun SemanticsNode.allDescendants(): List<SemanticsNode> =
        children.flatMap { listOf(it) + it.allDescendants() }

    private val loadTownBoundariesUseCase: LoadTownBoundariesUseCase = mock()
    private val observeFindingsUseCase: ObserveFindingsUseCase = mock<ObserveFindingsUseCase>().also {
        whenever(it.invoke(any(), any(), anyOrNull())).thenReturn(
            flowOf(FindingsResult(emptyList(), 0)),
        )
    }

    @Before
    fun setUp() {
        runBlocking {
            whenever(loadTownBoundariesUseCase(any())).thenReturn(Result.success(null))
        }
    }

    private fun identityUseCase() = mock<ObserveLocalIdentityUseCase>().also {
        whenever(it.invoke()).thenReturn(
            flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")),
        )
    }

    private fun createViewModel(
        observeMyCoverageUseCase: ObserveMyCoverageUseCase,
        loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock(),
    ) = MyCoverageCardViewModel(
        clock = Clock.systemUTC(),
        observeLocalIdentityUseCase = identityUseCase(),
        observeMyCoverageUseCase = observeMyCoverageUseCase,
        loadProvinceBoundariesUseCase = loadProvinceBoundariesUseCase,
        loadTownBoundariesUseCase = loadTownBoundariesUseCase,
        observeFindingsUseCase = observeFindingsUseCase,
    )

    @Test
    fun `loading shows a skeleton`() {
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any())).thenReturn(
            kotlinx.coroutines.flow.flow { }, // never emits -> stays Loading
        )
        val vm = createViewModel(observeMyCoverageUseCase)

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
    fun `loading skeleton covers the whole card, not just the inner content area`() {
        // Regression test for the bug where the skeleton only overlaid the inner content Box
        // (below the header row, above the legend row), leaving the header/legend/padding
        // area of the card showing bare chrome instead of a loading placeholder. The fix moved
        // the SkeletonBox to be a sibling of the whole-card Column inside the outer Box, using
        // matchParentSize() against the *outer* Box (which carries the "myCoverageCard" tag) —
        // so its measured bounds should match the full card, not a smaller inner region.
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any())).thenReturn(
            kotlinx.coroutines.flow.flow { }, // never emits -> stays Loading
        )
        val vm = createViewModel(observeMyCoverageUseCase)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(
                    period = HomePeriod.TODAY,
                    onOpen = {},
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    viewModel = vm,
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val cardNode = composeRule.onNodeWithTag("myCoverageCard", useUnmergedTree = true).fetchSemanticsNode()

        // SkeletonBox's outer `Box(Modifier.clearAndSetSemantics {})` still produces a
        // semantics node (with an empty config) in the unmerged tree, so it's findable even
        // though its own semantics were cleared.
        val skeletonNodes = cardNode.allDescendants().filter { it.config.none() }

        assertTrue(
            "Expected to find at least one SkeletonBox semantics node (empty config) while loading",
            skeletonNodes.isNotEmpty(),
        )
        skeletonNodes.forEach { node ->
            assertTrue(
                "Expected the skeleton overlay height (${node.size.height}px) to match the " +
                    "whole card height (${cardNode.size.height}px), but it was noticeably " +
                    "smaller — the skeleton appears to only cover the inner content area " +
                    "instead of the full card (chrome + content).",
                node.size.height >= cardNode.size.height - 1,
            )
            assertTrue(
                "Expected the skeleton overlay width (${node.size.width}px) to match the " +
                    "whole card width (${cardNode.size.width}px).",
                node.size.width >= cardNode.size.width - 1,
            )
        }
    }

    @Test
    fun `ready state does not render a skeleton`() {
        val provinces = BoundarySet(areas = emptyList(), bounds = GeoBounds(0f, 0f, 1f, 1f))
        val coverage = MyCoverage(
            period = HomePeriod.TODAY,
            totals = AreaCount(smears = 10, positives = 4),
            unlocatedSmears = 0,
            provinces = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4)),
            ),
            islandGroupCounts = mapOf(IslandGroup.VISAYAS to 1),
            framing = CoverageFraming.SingleProvince("CEB"),
        )
        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.success(coverage)))
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        runBlocking {
            whenever(loadProvinceBoundariesUseCase()).thenReturn(Result.success(provinces))
        }
        val vm = createViewModel(observeMyCoverageUseCase, loadProvinceBoundariesUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        val cardNode = composeRule.onNodeWithTag("myCoverageCard", useUnmergedTree = true).fetchSemanticsNode()
        val skeletonNodes = cardNode.allDescendants().filter { it.config.none() }
        assertTrue(
            "Expected no SkeletonBox nodes once the card is in the Ready state",
            skeletonNodes.isEmpty(),
        )
    }

    @Test
    fun `empty state does not render a skeleton`() {
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
        val vm = createViewModel(observeMyCoverageUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        val cardNode = composeRule.onNodeWithTag("myCoverageCard", useUnmergedTree = true).fetchSemanticsNode()
        val skeletonNodes = cardNode.allDescendants().filter { it.config.none() }
        assertTrue(
            "Expected no SkeletonBox nodes once the card is in the Empty state",
            skeletonNodes.isEmpty(),
        )
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
        val vm = createViewModel(observeMyCoverageUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("No smears in this period").assertExists()
    }

    @Test
    fun `empty state renders inside the real fixed-height modifier used by KpiPager`() {
        // Mirrors KpiPager's Modifier.fillMaxWidth().height(coveragePageHeight(...)) for page 1 —
        // this is the exact shape of modifier that regressed to a blank card when it used
        // heightIn(min = 220.dp) instead (that collapses to 0px inside the pager's unbounded
        // LazyColumn measuring context, since the card body uses weight(1f) internally).
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
        val vm = createViewModel(observeMyCoverageUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(
                    period = HomePeriod.TODAY,
                    onOpen = {},
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    viewModel = vm,
                )
            }
        }

        composeRule.onNodeWithTag("myCoverageCard").assertIsDisplayed()
        composeRule.onNodeWithText("No smears in this period").assertIsDisplayed()
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

        val vm = createViewModel(observeMyCoverageUseCase, loadProvinceBoundariesUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.TODAY, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("Cebu").assertExists()
        composeRule.onNodeWithText("40% positive").assertExists()
        composeRule.onNodeWithText("Explore").assertExists()
    }

    @Test
    fun `a Ready state with IslandGroup framing renders region title and provinces`() {
        val provinces = BoundarySet(areas = emptyList(), bounds = GeoBounds(0f, 0f, 1f, 1f))
        val coverage = MyCoverage(
            period = HomePeriod.LAST_7_DAYS,
            totals = AreaCount(smears = 168, positives = 50),
            unlocatedSmears = 0,
            provinces = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(120, 41)),
                ProvinceCoverage("BOH", "Bohol", IslandGroup.VISAYAS, AreaCount(44, 8)),
                ProvinceCoverage("SIQ", "Siquijor", IslandGroup.VISAYAS, AreaCount(4, 1)),
            ),
            islandGroupCounts = mapOf(IslandGroup.VISAYAS to 3),
            framing = CoverageFraming.IslandGroupFrame(IslandGroup.VISAYAS, "Central Visayas"),
        )

        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.success(coverage)))
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase()).thenReturn(Result.success(provinces))
        }

        val vm = createViewModel(observeMyCoverageUseCase, loadProvinceBoundariesUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.LAST_7_DAYS, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("Central Visayas").assertExists()
        composeRule.onNodeWithText("3 provinces · 168 smears").assertExists()
        composeRule.onNodeWithText("Cebu").assertExists()
        composeRule.onNodeWithText("34%").assertExists()
        composeRule.onNodeWithText("Bohol").assertExists()
        composeRule.onNodeWithText("18%").assertExists()
        composeRule.onNodeWithText("Siquijor").assertExists()
        composeRule.onNodeWithText("Too few").assertExists()
    }

    @Test
    fun `a Ready state with Country framing renders Philippines and summary`() {
        val provinces = BoundarySet(areas = emptyList(), bounds = GeoBounds(0f, 0f, 1f, 1f))
        val coverage = MyCoverage(
            period = HomePeriod.LAST_30_DAYS,
            totals = AreaCount(smears = 200, positives = 60),
            unlocatedSmears = 0,
            provinces = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(100, 34)),
                ProvinceCoverage("DAV", "Davao del Sur", IslandGroup.MINDANAO, AreaCount(50, 10)),
                ProvinceCoverage("LAG", "Laguna", IslandGroup.LUZON, AreaCount(30, 3)),
                ProvinceCoverage("RIZ", "Rizal", IslandGroup.LUZON, AreaCount(15, 2)),
                ProvinceCoverage("COT", "Cotabato", IslandGroup.MINDANAO, AreaCount(5, 1)),
            ),
            islandGroupCounts = mapOf(
                IslandGroup.LUZON to 2,
                IslandGroup.VISAYAS to 1,
                IslandGroup.MINDANAO to 2,
            ),
            framing = CoverageFraming.Country,
        )

        val observeMyCoverageUseCase: ObserveMyCoverageUseCase = mock()
        whenever(observeMyCoverageUseCase(eq("user-1"), any(), any()))
            .thenReturn(flowOf(Result.success(coverage)))
        val loadProvinceBoundariesUseCase: LoadProvinceBoundariesUseCase = mock()
        kotlinx.coroutines.runBlocking {
            whenever(loadProvinceBoundariesUseCase()).thenReturn(Result.success(provinces))
        }

        val vm = createViewModel(observeMyCoverageUseCase, loadProvinceBoundariesUseCase)

        composeRule.setContent {
            AgarthaVisionTheme {
                MyCoverageCard(period = HomePeriod.LAST_30_DAYS, onOpen = {}, viewModel = vm)
            }
        }

        composeRule.onNodeWithText("Philippines").assertExists()
        composeRule.onNodeWithText("5 provinces · 3 island groups").assertExists()
        composeRule.onNodeWithText("+2 more").assertExists()
    }
}
