package com.agarthavision.domain.geo

/**
 * An affine map-to-screen transform: uniform [scale] plus a translation ([tx], [ty]).
 *
 * `screen = map * scale + translation`. [toScreen] and [toMap] are exact inverses of each
 * other for any point, which [ViewFitTest] round-trips.
 */
data class ViewTransform(
    val scale: Float,
    val tx: Float,
    val ty: Float,
) {
    fun toScreen(x: Float, y: Float): Pair<Float, Float> = (x * scale + tx) to (y * scale + ty)

    fun toMap(px: Float, py: Float): Pair<Float, Float> = ((px - tx) / scale) to ((py - ty) / scale)
}

/**
 * Computes the [ViewTransform] that fits [bounds] inside a `widthPx` x `heightPx` viewport with
 * [paddingPx] margin on every side, preserving aspect ratio and centering the result.
 *
 * [bounds] is first expanded to at least [minSpan] on each axis via [GeoBounds.paddedToMinSpan]
 * so a tiny area (a single small island province) does not zoom in absurdly far.
 */
fun fitBounds(
    bounds: GeoBounds,
    widthPx: Float,
    heightPx: Float,
    paddingPx: Float,
    minSpan: Float = 0.25f,
): ViewTransform {
    val padded = bounds.paddedToMinSpan(minSpan)

    val availableWidth = (widthPx - 2f * paddingPx).coerceAtLeast(1f)
    val availableHeight = (heightPx - 2f * paddingPx).coerceAtLeast(1f)

    val scale = minOf(availableWidth / padded.width, availableHeight / padded.height)

    val centerMapX = (padded.minX + padded.maxX) / 2f
    val centerMapY = (padded.minY + padded.maxY) / 2f
    val centerScreenX = widthPx / 2f
    val centerScreenY = heightPx / 2f

    val tx = centerScreenX - centerMapX * scale
    val ty = centerScreenY - centerMapY * scale

    return ViewTransform(scale = scale, tx = tx, ty = ty)
}
