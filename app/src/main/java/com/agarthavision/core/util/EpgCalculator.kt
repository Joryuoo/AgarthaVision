package com.agarthavision.core.util

/**
 * Kato-Katz EPG multiplier utility.
 *
 * RETIRED (86d4a6jxw): Philippine medtechs use Direct Smear, so EPG is replaced by LPF density.
 * This class is kept for history and will be removed in a follow-up chore.
 */
@Deprecated("Use LPF density instead. EPG is wrong for Direct Smear.")
object EpgCalculator {
    /**
     * Kato-Katz volumetric multiplier for EPG computation.
     */
    const val MULTIPLIER = 24

    /**
     * Returns eggs per gram (EPG) for the supplied egg count.
     */
    @Deprecated("EPG is retired.")
    fun epg(eggCount: Int): Int = eggCount * MULTIPLIER
}
