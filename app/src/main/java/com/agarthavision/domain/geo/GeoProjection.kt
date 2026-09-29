package com.agarthavision.domain.geo

import kotlin.math.cos

/**
 * Fixed equirectangular projection used for every boundary shape in this package. Not a
 * general-purpose map projection — it exists only to turn PSGC boundary lon/lat into flat map
 * units cheaply, with the Philippines' latitude span small enough that the distortion is
 * negligible for a choropleth.
 *
 * **Sign convention:** `y = -lat`. Screen coordinate systems (and Compose's `Canvas`) grow
 * downward, but latitude grows northward, so this flips the sign at projection time rather
 * than leaving every caller to remember to flip it later. A larger `y` is further south.
 */
object GeoProjection {
    /** Reference latitude the longitude scale is corrected against, in degrees. */
    const val REF_LAT_DEG = 12.5

    private val REF_LAT_COS = cos(Math.toRadians(REF_LAT_DEG))

    fun x(lonDeg: Double): Float = (lonDeg * REF_LAT_COS).toFloat()

    fun y(latDeg: Double): Float = (-latDeg).toFloat()
}
