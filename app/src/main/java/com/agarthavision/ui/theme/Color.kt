package com.agarthavision.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komoui.themes.BoxShadow
import com.komoui.themes.KomoStyles

// ── Raw moodboard palette ─────────────────────────────────────────────────────
// Use the token names (MaterialTheme.styles.*) in component code.
// Reach these directly only when a token name doesn't map cleanly to a raw hex.
val Bone               = Color(0xFFFAFAF7) // App background, scaffold
val Paper              = Color(0xFFF1EFE9) // Chips, inset cards, skeleton base
val Ink                = Color(0xFF0E1B2C) // All primary text + outlined inputs
val Mute               = Color(0xFF5A6577) // Captions, metadata, helper text
val ClinicalBlue       = Color(0xFF1F5BFF) // Primary actions, focus, progress
val DiagnosticCyan     = Color(0xFF7FE3FF) // Live signal dot, overlay highlights
val DiagnosticCyanSoft = Color(0xFFE5F7FF) // Hover / soft highlight backgrounds
val AlertCoral         = Color(0xFFFF5A4A) // Errors, discard, threshold breach
val BorderInk          = Color(0xFFE5E3DF) // Ink @ ~15%, baked solid — lines

// ── Clinical Pulse token implementation ───────────────────────────────────────
// Implements KomoStyles so KomoTheme reads these values.
// See docs/components.md §2 for the full token → usage cheatsheet.
// API note: library names KomoTheme/KomoStyles/KomoRadius map to
//           ShadcnTheme/ShadcnColors/ShadcnRadius in the design doc.
object AgarthaLightStyles : KomoStyles {

    // Surfaces
    override val background  = Bone
    override val card        = Color.White
    override val popover     = Color.White
    override val sidebar     = Bone

    // Text
    override val foreground              = Ink
    override val cardForeground          = Ink
    override val popoverForeground       = Ink
    override val sidebarForeground       = Ink
    override val mutedForeground         = Mute

    // Primary (Clinical Blue) — "act"
    override val primary                    = ClinicalBlue
    override val primaryForeground          = Color.White
    override val sidebarPrimary             = ClinicalBlue
    override val sidebarPrimaryForeground   = Color.White

    // Secondary (Paper) + accent (Diagnostic Cyan @ 10%)
    override val secondary               = Paper
    override val secondaryForeground     = Ink
    override val muted                   = Paper
    override val accent                  = DiagnosticCyanSoft
    override val accentForeground        = Ink
    override val sidebarAccent           = DiagnosticCyanSoft
    override val sidebarAccentForeground = Ink

    // Destructive (Alert Coral) — "danger"
    override val destructive             = AlertCoral
    override val destructiveForeground   = Color.White

    // Lines, inputs, focus
    override val border       = BorderInk
    override val input        = Ink        // 1.5 px Ink border per moodboard
    override val ring         = ClinicalBlue
    override val sidebarBorder = BorderInk
    override val sidebarRing  = ClinicalBlue

    // Snackbar / toast — dark bar on light backgrounds
    override val snackbar = Ink

    // Charts — never swap these roles (see Do/Don't in §8)
    override val chart1 = ClinicalBlue    // Primary series
    override val chart2 = DiagnosticCyan  // Secondary / live signal
    override val chart3 = Ink             // Reference line
    override val chart4 = AlertCoral      // Threshold breach
    override val chart5 = Mute            // Comparison series

    // Shadows — scaled from moodboard elevated-card recipe: 0 12 32 -16 Ink@0.18
    @Composable override fun shadow2xs() = listOf(
        BoxShadow(0.dp, 1.dp,  3.dp,   0.dp,   Ink.copy(alpha = 0.06f))
    )
    @Composable override fun shadowXs() = listOf(
        BoxShadow(0.dp, 2.dp,  4.dp,   0.dp,   Ink.copy(alpha = 0.08f))
    )
    @Composable override fun shadowSm() = listOf(
        BoxShadow(0.dp, 2.dp,  6.dp,  (-1).dp, Ink.copy(alpha = 0.08f)),
        BoxShadow(0.dp, 1.dp,  2.dp,   0.dp,   Ink.copy(alpha = 0.06f)),
    )
    @Composable override fun shadow() = listOf(
        BoxShadow(0.dp, 4.dp,  8.dp,  (-2).dp, Ink.copy(alpha = 0.10f)),
        BoxShadow(0.dp, 2.dp,  4.dp,  (-2).dp, Ink.copy(alpha = 0.06f)),
    )
    @Composable override fun shadowMd() = listOf(
        BoxShadow(0.dp, 6.dp,  12.dp, (-4).dp, Ink.copy(alpha = 0.12f)),
        BoxShadow(0.dp, 2.dp,   4.dp, (-2).dp, Ink.copy(alpha = 0.06f)),
    )
    @Composable override fun shadowLg() = listOf(
        // Primary recipe from moodboard — elevated detection card
        BoxShadow(0.dp, 12.dp, 32.dp, (-16).dp, Ink.copy(alpha = 0.18f)),
        BoxShadow(0.dp,  4.dp,  8.dp,  (-4).dp, Ink.copy(alpha = 0.08f)),
    )
    @Composable override fun shadowXl() = listOf(
        BoxShadow(0.dp, 16.dp, 40.dp, (-16).dp, Ink.copy(alpha = 0.20f)),
        BoxShadow(0.dp,  8.dp, 16.dp,  (-8).dp, Ink.copy(alpha = 0.10f)),
    )
    @Composable override fun shadow2xl() = listOf(
        BoxShadow(0.dp, 24.dp, 56.dp, (-16).dp, Ink.copy(alpha = 0.24f)),
    )
}
