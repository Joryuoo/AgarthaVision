package com.agarthavision.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Canonical AgarthaVision color palette (Inter / #1E3FD9 design system, per
 * agartha-design-system.md). Single source of truth for app color tokens.
 *
 * The legacy "Clinical Pulse" KomoUI palette (Bone/Paper/Ink/ClinicalBlue/…)
 * and its legacy theme implementation were removed when the old theme system
 * was retired; no code references those tokens directly any more.
 */
object AppColors {
    // Brand
    val Blue        = Color(0xFF1E3FD9)
    val BlueHover   = Color(0xFF1A36BF)
    val BluePressed = Color(0xFF15309F)
    val BlueTint    = Color(0xFFE6EBFC)
    val BlueTint2   = Color(0xFFF1F4FE)

    // Neutrals
    val White       = Color(0xFFFFFFFF)
    val OffWhite    = Color(0xFFFAFBFC)
    val Gray50      = Color(0xFFF7F8FA)
    val Gray100     = Color(0xFFEEF0F4)
    val Gray200     = Color(0xFFE2E5EB)
    val Gray300     = Color(0xFFCBD0DA)
    val Gray400     = Color(0xFF9CA3AF)
    val Gray500     = Color(0xFF6B7280)
    val Gray700     = Color(0xFF374151)
    val Gray900     = Color(0xFF0F172A)

    // Semantic
    val Red         = Color(0xFFDC2626)
    val RedTint     = Color(0xFFFEE2E2)
    val Green       = Color(0xFF16A34A)
    val GreenTint   = Color(0xFFDCFCE7)
    val GreenText   = Color(0xFF166534)
    val Amber       = Color(0xFFD97706)
    val AmberTint   = Color(0xFFFEF3C7)
    val AmberText   = Color(0xFF92400E)

    // Microscope-feel sample tile gradient
    val MicroscopeBrush = Brush.linearGradient(
        0.0f to Color(0xFF0E1424),
        1.0f to Color(0xFF060912)
    )
}
