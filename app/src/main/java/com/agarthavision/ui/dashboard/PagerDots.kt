package com.agarthavision.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.agarthavision.ui.theme.AgarthaTheme
import kotlin.math.absoluteValue

private val PillShape = RoundedCornerShape(999.dp)
private val SelectedWidth = 18.dp
private val UnselectedWidth = 6.dp
private val DotHeight = 6.dp

@Composable
fun PagerDots(
    pagerState: PagerState,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in 0 until pageCount) {
            val isSelected = page == pagerState.currentPage
            val distance = (pagePosition - page).absoluteValue.coerceIn(0f, 1f)
            val dotWidth = lerp(SelectedWidth, UnselectedWidth, distance)
            val dotColor = lerp(colors.accent, colors.borderStrong, distance)

            val description = when (page) {
                0 -> "Page 1 of $pageCount, Activity tiles"
                1 -> "Page 2 of $pageCount, My coverage"
                else -> "Page ${page + 1} of $pageCount"
            }
            Box(
                modifier = Modifier
                    .semantics {
                        this.role = Role.Tab
                        this.selected = isSelected
                        this.contentDescription = description
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(page) },
                    )
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(DotHeight)
                        .width(dotWidth)
                        .clip(PillShape)
                        .background(dotColor),
                )
            }
        }
    }
}

@Composable
fun PagerDots(
    pageCount: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in 0 until pageCount) {
            val isSelected = page == current
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) SelectedWidth else UnselectedWidth,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth_$page",
            )
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) colors.accent else colors.borderStrong,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotColor_$page",
            )
            val description = when (page) {
                0 -> "Page 1 of $pageCount, Activity tiles"
                1 -> "Page 2 of $pageCount, My coverage"
                else -> "Page ${page + 1} of $pageCount"
            }
            Box(
                modifier = Modifier
                    .semantics {
                        this.role = Role.Tab
                        this.selected = isSelected
                        this.contentDescription = description
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(page) },
                    )
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(DotHeight)
                        .width(dotWidth)
                        .clip(PillShape)
                        .background(dotColor),
                )
            }
        }
    }
}
