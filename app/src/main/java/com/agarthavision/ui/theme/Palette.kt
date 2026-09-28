package com.agarthavision.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
    /** Cards, lists. */
    val surface: Color,
    /** Subtle fills — table headers, empty tiles. */
    val surfaceVariant: Color,
    /** Secondary-button and chip fills. */
    val surfaceMuted: Color,
    /** Bottom sheets, dialogs, menus. */
    val surfaceHigh: Color,
    /** Hairline separators and plain-card borders. */
    val border: Color,
    /** Input borders, focused inputs, outlined buttons. */
    val borderStrong: Color,
    /** Primary body/heading text. */
    val textPrimary: Color,
    /** Secondary text — labels, meta. */
    val textSecondary: Color,
    /** Tertiary text — captions, placeholders, disabled. */
    val textTertiary: Color,
    /** Brand fill — Hero cards, Sessions tile, primary buttons (#8C1823 in both modes). */
    val brandFill: Color,
    /** Brand fill pressed state (lightens in dark mode instead of darkening). */
    val brandFillPressed: Color,
    /** Text/icon color on brand fill (white in both modes). */
    val onBrandFill: Color,
    /** Brand accent — text, icons, links, selected nav, data marks (rose in dark mode). */
    val accent: Color,
    /** Accent hover state. */
    val accentHover: Color,
    /** Accent pressed state. */
    val accentPressed: Color,
    /** Text/icon color on accent fills. */
    val onAccent: Color,
    /** Active-card / selected background wash / chip background (12% rose in dark mode). */
    val accentTint: Color,
    /** Hint / info banner background wash. */
    val accentTint2: Color,
    /** Text on [accentTint] (passes >= 5.0:1 on all surface levels). */
    val onAccentTint: Color,
    /** Gold brand highlight — fill-only, pair with [onGold]. */
    val gold: Color,
    /** Text/icon color on gold fills. */
    val onGold: Color,
    /** Gold badge background (14% tonal in dark mode). */
    val goldTint: Color,
    /** Text on [goldTint] (#FFC65C in dark mode). */
    val goldText: Color,
    /** Destructive / error. */
    val danger: Color,
    /** Error banner background (14% tonal in dark mode). */
    val dangerTint: Color,
    /** Text on [dangerTint]. */
    val dangerText: Color,
    /** Sync OK / confirmed detections. */
    val success: Color,
    /** Success badge background (14% tonal in dark mode). */
    val successTint: Color,
    /** Text on [successTint]. */
    val successText: Color,
    /** Manual captures / pending review / sync warning. */
    val warning: Color,
    /** Warning badge background (14% tonal in dark mode). */
    val warningTint: Color,
    /** Text on [warningTint]. */
    val warningText: Color,
    /** 5-tier choropleth coverage bin fills (0-10%, 10-20%, 20-30%, 30-40%, 40%+). */
    val coverageBins: List<Color>,
    /** Coverage map no-data fill. */
    val coverageNoData: Color,
    /** Coverage map too-few fill. */
    val coverageTooFew: Color,
) {
    /** Helper to get coverage bin color by bin index (0..4). */
    fun coverageBinColor(bin: Int): Color =
        coverageBins.getOrElse(bin.coerceIn(0, coverageBins.lastIndex)) { accent }
}

/** Light-mode palette (default; the clinical calibration). */
val LightAgarthaColors = AgarthaColors(
    isDark = false,
    background = AppColors.White,
    surface = AppColors.White,
    surfaceVariant = AppColors.Gray50,
    surfaceMuted = AppColors.Gray100,
    surfaceHigh = AppColors.White,
    border = AppColors.Gray100,
    borderStrong = AppColors.Gray200,
    textPrimary = AppColors.Gray900,
    textSecondary = AppColors.Gray500,
    textTertiary = AppColors.Gray400,
    brandFill = AppColors.Maroon,
    brandFillPressed = AppColors.MaroonPressed,
    onBrandFill = AppColors.White,
    accent = AppColors.Maroon,
    accentHover = AppColors.MaroonHover,
    accentPressed = AppColors.MaroonPressed,
    onAccent = AppColors.White,
    accentTint = AppColors.MaroonTint,
    accentTint2 = AppColors.MaroonTint2,
    onAccentTint = AppColors.Maroon,
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
    coverageBins = listOf(
        AppColors.MaroonTint,
        lerp(AppColors.MaroonTint, AppColors.Maroon, 0.25f),
        lerp(AppColors.MaroonTint, AppColors.Maroon, 0.50f),
        lerp(AppColors.MaroonTint, AppColors.Maroon, 0.75f),
        AppColors.Maroon,
    ),
    coverageNoData = AppColors.Gray200,
    coverageTooFew = AppColors.Gray100,
)

/** Dark-mode palette — warm charcoal surfaces, brightened accent/semantic colors. */
val DarkAgarthaColors = AgarthaColors(
    isDark = true,
    background = AppColors.DarkBackground,
    surface = AppColors.DarkSurface,
    surfaceVariant = AppColors.DarkSurfaceAlt,
    surfaceMuted = AppColors.DarkSurfaceAlt,
    surfaceHigh = AppColors.DarkSurfaceHigh,
    border = AppColors.DarkBorder,
    borderStrong = AppColors.DarkBorderStrong,
    textPrimary = AppColors.DarkTextPrimary,
    textSecondary = AppColors.DarkTextSecondary,
    textTertiary = AppColors.DarkTextTertiary,
    brandFill = AppColors.DarkMaroon,
    brandFillPressed = AppColors.DarkMaroonPressed,
    onBrandFill = AppColors.White,
    accent = AppColors.CrimsonBright,
    accentHover = AppColors.CrimsonBright,
    accentPressed = AppColors.DarkMaroonPressed,
    onAccent = AppColors.White,
    accentTint = AppColors.DarkAccentTint,
    accentTint2 = AppColors.DarkAccentTint2,
    onAccentTint = AppColors.CrimsonBright,
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
    coverageBins = listOf(
        AppColors.DarkCoverageBin0,
        AppColors.DarkCoverageBin1,
        AppColors.DarkCoverageBin2,
        AppColors.DarkCoverageBin3,
        AppColors.DarkCoverageBin4,
    ),
    coverageNoData = AppColors.DarkCoverageNoData,
    coverageTooFew = AppColors.DarkCoverageTooFew,
)

/** CompositionLocal carrying the active [AgarthaColors]; provided by AgarthaVisionTheme. */
val LocalAgarthaColors = staticCompositionLocalOf { LightAgarthaColors }
