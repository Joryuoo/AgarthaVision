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

/**
 * Clamps this transform's translation so [bounds], drawn at this transform's [ViewTransform.scale],
 * can never be panned or zoomed entirely off the `viewportWidth` x `viewportHeight` screen.
 *
 * For one axis, let `a` be the translation that pins [bounds]'s min edge to screen `0` and `b` be
 * the translation that pins its max edge to the viewport's far edge. When the scaled bounds are
 * smaller than the viewport, `[a, b]` is the range that keeps the whole box on screen (any tighter
 * and part of the viewport would show past the map's edge); when the scaled bounds are larger,
 * `[b, a]` is the range that keeps the viewport fully covered by the map (any looser and an edge
 * would pull inward, exposing empty space beyond it). Either way `tx`/`ty` is clamped to
 * `[min(a, b), max(a, b)]`, so a free pan/zoom gesture can never carry the map fully out of view.
 */
fun ViewTransform.clampedToBounds(
    bounds: GeoBounds,
    viewportWidth: Float,
    viewportHeight: Float,
): ViewTransform {
    fun clampAxis(minCoord: Float, maxCoord: Float, viewportSize: Float, t: Float): Float {
        val pinMin = -minCoord * scale
        val pinMax = viewportSize - maxCoord * scale
        return t.coerceIn(minOf(pinMin, pinMax), maxOf(pinMin, pinMax))
    }
    return copy(
        tx = clampAxis(bounds.minX, bounds.maxX, viewportWidth, tx),
        ty = clampAxis(bounds.minY, bounds.maxY, viewportHeight, ty),
    )
}
