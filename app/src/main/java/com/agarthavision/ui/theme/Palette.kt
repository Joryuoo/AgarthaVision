package com.agarthavision.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mode-aware semantic palette. Screens read these roles via [AgarthaTheme.colors]
 * so every surface renders correctly in both light and dark mode; raw values live
 * only in [AppColors].
 */
@Immutable
data class AgarthaColors(
    /** True when this palette is the dark-mode instance. */
    val isDark: Boolean,
    /** Screen background. */
    val background: Color,
    /** Cards, sheets, dialogs. */
    val surface: Color,
    /** Subtle fills — table headers, empty tiles. */
    val surfaceVariant: Color,
    /** Secondary-button and chip fills. */
    val surfaceMuted: Color,
    /** Hairline separators and plain-card borders. */
    val border: Color,
    /** Input borders. */
    val borderStrong: Color,
    /** Primary body/heading text. */
    val textPrimary: Color,
    /** Secondary text — labels, meta. */
    val textSecondary: Color,
    /** Tertiary text — placeholders, disabled. */
    val textTertiary: Color,
    /** Brand accent — CTAs, active states, focus rings, AI bboxes. */
    val accent: Color,
    /** Accent hover state. */
    val accentHover: Color,
    /** Accent pressed state. */
    val accentPressed: Color,
    /** Text/icon color on accent fills. */
    val onAccent: Color,
    /** Active-card / selected background wash. */
    val accentTint: Color,
    /** Hint / info banner background wash. */
    val accentTint2: Color,
    /** Gold brand highlight — fill-only, pair with [onGold]. */
    val gold: Color,
    /** Text/icon color on gold fills. */
    val onGold: Color,
    /** Gold badge background. */
    val goldTint: Color,
    /** Text on [goldTint]. */
    val goldText: Color,
    /** Destructive / error. */
    val danger: Color,
    /** Error banner background. */
    val dangerTint: Color,
    /** Text on [dangerTint]. */
    val dangerText: Color,
    /** Sync OK / confirmed detections. */
    val success: Color,
    /** Success badge background. */
    val successTint: Color,
    /** Text on [successTint]. */
    val successText: Color,
    /** Manual captures / pending review / sync warning. */
    val warning: Color,
    /** Warning badge background. */
    val warningTint: Color,
    /** Text on [warningTint]. */
    val warningText: Color,
)

/** Light-mode palette (default; the clinical calibration). */
val LightAgarthaColors = AgarthaColors(
    isDark = false,
    background = AppColors.White,
    surface = AppColors.White,
    surfaceVariant = AppColors.Gray50,
    surfaceMuted = AppColors.Gray100,
    border = AppColors.Gray100,
    borderStrong = AppColors.Gray200,
    textPrimary = AppColors.Gray900,
    textSecondary = AppColors.Gray500,
    textTertiary = AppColors.Gray400,
    accent = AppColors.Maroon,
    accentHover = AppColors.MaroonHover,
    accentPressed = AppColors.MaroonPressed,
    onAccent = AppColors.White,
    accentTint = AppColors.MaroonTint,
    accentTint2 = AppColors.MaroonTint2,
    gold = AppColors.Gold,
    onGold = AppColors.Gray900,
    goldTint = AppColors.GoldTint,
    goldText = AppColors.GoldText,
    danger = AppColors.Red,
    dangerTint = AppColors.RedTint,
    dangerText = AppColors.RedText,
    success = AppColors.Green,
    successTint = AppColors.GreenTint,
    successText = AppColors.GreenText,
    warning = AppColors.Amber,
    warningTint = AppColors.AmberTint,
    warningText = AppColors.AmberText,
)

/** Dark-mode palette — warm charcoal surfaces, brightened accent/semantic colors. */
val DarkAgarthaColors = AgarthaColors(
    isDark = true,
    background = AppColors.DarkBackground,
    surface = AppColors.DarkSurface,
    surfaceVariant = AppColors.DarkSurfaceAlt,
    surfaceMuted = AppColors.DarkSurfaceAlt,
    border = AppColors.DarkBorder,
    borderStrong = AppColors.Gray700,
    textPrimary = AppColors.DarkTextPrimary,
    textSecondary = AppColors.DarkTextSecondary,
    textTertiary = AppColors.Gray500,
    accent = AppColors.MaroonBright,
    accentHover = AppColors.MaroonBright,
    accentPressed = AppColors.Maroon,
    onAccent = AppColors.Gray900,
    accentTint = AppColors.DarkMaroonTint,
    accentTint2 = AppColors.DarkMaroonTint2,
    gold = AppColors.Gold,
    onGold = AppColors.Gray900,
    goldTint = AppColors.DarkGoldTint,
    goldText = AppColors.GoldTextDark,
    danger = AppColors.RedBright,
    dangerTint = AppColors.RedTintDark,
    dangerText = AppColors.RedBright,
    success = AppColors.GreenBright,
    successTint = AppColors.GreenTintDark,
    successText = AppColors.GreenBright,
    warning = AppColors.AmberBright,
    warningTint = AppColors.AmberTintDark,
    warningText = AppColors.AmberBright,
)

/** CompositionLocal carrying the active [AgarthaColors]; provided by AgarthaVisionTheme. */
val LocalAgarthaColors = staticCompositionLocalOf { LightAgarthaColors }
