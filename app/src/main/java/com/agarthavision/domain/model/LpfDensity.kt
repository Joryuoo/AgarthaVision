package com.agarthavision.domain.model

/**
 * What was seen of one species across the fields of a session, as a **range per low power
 * field** — the lowest and highest counts in any single field.
 *
 * Philippine medtechs use Direct Smear rather than Kato-Katz, so there is no eggs-per-gram to
 * report (PB-16). Consultation settled the session-level figure as a min–max range rather than
 * a mean or a single worst field: `Ascaris lumbricoides — 0–2 LPF`.
 *
 * **The mean is gone, and its absence is the correction.** 86d4a6jxw's build spec defined the
 * figure as "eggs of that species summed across the session, divided by the count of recorded
 * frames", and separately left "mean, or the highest single field?" open. It is neither. A mean
 * averages a single heavy field away under nine clean ones, which is exactly the field a
 * medtech needs to see.
 *
 * @property min the lowest count in any single field. **Zero whenever the species was absent
 *   from at least one field** — an absent species contributes a zero, not a gap. Dropping the
 *   empty fields and taking the smallest non-zero count would systematically overstate every
 *   result on the report.
 * @property max the highest count in any single field.
 */
data class LpfDensity(
    val min: Int,
    val max: Int,
) {
    /**
     * The qualitative reading, derived from [max] rather than stored.
     *
     * Derived on purpose: a descriptor persisted alongside the range can drift out of step with
     * it, and there is no version of this data where the two should disagree. It also means no
     * migration and no schema change — an older stored report carries `min` and `max` already,
     * and its extra `mean` is simply ignored on read.
     */
    val descriptor: LpfDescriptor? get() = LpfDescriptor.forMax(max)
}

/**
 * The conventional semi-quantitative wet-mount scale, read per organism at the 10× objective.
 *
 * **Taken from the worst field, not the mean and not the range as a whole.** A single heavy
 * field is what drives clinical attention and must not be averaged away by nine clean ones.
 * It attaches per species for the same reason the range does: a field holding Ascaris and
 * hookworm is two independent readings, not one combined figure.
 *
 * **This is not a WHO tier and must never be presented as one.** WHO's light/moderate/heavy
 * bands are defined only as eggs-per-gram measured by Kato-Katz; there is no WHO or DOH
 * intensity table for direct fecal smear, and PB-16 deleted the machinery that claimed one.
 * This scale describes what the medtech saw down the microscope. It carries no clinical
 * classification, and the bands below would need clinical sign-off before they ever did.
 */
enum class LpfDescriptor {
    RARE,
    FEW,
    MODERATE,
    NUMEROUS,
    ;

    companion object {
        /**
         * The band the highest single field falls in, or null when the species was never seen.
         *
         * Null rather than a fifth "none" band: a species with no eggs in any field has nothing
         * to describe, and a report that names a descriptor for it is saying something about an
         * organism it did not find.
         */
        fun forMax(max: Int): LpfDescriptor? = when {
            max <= 0 -> null
            max <= RARE_MAX -> RARE
            max <= FEW_MAX -> FEW
            max <= MODERATE_MAX -> MODERATE
            else -> NUMEROUS
        }

        /**
         * Band ceilings. Direct-smear helminth counts at 10× are small numbers, so these are
         * far tighter than the rare/few/moderate/numerous bands used for cells or crystals.
         *
         * Both boundaries PB-17's worked examples imply are pinned by tests: a worst field of 4
         * reads "few", and one of 12 reads "numerous".
         */
        const val RARE_MAX = 2
        const val FEW_MAX = 5
        const val MODERATE_MAX = 10
    }
}
