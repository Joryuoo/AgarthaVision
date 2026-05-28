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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles

/**
 * A custom video shutter button that adheres to KomoUI design tokens.
 *
 * Morphs between a circle (Idle) and a rounded square (Recording) inside
 * a persistent circular border.
 *
 * @param isRecording The current recording state.
 * @param onClick Triggered when the button is tapped.
 * @param modifier Optional modifier for the button container.
 */
@Composable
fun ShutterButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val styles = MaterialTheme.styles
    
    // Animate the corner radius for the morphing effect
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

    // Outer Container (The Border)
    Box(
        modifier = modifier
            .size(84.dp)
            .border(
                width = 4.dp,
                color = styles.foreground.copy(alpha = 0.8f),
                shape = CircleShape
            )
            .clickable(
                onClick = onClick,
                enabled = true
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner Action Button (The morphing shape)
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .size(72.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (isRecording) styles.destructive else styles.primary
                )
        )
    }
}
