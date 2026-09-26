package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.geo.ProvinceRef
import com.agarthavision.domain.geo.TownRef
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.TownCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageAggregationTest {

    private fun directoryOf(vararg provinces: Pair<ProvinceRef, List<String>>): AreaDirectory {
        val provinceMap = provinces.associate { (ref, _) -> ref.code to ref }
        val townMap = provinces.flatMap { (ref, townCodes) ->
            townCodes.map { code -> code to TownRef(code, "Town $code", ref.code, hasGeometry = true) }
        }.toMap()
        return AreaDirectory(towns = townMap, provinces = provinceMap)
    }

    private fun province(code: String, name: String, group: IslandGroup) =
        ProvinceRef(code = code, name = name, regionCode = "0100000000", islandGroup = group)

    @Test
    fun `4 smears is TooFew, 5 is Reported, 0 is absent`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val directory = directoryOf(cebu to listOf("town-4", "town-5", "town-0"))

        val rows = listOf(
            TownCoverage("town-4", smearCount = 4, positiveCount = 1),
        )
        val coverage = aggregateCoverage(HomePeriod.TODAY, rows, directory)
        assertEquals(1, coverage.provinces.size)
        assertEquals(AreaStat.TooFew, coverage.provinces.first().count.stat)

        val reportedRows = listOf(TownCoverage("town-5", smearCount = 5, positiveCount = 2))
        val reportedCoverage = aggregateCoverage(HomePeriod.TODAY, reportedRows, directory)
        assertTrue(reportedCoverage.provinces.first().count.stat is AreaStat.Reported)

        val zeroRows = emptyList<TownCoverage>()
        val zeroCoverage = aggregateCoverage(HomePeriod.TODAY, zeroRows, directory)
        assertTrue(zeroCoverage.provinces.isEmpty())
    }

    @Test
    fun `NoData is distinct from TooFew, never appearing in the ranked list`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val directory = directoryOf(cebu to listOf("town-a"))

        val coverage = aggregateCoverage(HomePeriod.TODAY, emptyList(), directory)
        assertTrue(coverage.provinces.none { it.code == "CEB" })
    }

    @Test
    fun `ranking orders Reported by rate desc then smears desc then name, TooFew after`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val bohol = province("BOH", "Bohol", IslandGroup.VISAYAS)
        val leyte = province("LEY", "Leyte", IslandGroup.VISAYAS)
        val samar = province("SAM", "Samar", IslandGroup.VISAYAS)
        val directory = directoryOf(
            cebu to listOf("t-ceb"),
            bohol to listOf("t-boh"),
            leyte to listOf("t-ley"),
            samar to listOf("t-sam"),
        )

        val rows = listOf(
            TownCoverage("t-ceb", smearCount = 10, positiveCount = 5), // rate 0.5
            TownCoverage("t-boh", smearCount = 20, positiveCount = 10), // rate 0.5, more smears
            TownCoverage("t-ley", smearCount = 3, positiveCount = 1), // TooFew
            TownCoverage("t-sam", smearCount = 2, positiveCount = 0), // TooFew
        )
        val coverage = aggregateCoverage(HomePeriod.TODAY, rows, directory)

        assertEquals(listOf("BOH", "CEB", "LEY", "SAM"), coverage.provinces.map { it.code })
    }

    @Test
    fun `framing rules- Empty, SingleProvince, IslandGroupFrame, Country`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val bohol = province("BOH", "Bohol", IslandGroup.VISAYAS)
        val manila = province("MNL", "Metro Manila", IslandGroup.LUZON)
        val directory = directoryOf(
            cebu to listOf("t-ceb"),
            bohol to listOf("t-boh"),
            manila to listOf("t-mnl"),
        )

        val empty = aggregateCoverage(HomePeriod.TODAY, emptyList(), directory)
        assertEquals(CoverageFraming.Empty, empty.framing)

        val single = aggregateCoverage(
            HomePeriod.TODAY,
            listOf(TownCoverage("t-ceb", 10, 5)),
            directory,
        )
        assertEquals(CoverageFraming.SingleProvince("CEB"), single.framing)

        val sameGroup = aggregateCoverage(
            HomePeriod.TODAY,
            listOf(TownCoverage("t-ceb", 10, 5), TownCoverage("t-boh", 10, 2)),
            directory,
        )
        assertTrue(sameGroup.framing is CoverageFraming.IslandGroupFrame)
        assertEquals(IslandGroup.VISAYAS, (sameGroup.framing as CoverageFraming.IslandGroupFrame).group)

        val multiGroup = aggregateCoverage(
            HomePeriod.TODAY,
            listOf(TownCoverage("t-ceb", 10, 5), TownCoverage("t-mnl", 10, 2)),
            directory,
        )
        assertEquals(CoverageFraming.Country, multiGroup.framing)
    }

    @Test
    fun `identical rate and identical smear count tie-break to name ascending, deterministically`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val bohol = province("BOH", "Bohol", IslandGroup.VISAYAS)
        val directory = directoryOf(
            cebu to listOf("t-ceb"),
            bohol to listOf("t-boh"),
        )

        // Same rate (0.5), same smear count (10) for both — only name should decide order.
        val rows = listOf(
            TownCoverage("t-ceb", smearCount = 10, positiveCount = 5),
            TownCoverage("t-boh", smearCount = 10, positiveCount = 5),
        )

        // Run repeatedly with input order swapped to rule out hash map / input-order dependence.
        val forward = aggregateCoverage(HomePeriod.TODAY, rows, directory)
        val reversed = aggregateCoverage(HomePeriod.TODAY, rows.reversed(), directory)

        assertEquals(listOf("BOH", "CEB"), forward.provinces.map { it.code })
        assertEquals(listOf("BOH", "CEB"), reversed.provinces.map { it.code })
    }

    @Test
    fun `a townCode with a provinceKey missing from the directory doesn't crash`() {
        // Simulates a directory/asset mismatch: the town resolves to a provinceKey that has no
        // corresponding ProvinceRef. Phase 8's join guarantees this won't happen in practice, but
        // aggregation shouldn't blow up if it ever did.
        val orphanTown = TownRef(code = "t-orphan", name = "Orphan town", provinceKey = "GHOST", hasGeometry = true)
        val directory = AreaDirectory(towns = mapOf("t-orphan" to orphanTown), provinces = emptyMap())

        val rows = listOf(TownCoverage("t-orphan", smearCount = 10, positiveCount = 3))
        val coverage = aggregateCoverage(HomePeriod.TODAY, rows, directory)

        assertTrue(coverage.provinces.isEmpty())
        assertEquals(10, coverage.totals.smears)
        assertEquals(0, coverage.unlocatedSmears)
    }

    @Test
    fun `unlocated smears - null and unrecognized townCode both land in unlocatedSmears`() {
        val cebu = province("CEB", "Cebu", IslandGroup.VISAYAS)
        val directory = directoryOf(cebu to listOf("t-ceb"))

        val rows = listOf(
            TownCoverage(null, smearCount = 3, positiveCount = 1),
            TownCoverage("unknown-town", smearCount = 2, positiveCount = 0),
            TownCoverage("t-ceb", smearCount = 5, positiveCount = 2),
        )
        val coverage = aggregateCoverage(HomePeriod.TODAY, rows, directory)

        assertEquals(5, coverage.unlocatedSmears)
        assertEquals(1, coverage.provinces.size)
        assertNull(coverage.provinces.firstOrNull { it.code != "CEB" })
        assertEquals(10, coverage.totals.smears)
    }
}
