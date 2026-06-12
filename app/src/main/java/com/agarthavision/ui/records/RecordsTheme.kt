package com.agarthavision.ui.records

import androidx.compose.runtime.Composable
import com.agarthavision.ui.theme.AgarthaVisionTheme

/**
 * Transitional re-export shim.
 *
 * The canonical AgarthaVision theme now lives in `com.agarthavision.ui.theme`
 * (single Material3 entry point: [AgarthaVisionTheme]). These aliases keep the
 * many existing `com.agarthavision.ui.records.*` imports — and the records
 * screens' same-package references — compiling unchanged.
 *
 * TODO(theme-cleanup): repoint call sites at `com.agarthavision.ui.theme.*`
 * and delete this file.
 */

typealias AppColors = com.agarthavision.ui.theme.AppColors
typealias Spacing = com.agarthavision.ui.theme.Spacing

val AppTypography = com.agarthavision.ui.theme.AppTypography
val AppShapes = com.agarthavision.ui.theme.AppShapes
val InterFamily = com.agarthavision.ui.theme.InterFamily
val GoogleFontProvider = com.agarthavision.ui.theme.GoogleFontProvider
val Inter = com.agarthavision.ui.theme.Inter

/** Legacy alias for [AgarthaVisionTheme]; kept for the dashboard preview. */
@Composable
fun AgarthaTheme(content: @Composable () -> Unit) = AgarthaVisionTheme(content)
