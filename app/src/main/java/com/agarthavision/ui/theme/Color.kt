package com.agarthavision.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Canonical AgarthaVision color palette — CIT-U brand (maroon `#8C1823`, gold
 * `#FFB81C`, warm stone neutrals). Single source of truth for raw color values;
 * screens consume these through [AgarthaColors] (mode-aware) or directly for
 * mode-independent surfaces (Capture glass, on-accent white).
 *
 * The prior cobalt "clinical blue" palette (`#1E3FD9`) is retired; no blue
 * tokens remain. Gold is a fill-only highlight — always paired with dark text,
 * never used as text on light surfaces.
 */
object AppColors {
    // Brand — CIT-U maroon
    val Maroon        = Color(0xFF8C1823)
    val MaroonHover   = Color(0xFF75141E)
    val MaroonPressed = Color(0xFF5E1018)
    val MaroonTint    = Color(0xFFF9E8EA)
    val MaroonTint2   = Color(0xFFFCF3F4)

    /** Main maroon in dark mode — vibrant maroon with 7.0:1 contrast against white text. */
    val DarkMaroon        = Color(0xFFA01C29)
    val DarkMaroonPressed = Color(0xFFB82030)
    val MaroonBright      = DarkMaroon // Alias for backward compatibility
    val RoseBright        = DarkMaroon // Alias for backward compatibility

    val DarkAccentTint    = Color(0xFFA01C29).copy(alpha = 0.18f)
    val DarkAccentTint2   = Color(0xFFA01C29).copy(alpha = 0.10f)
    val DarkOnAccentTint  = DarkMaroon

    // Brand — CIT-U gold (fill-only; pair with Gray900 text)
    val Gold          = Color(0xFFFFB81C)
    val GoldTint      = Color(0xFFFFF4D6)
    val GoldText      = Color(0xFF7A5A00)
    val GoldTextDark  = Color(0xFFFFC65C)

    // Neutrals — warm stone ramp (no blue cast)
    val White       = Color(0xFFFFFFFF)
    val OffWhite    = Color(0xFFFAFAF9)
    val Gray50      = Color(0xFFF5F5F4)
    val Gray100     = Color(0xFFE7E5E4)
    val Gray200     = Color(0xFFD6D3D1)
    val Gray300     = Color(0xFFA8A29E)
    val Gray400     = Color(0xFF8A847F)
    val Gray500     = Color(0xFF78716C)
    val Gray700     = Color(0xFF44403C)
    val Gray900     = Color(0xFF1C1917)

    // Dark-mode surfaces — warm charcoal (tonal elevation, no shadows needed)
    val DarkBackground   = Color(0xFF171412)
    val DarkSurface      = Color(0xFF1F1B18)
    val DarkSurfaceAlt   = Color(0xFF262220)
    val DarkSurfaceHigh  = Color(0xFF2E2926)
    val DarkBorder       = Color(0xFF37322E)
    val DarkBorderStrong = Color(0xFF4A443F)
    val DarkTextPrimary   = Color(0xFFF5F5F4)
    val DarkTextSecondary = Color(0xFFA8A29E)
    val DarkTextTertiary  = Color(0xFF9C958F)
    val DarkMaroonTint  = Color(0xFF3A2226)
    val DarkMaroonTint2 = Color(0xFF2E1D20)
    val DarkGoldTint    = Color(0xFFFFB81C).copy(alpha = 0.14f)

    // Semantic
    val Red         = Color(0xFFDC2626)
    val RedTint     = Color(0xFFFEE2E2)
    val RedText     = Color(0xFF991B1B)
    val Green       = Color(0xFF16A34A)
    val GreenTint   = Color(0xFFDCFCE7)
    val GreenText   = Color(0xFF166534)
    val Amber       = Color(0xFFD97706)
    val AmberTint   = Color(0xFFFEF3C7)
    val AmberText   = Color(0xFF92400E)

    // Semantic — dark-mode variants (brightened fg / 14% tonal tints)
    val RedBright   = Color(0xFFF87171)
    val RedTintDark = Color(0xFFF87171).copy(alpha = 0.14f)
    val GreenBright = Color(0xFF4ADE80)
    val GreenTintDark = Color(0xFF4ADE80).copy(alpha = 0.14f)
    val AmberBright = Color(0xFFFBBF24)
    val AmberTintDark = Color(0xFFFBBF24).copy(alpha = 0.14f)

    // Coverage map scale (Dark) — hotspots glow instead of sinking into dark background
    val DarkCoverageBin0   = Color(0xFF3A2226)
    val DarkCoverageBin1   = Color(0xFF5C1C26)
    val DarkCoverageBin2   = Color(0xFF8C1823)
    val DarkCoverageBin3   = Color(0xFFC43343)
    val DarkCoverageBin4   = Color(0xFFF06B78)
    val DarkCoverageNoData = Color(0xFF2A2522)
    val DarkCoverageTooFew = Color(0xFF37322E)

    // Microscope-feel sample tile gradient — warm dark, no navy
    val MicroscopeBrush = Brush.linearGradient(
        0.0f to Color(0xFF1C1210),
        1.0f to Color(0xFF0B0605)
    )
}
