package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.MyCoverage

/**
 * Resolves which [GeoBounds] the coverage map should fit to for [coverage], given the loaded
 * [provinces]. Shared by `MyCoverageCardViewModel` (static fit for the Home card) and the
 * full-screen `MyCoverageViewModel` (initial fit before the medtech pans/zooms) so the two
 * screens never disagree on framing.
 *
 * A [CoverageFraming.SingleProvince] fits exactly that province; otherwise the union of every
 * province with data, falling back to the whole country if none has data.
 */
fun resolveCoverageFitBounds(coverage: MyCoverage, provinces: BoundarySet): GeoBounds {
    val framing = coverage.framing
    return when {
        framing is CoverageFraming.SingleProvince ->
            provinces.byCode[framing.code]?.bounds ?: provinces.bounds
        framing is CoverageFraming.Country ->
            provinces.bounds
        else -> {
            val withData = coverage.provinces.mapNotNull { provinces.byCode[it.code]?.bounds }
            withData.reduceOrNull(GeoBounds::union) ?: provinces.bounds
        }
    }
}

/** Union of every province with data; whole country only when none has data. Full-screen map only. */
fun resolveDataFitBounds(coverage: MyCoverage, provinces: BoundarySet): GeoBounds =
    coverage.provinces.mapNotNull { provinces.byCode[it.code]?.bounds }
        .reduceOrNull(GeoBounds::union)
        ?: provinces.bounds
