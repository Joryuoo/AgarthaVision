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
    if (framing is CoverageFraming.SingleProvince) {
        provinces.byCode[framing.code]?.bounds?.let { return it }
    }
    val withData = coverage.provinces.mapNotNull { provinces.byCode[it.code]?.bounds }
    return withData.reduceOrNull(GeoBounds::union) ?: provinces.bounds
}
