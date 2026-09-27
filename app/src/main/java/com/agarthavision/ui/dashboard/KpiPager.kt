package com.agarthavision.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.agarthavision.ui.dashboard.coverage.MyCoverageCard
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Suppress("LongParameterList") // Mirrors the sibling KpiPager tile-click callback; each param is an independent slot.
@Composable
internal fun KpiPager(
    tiles: List<KpiTileUi>,
    isLoading: Boolean,
    period: HomePeriod,
    onTileClick: (KpiKind) -> Unit = {},
    onOpenCoverage: (HomePeriod) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var page0HeightDp by remember { mutableStateOf<Dp?>(null) }

    // Persist the current page so it survives navigation away and back.
    savedPage = pagerState.currentPage

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
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
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val pageTransitionModifier = Modifier.graphicsLayer {
                val progress = pageOffset.coerceIn(0f, 1f)
                alpha = 1f - (progress * 0.25f)
                scaleX = 1f - (progress * 0.04f)
                scaleY = 1f - (progress * 0.04f)
            }
            when (page) {
                0 -> {
                    KpiGrid(
                        tiles = tiles,
                        isLoading = isLoading,
                        onTileClick = onTileClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(pageTransitionModifier)
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
                    MyCoverageCard(
                        period = period,
                        onOpen = onOpenCoverage,
                        modifier = page1Modifier.then(pageTransitionModifier),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerDots(
                pagerState = pagerState,
                pageCount = 2,
                onSelect = { targetPage ->
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                },
            )
            val continuousOffset = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
            val hintDismissProgress = (1f - continuousOffset * 2.5f).coerceIn(0f, 1f)
            if (hintDismissProgress > 0f) {
                val infiniteTransition = rememberInfiniteTransition(label = "swipeHint")
                val nudgeOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "nudgeOffset",
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.65f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseAlpha",
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Swipe for My coverage →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AgarthaTheme.colors.textSecondary,
                    modifier = Modifier
                        .testTag("pagerCoverageHint")
                        .graphicsLayer {
                            translationX = nudgeOffset * density.density
                            alpha = hintDismissProgress * pulseAlpha
                        }
                        .clickable {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                )
            }
        }
    }
}
