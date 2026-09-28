package com.agarthavision.domain.geo

/**
 * Even-odd ray-casting point-in-polygon test across every ring in [rings] at once.
 *
 * Treating every ring (exterior or hole, from any part of a multi-part area) as one combined
 * edge set is what makes holes and disjoint (archipelagic) parts both fall out correctly from
 * plain even-odd parity, with no special-casing: a point inside a hole crosses both the
 * exterior boundary and the hole boundary once each (even total, so outside); a point inside a
 * second, disjoint landmass only crosses that landmass's own ring an odd number of times
 * (the other rings contribute an even count, since the ray to infinity either misses them
 * entirely or crosses fully in and back out).
 */
private const val MIN_RING_POINTS = 3

fun pointInRings(x: Float, y: Float, rings: List<FloatArray>): Boolean {
    var inside = false
    for (ring in rings) {
        if (ring.size / 2 < MIN_RING_POINTS) continue
        if (ringTogglesParity(ring, x, y)) inside = !inside
    }
    return inside
}

/** Whether an odd number of [ring]'s edges cross the horizontal ray from ([x], [y]) rightward. */
private fun ringTogglesParity(ring: FloatArray, x: Float, y: Float): Boolean {
    val n = ring.size / 2
    var toggled = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = ring[i * 2]
        val yi = ring[i * 2 + 1]
        val xj = ring[j * 2]
        val yj = ring[j * 2 + 1]

        if ((yi > y) != (yj > y)) {
            val xIntersect = xi + (y - yi) / (yj - yi) * (xj - xi)
            if (x < xIntersect) toggled = !toggled
        }
        j = i
    }
    return toggled
}

/**
 * Finds the [AreaShape] in [set] containing map point ([x], [y]), or `null` if none does.
 *
 * Bbox-prefiltered: [AreaShape.bounds] is checked before the expensive ring test runs, so a
 * miss against most of a large set (e.g. all 85 provinces) is cheap. Returns the *first* match
 * in [BoundarySet.areas] iteration order — real boundary areas at the same level do not
 * overlap, so in practice there is at most one match; this is only a tie-break rule for
 * malformed input, not a "pick the smallest" policy.
 */
fun hitTest(set: BoundarySet, x: Float, y: Float): AreaShape? {
    for (area in set.areas) {
        if (!area.bounds.contains(x, y)) continue
        if (pointInRings(x, y, area.rings)) return area
    }
    return null
}
