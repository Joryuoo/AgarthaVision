package com.agarthavision.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
internal fun KpiPager(
    tiles: List<KpiTileUi>,
    isLoading: Boolean,
    period: HomePeriod,
    onTileClick: (KpiKind) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var page0HeightDp by remember { mutableStateOf<Dp?>(null) }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = Spacing.sm,
            modifier = Modifier
                .testTag("kpiPager")
                .fillMaxWidth()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Show My coverage") {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            true
                        },
                        CustomAccessibilityAction("Show activity tiles") {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            true
                        },
                    )
                },
        ) { page ->
            when (page) {
                0 -> {
                    KpiGrid(
                        tiles = tiles,
                        isLoading = isLoading,
                        onTileClick = onTileClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    page0HeightDp = with(density) { size.height.toDp() }
                                }
                            },
                    )
                }
                1 -> {
                    val page1Modifier = if (page0HeightDp != null) {
                        Modifier
                            .fillMaxWidth()
                            .height(page0HeightDp!!)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    }
                    CoveragePlaceholderCard(
                        period = period,
                        modifier = page1Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerDots(
                pageCount = 2,
                current = pagerState.currentPage,
                onSelect = { targetPage ->
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                },
            )
            if (pagerState.currentPage == 0) {
                Text(
                    text = "Swipe for My coverage →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AgarthaTheme.colors.textSecondary,
                    modifier = Modifier
                        .testTag("pagerCoverageHint")
                        .clickable {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                )
            }
        }
    }
}
