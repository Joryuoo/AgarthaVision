package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.AreaShape
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.GeoBounds
import com.agarthavision.domain.geo.IslandGroup
import com.agarthavision.domain.model.AreaCount
import com.agarthavision.domain.model.CoverageFraming
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.ProvinceCoverage
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageFitTest {

    private val cebuShape = AreaShape(
        code = "CEB",
        name = "Cebu",
        parentCode = "",
        bounds = GeoBounds(minX = 10f, minY = 10f, maxX = 20f, maxY = 20f),
        labelX = 15f,
        labelY = 15f,
        rings = emptyList(),
    )
    private val boholShape = AreaShape(
        code = "BOH",
        name = "Bohol",
        parentCode = "",
        bounds = GeoBounds(minX = 25f, minY = 25f, maxX = 35f, maxY = 35f),
        labelX = 30f,
        labelY = 30f,
        rings = emptyList(),
    )
    private val countryBounds = GeoBounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)
    private val provinces = BoundarySet(
        areas = listOf(cebuShape, boholShape),
        bounds = countryBounds,
    )

    private fun coverageOf(
        framing: CoverageFraming,
        provincesList: List<ProvinceCoverage> = emptyList(),
    ) = MyCoverage(
        period = HomePeriod.TODAY,
        totals = AreaCount(10, 4),
        unlocatedSmears = 0,
        provinces = provincesList,
        islandGroupCounts = emptyMap(),
        framing = framing,
    )

    @Test
    fun `single province returns that province bounds`() {
        val coverage = coverageOf(
            framing = CoverageFraming.SingleProvince("CEB"),
            provincesList = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4)),
            ),
        )
        val fit = resolveCoverageFitBounds(coverage, provinces)
        assertEquals(cebuShape.bounds, fit)
    }

    @Test
    fun `country framing returns entire country bounds`() {
        val coverage = coverageOf(
            framing = CoverageFraming.Country,
            provincesList = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4)),
            ),
        )
        val fit = resolveCoverageFitBounds(coverage, provinces)
        assertEquals(countryBounds, fit)
    }

    @Test
    fun `island group framing returns union of provinces with data`() {
        val coverage = coverageOf(
            framing = CoverageFraming.IslandGroupFrame(IslandGroup.VISAYAS, "Central Visayas"),
            provincesList = listOf(
                ProvinceCoverage("CEB", "Cebu", IslandGroup.VISAYAS, AreaCount(10, 4)),
                ProvinceCoverage("BOH", "Bohol", IslandGroup.VISAYAS, AreaCount(5, 2)),
            ),
        )
        val fit = resolveCoverageFitBounds(coverage, provinces)
        assertEquals(cebuShape.bounds.union(boholShape.bounds), fit)
    }

    @Test
    fun `empty framing falls back to country bounds`() {
        val coverage = coverageOf(framing = CoverageFraming.Empty)
        val fit = resolveCoverageFitBounds(coverage, provinces)
        assertEquals(countryBounds, fit)
    }
}
