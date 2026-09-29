package com.agarthavision.domain.geo

/**
 * Which of the three main island groups a PSGC region belongs to.
 *
 * Mirrors the Python mapping in `tools/geo/build-town-keys.py` (`island_group_of`) — the two
 * must agree, and [com.agarthavision.data.geo.GeoDatasetIntegrityTest] (see that test's
 * counterpart under `data/geo/`) exercises this against every province the pipeline emits.
 */
enum class IslandGroup {
    LUZON,
    VISAYAS,
    MINDANAO,
    ;

    companion object {
        private val LUZON_PREFIXES = setOf("01", "02", "03", "04", "05", "13", "14", "17")
        private val VISAYAS_PREFIXES = setOf("06", "07", "08", "18")
        private val MINDANAO_PREFIXES = setOf("09", "10", "11", "12", "16", "19")

        /**
         * Resolves a 10-digit PSGC region code (e.g. `"1300000000"`) to its island group by
         * the region's first two digits.
         *
         * @throws IllegalArgumentException if the prefix matches none of the 18 known regions.
         */
        fun fromRegionCode(code: String): IslandGroup {
            val prefix = code.take(2)
            return when (prefix) {
                in LUZON_PREFIXES -> LUZON
                in VISAYAS_PREFIXES -> VISAYAS
                in MINDANAO_PREFIXES -> MINDANAO
                else -> throw IllegalArgumentException("Unknown region code prefix '$prefix' in '$code'")
            }
        }
    }
}
