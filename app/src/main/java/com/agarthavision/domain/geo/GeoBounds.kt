package com.agarthavision.domain.geo

/**
 * An axis-aligned bounding box in projected map units (see [GeoProjection]), not raw
 * lon/lat degrees.
 */
data class GeoBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    fun union(other: GeoBounds): GeoBounds =
        GeoBounds(
            minX = minOf(minX, other.minX),
            minY = minOf(minY, other.minY),
            maxX = maxOf(maxX, other.maxX),
            maxY = maxOf(maxY, other.maxY),
        )

    fun contains(x: Float, y: Float): Boolean = x in minX..maxX && y in minY..maxY

    /** True if this box and [other] overlap on both axes (touching edges count as overlap). */
    fun intersects(other: GeoBounds): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    /**
     * Expands this box symmetrically about its center so neither axis is smaller than
     * [minSpan], so a tiny province (e.g. a single small island) does not over-zoom when
     * later fit to a viewport by [ViewFit].
     */
    fun paddedToMinSpan(minSpan: Float): GeoBounds {
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val halfWidth = maxOf(width, minSpan) / 2f
        val halfHeight = maxOf(height, minSpan) / 2f
        return GeoBounds(
            minX = centerX - halfWidth,
            minY = centerY - halfHeight,
            maxX = centerX + halfWidth,
            maxY = centerY + halfHeight,
        )
    }
}
