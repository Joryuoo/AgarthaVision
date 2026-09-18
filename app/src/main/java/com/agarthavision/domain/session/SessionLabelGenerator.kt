package com.agarthavision.domain.session

import com.agarthavision.domain.model.Patient

/**
 * Builds the auto-generated smear label, `C.G.-0730600000-001`.
 *
 * ```
 * C.G.-0730600000-001
 * │ │   │          └── sequence: the Nth smear for this patient
 * │ │   └───────────── the patient's 10-digit PSGC barangay code
 * │ └───────────────── firstname initial  (Gerald)
 * └─────────────────── lastname initial   (Cruz)
 * ```
 *
 * Pure Kotlin, no Android and no repository (C2): it takes a [Patient] and a sequence number
 * and returns a string. Everything that has to touch a database — reading the patient's
 * existing labels — happens in the use case above it.
 *
 * **The label is cosmetic, editable, and deliberately not unique.** Two devices working
 * offline will both mint `-001` for the same patient, and that is accepted: the session UUID
 * is the real key and is globally unique. Nothing enforces uniqueness — not a CHECK, not an
 * index, not this class. Anyone reaching for a server round-trip, a device discriminator or a
 * reservation scheme should read this paragraph first: the label exists to orient a medtech
 * looking at a list, not to identify a row.
 */
object SessionLabelGenerator {

    /** Width the sequence is padded to. Exceeding it widens the label rather than wrapping. */
    const val SEQUENCE_DIGITS = 3

    /**
     * `C.G.-0730600000-001` for [patient] and [sequence].
     *
     * An initial is uppercased, so a lastname stored as `cruz` still yields `C.`, and an
     * accented first letter is preserved rather than stripped. A name with no letter at all
     * contributes no initial instead of a placeholder — the form requires both names, so that
     * only happens on a hand-edited row, and `?.` in a clinical label reads worse than a
     * slightly shorter one.
     *
     * A [sequence] past [SEQUENCE_DIGITS] digits prints in full (`1000`). Truncating it to the
     * last three would silently collide `1000` with the existing `000`, and the label field is
     * long enough to carry the extra character.
     */
    fun generate(patient: Patient, sequence: Int): String {
        val initials = initial(patient.lastname) + initial(patient.firstname)
        val padded = sequence.coerceAtLeast(1).toString().padStart(SEQUENCE_DIGITS, '0')
        return "$initials-${patient.psgcBarangayCode}-$padded"
    }

    /**
     * The next sequence for a patient: one past the highest already minted, or `1` when the
     * patient has no labelled smear yet.
     *
     * Labels the medtech has edited into some other shape are ignored rather than treated as
     * zero — an edited label is not a claim on a sequence number, and reading one as `000`
     * would hand the next smear a number that is already taken.
     */
    fun nextSequence(existingLabels: List<String>): Int =
        existingLabels.mapNotNull(::sequenceOf).maxOrNull()?.plus(1) ?: 1

    /**
     * The trailing sequence in [label], or null when it does not end in one.
     *
     * The whole tail is matched, not just the trailing digits: the PSGC code is also a run of
     * digits and it is the last thing left when the sequence is edited away, so an end-anchor
     * alone still reads the barangay as a ten-digit sequence. Requiring the barangay segment
     * in front of it is what makes the difference legible.
     */
    private fun sequenceOf(label: String): Int? =
        SEQUENCE_SUFFIX.find(label.trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.let { "$it." }.orEmpty()

    /** `-<10-digit barangay>-<sequence>` at the end of the label. */
    private val SEQUENCE_SUFFIX = Regex("""-\d{10}-(\d+)$""")
}
