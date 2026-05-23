package com.agarthavision.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.komoui.themes.KomoTheme

// Material3 color scheme derived from Clinical Pulse tokens.
// Used by KomoTheme for Material3 components that live underneath komoui.
// Keep these values in sync with AgarthaLightStyles above.
private val AgarthaMaterialColorScheme = lightColorScheme(
    primary             = ClinicalBlue,
    onPrimary           = Ink,
    primaryContainer    = DiagnosticCyanSoft,
    onPrimaryContainer  = Ink,
    secondary           = Paper,
    onSecondary         = Ink,
    secondaryContainer  = Paper,
    onSecondaryContainer = Ink,
    tertiary            = DiagnosticCyan,
    onTertiary          = Ink,
    tertiaryContainer   = DiagnosticCyanSoft,
    onTertiaryContainer = Ink,
    error               = AlertCoral,
    onError             = Ink,
    errorContainer      = AlertCoral.copy(alpha = 0.12f),
    onErrorContainer    = AlertCoral,
    background          = Bone,
    onBackground        = Ink,
    surface             = Bone,
    onSurface           = Ink,
    surfaceVariant      = Paper,
    onSurfaceVariant    = Mute,
    outline             = BorderInk,
    outlineVariant      = BorderInk.copy(alpha = 0.5f),
    scrim               = Ink.copy(alpha = 0.32f),
)

// Root theme composable — wrap all screens inside this.
// Dark mode is intentionally locked off: the Clinical Pulse moodboard is
// designed for light backgrounds; chart contrast is calibrated accordingly.
@Composable
fun AgarthaVisionTheme(content: @Composable () -> Unit) {
    KomoTheme(
        isDarkTheme          = false,
        komoLightColors      = AgarthaLightStyles,
        komoDarkColors       = AgarthaLightStyles, // same palette — dark toggle stays on-brand
        materialLightColors  = AgarthaMaterialColorScheme,
        materialDarkColors   = AgarthaMaterialColorScheme,
        komoRadius           = AgarthaRadius,
        typography           = AgarthaTypography,
        content              = content,
    )
}
