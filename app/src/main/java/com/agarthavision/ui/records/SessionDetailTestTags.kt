package com.agarthavision.ui.records

/**
 * Test tags for Session Detail's reported figures.
 *
 * Only the LPF block carries tags so far, and deliberately so: it is the part of this screen
 * that states a clinical result, and the one whose absence has to be distinguishable from a
 * negative result. The rest of the screen is chrome around it.
 */
object SessionDetailTestTags {

    /**
     * The "no parasites found" statement.
     *
     * A wholly negative session is a real result, so this is an assertion the screen makes, not
     * an empty state it falls into. A test can tell the two apart by this tag existing.
     */
    const val NO_PARASITES = "session_detail_no_parasites"

    /**
     * The statement that no field has been verified, so there is no result yet.
     *
     * Shown in place of [NO_PARASITES] for a session with no verified sample, which would
     * otherwise read exactly like a negative one (14zcqntk7bg).
     */
    const val NOT_READ = "session_detail_not_read"

    /** The row reporting one species' range. Keyed on the species so a test names the one it means. */
    fun lpfRow(species: String): String = "lpf_row_" + species
}
