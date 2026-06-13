package com.agarthavision.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// ── Shapes ─────────────────────────────────────────────────────────────────
// Radius scale for MaterialTheme shapes. Pills use `extraLarge`.
val AppShapes = Shapes(
    small      = RoundedCornerShape(8.dp),     // tiles, mini tags
    medium     = RoundedCornerShape(12.dp),    // cards, inputs, popovers
    large      = RoundedCornerShape(16.dp),    // hero, dialogs
    extraLarge = RoundedCornerShape(999.dp),   // pills
)

// ── Material3 color scheme derived from AppColors ────────────────────────────
private val AgarthaColorScheme = lightColorScheme(
    primary          = AppColors.Blue,
    onPrimary        = AppColors.White,
    background       = AppColors.White,
    onBackground     = AppColors.Gray900,
    surface          = AppColors.White,
    onSurface        = AppColors.Gray900,
    surfaceVariant   = AppColors.Gray50,
    onSurfaceVariant = AppColors.Gray500,
    outline          = AppColors.Gray100,
    error            = AppColors.Red,
    onError          = AppColors.White,
)

/**
 * Root theme — the single entry point for every screen (referenced by
 * MainActivity). Wraps content in a plain Material3 [MaterialTheme] built from
 * the [AppColors] / [AppTypography] / [AppShapes] tokens. The previous KomoUI
 * legacy wrapper ("Clinical Pulse") has been retired; dark mode stays off
 * (the palette is calibrated for light backgrounds).
 */
@Composable
fun AgarthaVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgarthaColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content,
    )
}
