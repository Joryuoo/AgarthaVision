package com.agarthavision.domain.model

/**
 * Low Power Field (LPF) density metrics for one species across a session.
 *
 * Philippine medtechs use Direct Smear rather than Kato-Katz, so density is reported as a
 * range and a mean per LPF rather than a calculated Eggs Per Gram (EPG).
 */
data class LpfDensity(
    val mean: Float,
    val min: Int,
    val max: Int,
)
