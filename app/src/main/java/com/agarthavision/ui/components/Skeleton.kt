package com.agarthavision.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Returns an animated horizontal shimmer [Brush] for skeleton loading placeholders.
 *
 * Uses [rememberInfiniteTransition] + [animateFloat] to sweep a three-stop gradient
 * (surfaceVariant → border → surfaceVariant) left-to-right with a 1200 ms cycle.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val surfaceVariant = AgarthaTheme.colors.surfaceVariant
    val border = AgarthaTheme.colors.border
    val shimmerColors = listOf(surfaceVariant, border, surfaceVariant)
    val sweepWidth = 600f

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -sweepWidth,
        targetValue = sweepWidth,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslateX",
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateX, 0f),
        end = Offset(translateX + sweepWidth, 0f),
    )
}

/**
 * A clipped [Box] filled with an animated shimmer brush, representing a loading placeholder.
 *
 * Width and height are controlled entirely by [modifier] — the caller is responsible for
 * supplying those constraints (e.g. `Modifier.width(140.dp).height(20.dp)`).
 * Semantics are cleared so screen readers skip the placeholder.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier = Modifier.clearAndSetSemantics {}) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(rememberShimmerBrush(), shape),
        )
    }
}
