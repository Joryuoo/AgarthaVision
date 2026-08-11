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

    /** Dark-mode accent — lightened maroon holding ≥4.5:1 on [DarkBackground]. */
    val MaroonBright  = Color(0xFFD9707A)

    // Brand — CIT-U gold (fill-only; pair with Gray900 text)
    val Gold          = Color(0xFFFFB81C)
    val GoldTint      = Color(0xFFFFF4D6)
    val GoldText      = Color(0xFF7A5A00)
    val GoldTextDark  = Color(0xFFFFCE5C)

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

    // Dark-mode surfaces — warm charcoal
    val DarkBackground = Color(0xFF171412)
    val DarkSurface    = Color(0xFF1F1B18)
    val DarkSurfaceAlt = Color(0xFF262220)
    val DarkBorder     = Color(0xFF37322E)
    val DarkTextPrimary   = Color(0xFFF5F5F4)
    val DarkTextSecondary = Color(0xFFA8A29E)
    val DarkMaroonTint  = Color(0xFF3A2226)
    val DarkMaroonTint2 = Color(0xFF2E1D20)
    val DarkGoldTint    = Color(0xFF3A2F14)

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

    // Semantic — dark-mode variants (brightened fg / deepened tint)
    val RedBright   = Color(0xFFF87171)
    val RedTintDark = Color(0xFF3B1D1D)
    val GreenBright = Color(0xFF4ADE80)
    val GreenTintDark = Color(0xFF14301F)
    val AmberBright = Color(0xFFFBBF24)
    val AmberTintDark = Color(0xFF382A10)

    // Microscope-feel sample tile gradient — warm dark, no navy
    val MicroscopeBrush = Brush.linearGradient(
        0.0f to Color(0xFF1C1210),
        1.0f to Color(0xFF0B0605)
    )
}
