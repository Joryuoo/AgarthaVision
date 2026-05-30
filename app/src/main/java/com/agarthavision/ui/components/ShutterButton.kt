package com.agarthavision.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles

/**
 * A custom shutter button that adheres to KomoUI design tokens. Used as the
 * Manual Capture trigger on [com.agarthavision.ui.capture.CaptureScreen] per
 * ADR-005.
 *
 * Morphs between a circle (idle) and a rounded square (active) inside a
 * persistent circular border. The morph is retained as a future hook; manual
 * capture is a momentary action so callers typically pass `isRecording = false`.
 *
 * @param onClick Triggered when the button is tapped.
 * @param modifier Optional modifier for the button container.
 * @param enabled When `false`, the click is ignored and the button visually dims.
 *   Mirrors `KomoButton(enabled = …)` semantics so callers can gate on
 *   `state.activeSessionId != null` as they did with the previous Outline button.
 * @param isRecording When `true`, morphs the inner shape to a rounded square and
 *   shifts the fill to the destructive token. Defaults to `false` for the manual-
 *   capture call site; reserved for future animated-feedback uses.
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRecording: Boolean = false,
) {
    val styles = MaterialTheme.styles

    // 0.dp = square, 36.dp (half of 72.dp) = circle
    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 36.dp,
        animationSpec = tween(durationMillis = 300),
        label = "ShutterMorph"
    )

    val innerPadding by animateDpAsState(
        targetValue = if (isRecording) 20.dp else 4.dp,
        animationSpec = tween(durationMillis = 300),
        label = "ShutterScale"
    )

    val borderAlpha = if (enabled) 0.8f else 0.3f
    val innerFill = when {
        !enabled -> styles.muted
        isRecording -> styles.destructive
        else -> styles.primary
    }

    Box(
        modifier = modifier
            .size(84.dp)
            .border(
                width = 4.dp,
                color = styles.foreground.copy(alpha = borderAlpha),
                shape = CircleShape
            )
            .clickable(
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .size(72.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(innerFill)
        )
    }
}
