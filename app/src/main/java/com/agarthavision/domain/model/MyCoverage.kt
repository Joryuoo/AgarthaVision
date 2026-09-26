package com.agarthavision.domain.model

import com.agarthavision.domain.geo.IslandGroup

/**
 * Minimum examined smears a town/province needs before its rate is reportable.
 *
 * Cites the same `min_examined = 5` rule as `supabase/migrations/0001_init.sql`'s
 * `barangay_prevalence()` — a rate computed from fewer smears is noise, not a finding.
 */
const val MIN_REPORTABLE_SMEARS = 5

/** One area's reportability tier, derived from [AreaCount]. */
sealed interface AreaStat {
    data object NoData : AreaStat
    data object TooFew : AreaStat
    data class Reported(val smears: Int, val positives: Int) : AreaStat {
        val positiveRate: Double get() = positives.toDouble() / smears
    }
}

data class AreaCount(val smears: Int, val positives: Int) {
    val stat: AreaStat
        get() = when {
            smears == 0 -> AreaStat.NoData
            smears < MIN_REPORTABLE_SMEARS -> AreaStat.TooFew
            else -> AreaStat.Reported(smears, positives)
        }
}

data class ProvinceCoverage(
    val code: String,
    val name: String,
    val islandGroup: IslandGroup,
    val count: AreaCount,
)

/** How the card/map should be framed given which provinces have data this period. */
sealed interface CoverageFraming {
    data object Empty : CoverageFraming
    data class SingleProvince(val code: String) : CoverageFraming
    data class IslandGroupFrame(val group: IslandGroup, val title: String) : CoverageFraming
    data object Country : CoverageFraming
}

/** Domain-layer (Room-free) mirror of `CoverageDao`'s `TownCoverageRow`. */
data class TownCoverage(
    val townCode: String?,
    val smearCount: Int,
    val positiveCount: Int,
)

data class MyCoverage(
    val period: HomePeriod,
    val totals: AreaCount,
    val unlocatedSmears: Int,
    /** Only provinces WITH data (smears > 0), ranked — see `aggregateCoverage` in usecase/coverage. */
    val provinces: List<ProvinceCoverage>,
    /** Province count per island group, for later phases' chips. */
    val islandGroupCounts: Map<IslandGroup, Int>,
    val framing: CoverageFraming,
)
