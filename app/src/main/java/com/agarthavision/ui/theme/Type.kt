package com.agarthavision.ui.theme

import androidx.compose.material3.Typography
import com.agarthavision.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ── Font families ─────────────────────────────────────────────────────────────
//   geist_regular.ttf        — Geist weight 400 (fonts.google.com)
//   geist_medium.ttf         — Geist weight 500
//   jetbrains_mono_regular.ttf — JetBrains Mono weight 400

  val GeistFamily = FontFamily(
      Font(resId = R.font.geist_regular,  weight = FontWeight.Normal),
      Font(resId = R.font.geist_medium,   weight = FontWeight.Medium),
  )
  val JetBrainsMonoFamily = FontFamily(
      Font(resId = R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
  )

// ── Material3 Typography (8 clinical roles → Material slots) ─────────────────
// See docs/components.md §3 for the full role ↔ slot mapping and usage rules.
val AgarthaTypography = Typography(
    // Hero numerals — EPG "1,284", large stats
    displayLarge = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 96.sp,
        lineHeight    = 104.sp,
        letterSpacing = (-0.04).em,
    ),
    displayMedium = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 72.sp,
        lineHeight    = 80.sp,
        letterSpacing = (-0.04).em,
    ),
    displaySmall = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 56.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-0.04).em,
    ),

    // Screen / section titles
    headlineLarge = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = (-0.01).em,
    ),

    // Card titles, dialog titles
    titleLarge = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    titleSmall = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.em,
    ),

    // Body copy
    bodyLarge = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.em,
    ),
    bodyMedium = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.em,
    ),
    bodySmall = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.em,
    ),

    // Buttons, tabs, list rows
    labelLarge = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.em,
    ),
    labelMedium = TextStyle(
        fontFamily    = GeistFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.em,
    ),
    // Mono eyebrow — "DETECTION", "EDGE INFERENCE" section labels
    labelSmall = TextStyle(
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.12.em,
    ),
)

// ── Extra non-Material styles for clinical data display ───────────────────────
// Use these directly (not via MaterialTheme.typography) for the listed surfaces.

// IDs, timestamps, GPS coords, EPG readouts — tabular figures required
val MonoDataStyle = TextStyle(
    fontFamily         = JetBrainsMonoFamily,
    fontWeight         = FontWeight.Normal,
    fontSize           = 13.sp,
    lineHeight         = 18.sp,
    letterSpacing      = 0.em,
    fontFeatureSettings = "\"tnum\"",
)

// Same as MonoData but at 11 sp for compact rows
val MonoSmallStyle = TextStyle(
    fontFamily         = JetBrainsMonoFamily,
    fontWeight         = FontWeight.Normal,
    fontSize           = 11.sp,
    lineHeight         = 16.sp,
    letterSpacing      = 0.em,
    fontFeatureSettings = "\"tnum\"",
)

// Hero EPG number — Geist, large, tabular figures (tnum)
val EpgDisplayStyle = TextStyle(
    fontFamily         = GeistFamily,
    fontWeight         = FontWeight.Medium,
    fontSize           = 56.sp,
    lineHeight         = 64.sp,
    letterSpacing      = (-0.04).em,
    fontFeatureSettings = "\"tnum\"",
)
