package com.agarthavision.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.agarthavision.R

// ── Inter font family (Google Fonts) ──────────────────────────────────────────
// Replaces the retired Geist + JetBrains Mono families. Inter ships tabular
// figures via `fontFeatureSettings = "tnum"`, so the EPG / mono readouts below
// no longer need a separate monospaced family.

val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val Inter = GoogleFont("Inter")

val InterFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Normal),    // 400
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Medium),    // 500
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.SemiBold),  // 600
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Bold),      // 700
)

private const val BASE_FEATURES = "tnum, cv11, ss01, ss03"

val AppTypography = Typography(
    // Headlines (app bar title)
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "cv11, ss01, ss03",
    ),
    // Titles (record ID, section labels)
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = BASE_FEATURES,
    ),
    // Body
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = BASE_FEATURES,
    ),
    // Labels (input labels, chip text)
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = BASE_FEATURES,
    ),
    // Captions (timestamps, meta)
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = BASE_FEATURES,
    ),
)

// ── Extra non-Material styles for clinical data display ───────────────────────
// Now Inter-based with tabular figures (tnum) instead of JetBrains Mono / Geist.
// Use these directly (not via MaterialTheme.typography) for the listed surfaces.

// IDs, timestamps, GPS coords, EPG readouts — tabular figures required
val MonoDataStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = "tnum",
)

// Same as MonoData but at 11 sp for compact rows
val MonoSmallStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "tnum",
)

// Hero EPG number — Inter, large, tabular figures (tnum)
val EpgDisplayStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = "tnum",
)
