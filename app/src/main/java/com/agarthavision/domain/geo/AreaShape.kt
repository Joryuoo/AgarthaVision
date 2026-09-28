package com.agarthavision.domain.geo

/**
 * One boundary shape — a province or a town — with geometry already projected via
 * [GeoProjection] (never raw lon/lat).
 *
 * [rings] flattens both single- and multi-part (archipelagic) areas into one list: a
 * disjoint second landmass and an interior hole are both just another ring, which is what lets
 * [HitTest.pointInRings] use a single even-odd pass over all of them. Each [FloatArray] is
 * interleaved `x0, y0, x1, y1, ...` projected map units.
 */
@Suppress("LongParameterList") // Every parameter is a distinct, independent shape attribute.
class AreaShape(
    val code: String,
    val name: String,
    val parentCode: String,
    val bounds: GeoBounds,
    val labelX: Float,
    val labelY: Float,
    val rings: List<FloatArray>,
)

/** A set of [AreaShape]s at one level (all provinces, or one province's towns), with their union bounds. */
class BoundarySet(
    val areas: List<AreaShape>,
    val bounds: GeoBounds,
) {
    val byCode: Map<String, AreaShape> by lazy { areas.associateBy { it.code } }
}

/** Directory entry for a town, independent of whether its geometry is loaded. */
data class TownRef(
    val code: String,
    val name: String,
    val provinceKey: String,
    val hasGeometry: Boolean,
)

/** Directory entry for a province-level unit. */
data class ProvinceRef(
    val code: String,
    val name: String,
    val regionCode: String,
    val islandGroup: IslandGroup,
)

/** The full town/province directory, independent of loaded geometry. */
class AreaDirectory(
    val towns: Map<String, TownRef>,
    val provinces: Map<String, ProvinceRef>,
)
