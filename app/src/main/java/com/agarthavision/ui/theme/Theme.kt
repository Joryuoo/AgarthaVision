package com.agarthavision.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// ── Shapes ─────────────────────────────────────────────────────────────────
// Radius scale for MaterialTheme shapes. Pills use `extraLarge`.
val AppShapes = Shapes(
    small      = RoundedCornerShape(8.dp),     // tiles, mini tags
    medium     = RoundedCornerShape(12.dp),    // cards, inputs, popovers
    large      = RoundedCornerShape(16.dp),    // hero, dialogs
    extraLarge = RoundedCornerShape(999.dp),   // pills
)

// Explicit shape for AlertDialogs. Material3 defaults dialogs to
// `shapes.extraLarge`, which in this app is the 999.dp pill token — far too
// round for a dialog. Pass this on every `AlertDialog(shape = DialogShape)`
// rather than overloading the shared `extraLarge` slot (mirrors AgarthaButton's
// local `PillShape`).
val DialogShape = RoundedCornerShape(8.dp)

// ── Material3 color schemes derived from AppColors (CIT-U maroon/gold) ───────
private val LightColorScheme = lightColorScheme(
    primary          = AppColors.Maroon,
    onPrimary        = AppColors.White,
    secondary        = AppColors.Gold,
    onSecondary      = AppColors.Gray900,
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

private val DarkColorScheme = darkColorScheme(
    primary          = AppColors.MaroonBright,
    onPrimary        = AppColors.Gray900,
    secondary        = AppColors.Gold,
    onSecondary      = AppColors.Gray900,
    background       = AppColors.DarkBackground,
    onBackground     = AppColors.DarkTextPrimary,
    surface          = AppColors.DarkSurface,
    onSurface        = AppColors.DarkTextPrimary,
    surfaceVariant   = AppColors.DarkSurfaceAlt,
    onSurfaceVariant = AppColors.DarkTextSecondary,
    outline          = AppColors.DarkBorder,
    error            = AppColors.RedBright,
    onError          = AppColors.Gray900,
)

/**
 * Accessor for the active mode-aware palette: `AgarthaTheme.colors.accent`.
 */
object AgarthaTheme {
    /** The [AgarthaColors] provided by the nearest [AgarthaVisionTheme]. */
    val colors: AgarthaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAgarthaColors.current
}

/**
 * Root theme — the single entry point for every screen (referenced by
 * MainActivity). One theme, two modes: [darkTheme] switches both the Material3
 * color scheme and [LocalAgarthaColors]. Defaults to light (the clinical
 * calibration); the persisted user preference drives the flag. The Capture
 * screen stays dark regardless (tool mode) via fixed [AppColors] values.
 */
@Composable
fun AgarthaVisionTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val agarthaColors = if (darkTheme) DarkAgarthaColors else LightAgarthaColors
    CompositionLocalProvider(LocalAgarthaColors provides agarthaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content,
        )
    }
}
