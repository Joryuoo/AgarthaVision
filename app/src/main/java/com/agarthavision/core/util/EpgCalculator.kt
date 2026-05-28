package com.agarthavision.core.util

/**
 * Kato-Katz EPG multiplier utility.
 *
 * TODO(DMKuZu): Insert the official citation for the multiplier value (paper / DOH bulletin / WHO guideline).
 */
object EpgCalculator {
    /**
     * Kato-Katz volumetric multiplier for EPG computation.
     */
    const val MULTIPLIER = 24

    /**
     * Returns eggs per gram (EPG) for the supplied egg count.
     */
    fun epg(eggCount: Int): Int = eggCount * MULTIPLIER
}
