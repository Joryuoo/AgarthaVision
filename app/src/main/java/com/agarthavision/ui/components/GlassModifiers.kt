package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agarthavision.ui.theme.AppColors

// NOTE: Android Compose doesn't support backdrop-filter blur easily out of the box in Modifier
// without RenderEffect which requires API 31+, and even then it can be tricky.
// We will simulate the glass look with a semi-transparent background and shadows,
// which matches the fallback behavior on older Android versions.

@Composable
fun Modifier.glassChrome(
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = AppColors.White.copy(alpha = 0.72f),
    elevation: Dp = 8.dp,
    shadowColor: Color = AppColors.Gray900.copy(alpha = 0.1f)
): Modifier {
    return this
        .shadow(elevation, shape, spotColor = shadowColor, ambientColor = shadowColor)
        .background(backgroundColor, shape)
        .border(0.5.dp, AppColors.White.copy(alpha = 0.6f), shape)
        .clip(shape)
}

@Composable
fun Modifier.glassChromeStrong(
    shape: Shape = RoundedCornerShape(100.dp),
    elevation: Dp = 2.dp
): Modifier {
    return glassChrome(
        shape = shape,
        backgroundColor = AppColors.White.copy(alpha = 0.86f),
        elevation = elevation,
        shadowColor = AppColors.Gray900.copy(alpha = 0.1f)
    )
}
