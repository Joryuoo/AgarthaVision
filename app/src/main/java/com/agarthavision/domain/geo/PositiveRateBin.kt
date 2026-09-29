package com.agarthavision.domain.geo

/**
 * Buckets a positive rate into one of five choropleth shading bins.
 *
 * Bin boundaries are half-open on the low end and closed on the high end of the previous bin,
 * i.e. `0.10` itself falls in bin 2, not bin 1.
 */
object PositiveRateBin {
    private const val BIN_1_UPPER = 0.10
    private const val BIN_2_UPPER = 0.20
    private const val BIN_3_UPPER = 0.40

    private const val BIN_0 = 0
    private const val BIN_1 = 1
    private const val BIN_2 = 2
    private const val BIN_3 = 3
    private const val BIN_4 = 4

    fun of(rate: Double): Int =
        when {
            rate <= 0.0 -> BIN_0
            rate < BIN_1_UPPER -> BIN_1
            rate < BIN_2_UPPER -> BIN_2
            rate < BIN_3_UPPER -> BIN_3
            else -> BIN_4
        }
}
