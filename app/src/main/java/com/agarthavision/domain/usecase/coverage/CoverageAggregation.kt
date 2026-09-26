package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import com.agarthavision.domain.model.TownCoverage

/**
 * Rolls per-town smear/positive counts up to provinces, ranks them, and picks a framing —
 * pure, so it is unit-testable without mocks. See `docs/` phase 9 plan for the ranking and
 * framing rules this implements.
 */
fun aggregateCoverage(
    period: HomePeriod,
    townRows: List<TownCoverage>,
    directory: AreaDirectory,
): MyCoverage {
    var totalSmears = 0
    var totalPositives = 0
    var unlocatedSmears = 0
    val perProvince = linkedMapOf<String, AreaCount>()

    for (row in townRows) {
        totalSmears += row.smearCount
        totalPositives += row.positiveCount

        val provinceKey = row.townCode?.let { directory.towns[it]?.provinceKey }
        if (provinceKey == null) {
            unlocatedSmears += row.smearCount
            continue
        }

        val existing = perProvince[provinceKey] ?: AreaCount(0, 0)
        perProvince[provinceKey] = AreaCount(
            smears = existing.smears + row.smearCount,
            positives = existing.positives + row.positiveCount,
        )
    }

    val provinces = perProvince.entries
        .mapNotNull { (code, count) ->
            val ref = directory.provinces[code] ?: return@mapNotNull null
            if (count.smears == 0) return@mapNotNull null
            ProvinceCoverage(code = code, name = ref.name, islandGroup = ref.islandGroup, count = count)
        }
        .sortedWith(provinceRankingComparator())

    val islandGroupCounts = provinces
        .groupingBy { it.islandGroup }
        .eachCount()

    return MyCoverage(
        period = period,
        totals = AreaCount(totalSmears, totalPositives),
        unlocatedSmears = unlocatedSmears,
        provinces = provinces,
        islandGroupCounts = islandGroupCounts,
        framing = resolveFraming(provinces),
    )
}

/**
 * Reported areas first (by rate desc, then smears desc, then name asc), then TooFew areas
 * alphabetically after all Reported ones. NoData provinces never reach this comparator —
 * they are excluded from the list entirely before ranking.
 */
private fun provinceRankingComparator(): Comparator<ProvinceCoverage> =
    compareBy<ProvinceCoverage> { province ->
        when (province.count.stat) {
            is AreaStat.Reported -> 0
            else -> 1
        }
    }.thenByDescending { province ->
        (province.count.stat as? AreaStat.Reported)?.positiveRate ?: 0.0
    }.thenByDescending { province ->
        (province.count.stat as? AreaStat.Reported)?.smears ?: 0
    }.thenBy { it.name }

private fun resolveFraming(provinces: List<ProvinceCoverage>): CoverageFraming {
    return when (provinces.size) {
        0 -> CoverageFraming.Empty
        1 -> CoverageFraming.SingleProvince(provinces.first().code)
        else -> {
            val groups = provinces.map { it.islandGroup }.distinct()
            if (groups.size == 1) {
                val group = groups.first()
                CoverageFraming.IslandGroupFrame(group, title = group.displayName())
            } else {
                CoverageFraming.Country
            }
        }
    }
}

private fun IslandGroup.displayName(): String = when (this) {
    IslandGroup.LUZON -> "Luzon"
    IslandGroup.VISAYAS -> "Visayas"
    IslandGroup.MINDANAO -> "Mindanao"
}
