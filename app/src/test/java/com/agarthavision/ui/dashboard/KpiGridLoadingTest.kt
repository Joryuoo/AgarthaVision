package com.agarthavision.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies the auditor-flagged KpiTile loading-skeleton fix (DashboardCards.kt):
 * the skeleton must occupy the tile's real footprint AND must actually be visible
 * (not collapse to a zero-size overlay), and the loaded/loading tile heights must match.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class KpiGridLoadingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val backdrop = Color.Red

    /** Depth-first search for descendant semantics nodes, including ones with empty configs
     * (like SkeletonBox's `clearAndSetSemantics {}` wrapper), which are only visible via the
     * unmerged tree. */
    private fun SemanticsNode.allDescendants(): List<SemanticsNode> =
        children.flatMap { listOf(it) + it.allDescendants() }

    @Test
    fun `loading skeleton is actually painted, not a zero-size overlay`() {
        // Freeze the infinite shimmer animation clock so the test doesn't wait forever for
        // the compose tree to go idle (it never will with an infiniteRepeatable running).
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            AgarthaVisionTheme {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .background(backdrop)
                ) {
                    KpiGrid(kpis = KpiState(), isLoading = true)
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val kpiTileHeight = root.size.height / 2 // 2 rows of tiles fill the grid height

        // SkeletonBox's outer `Box(Modifier.clearAndSetSemantics {})` still produces a
        // semantics node (with an empty config) in the unmerged tree. Its real layout bounds
        // reflect wherever the matchParentSize() modifier landed it. If matchParentSize()
        // resolved against Skeleton.kt's own wrapper Box (which has no other sized sibling)
        // instead of KpiTile's outer Box, this node's height collapses toward 0 instead of
        // matching the tile's real height.
        val skeletonNodes = root.allDescendants().filter { it.config.none() }

        assertTrue(
            "Expected to find at least one SkeletonBox semantics node (empty config) while loading",
            skeletonNodes.isNotEmpty(),
        )
        skeletonNodes.forEach { node ->
            assertTrue(
                "Expected SkeletonBox overlay height (${node.size.height}px) to be close to " +
                    "the tile height (${kpiTileHeight}px), but it collapsed — the skeleton " +
                    "overlay appears to have zero/near-zero size instead of filling the tile.",
                node.size.height > kpiTileHeight / 2,
            )
        }
    }

    @Test
    fun `loaded and loading tile heights match`() {
        var isLoading by mutableStateOf(false)

        composeRule.setContent {
            AgarthaVisionTheme {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .background(backdrop)
                ) {
                    KpiGrid(kpis = KpiState(), isLoading = isLoading)
                }
            }
        }
        val loadedHeight = composeRule.onRoot().fetchSemanticsNode().size.height

        // Switch to loading; the shimmer's infiniteRepeatable means the tree never reports
        // idle, so freeze the clock and force a single frame instead of waitForIdle().
        composeRule.mainClock.autoAdvance = false
        isLoading = true
        composeRule.mainClock.advanceTimeByFrame()
        val loadingHeight = composeRule.onRoot().fetchSemanticsNode().size.height

        assertEquals(loadedHeight, loadingHeight)
    }
}
