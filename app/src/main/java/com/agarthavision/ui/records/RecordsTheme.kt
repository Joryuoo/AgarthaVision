package com.agarthavision.ui.records

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R

val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val Inter = GoogleFont("Inter")

val InterFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Normal),    // 400
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Medium),    // 500
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.SemiBold),  // 600
    Font(googleFont = Inter, fontProvider = GoogleFontProvider, weight = FontWeight.Bold)       // 700
)

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
    val MicroscopeBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        0.0f to Color(0xFF0E1424),
        1.0f to Color(0xFF060912)
    )
}

private val baseFeatures = "tnum, cv11, ss01, ss03"

val AppTypography = Typography(
    // Headlines (app bar title)
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "cv11, ss01, ss03"
    ),
    // Titles (record ID, section labels)
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = baseFeatures
    ),
    // Body
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = baseFeatures
    ),
    // Labels (input labels, chip text)
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = baseFeatures
    ),
    // Captions (timestamps, meta)
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = baseFeatures
    )
)

val AppShapes = Shapes(
    small      = RoundedCornerShape(8.dp),    // tiles
    medium     = RoundedCornerShape(12.dp),   // cards, inputs
    large      = RoundedCornerShape(16.dp),   // hero
    extraLarge = RoundedCornerShape(999.dp)   // pills
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

private val AgarthaColorScheme = lightColorScheme(
    primary       = AppColors.Blue,
    onPrimary     = AppColors.White,
    background    = AppColors.White,
    onBackground  = AppColors.Gray900,
    surface       = AppColors.White,
    onSurface     = AppColors.Gray900,
    surfaceVariant = AppColors.Gray50,
    onSurfaceVariant = AppColors.Gray500,
    outline       = AppColors.Gray100
)

@Composable
fun AgarthaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgarthaColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
