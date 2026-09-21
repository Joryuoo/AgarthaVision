package com.agarthavision.domain.session

import com.agarthavision.domain.model.Patient

/**
 * Builds the auto-generated smear label, `GarciaM-S01`.
 *
 * ```
 * GarciaM-S01
 * │      │ └── sequence: zero-padded to 2 digits; widens past 99 without truncation
 * │      └──── literal "-S" separator
 * └─────────── lastname (whitespace-stripped) + uppercase first-initial of firstname
 * ```
 *
 * Pure Kotlin, no Android and no repository (C2): it takes a [Patient] and a sequence number
 * and returns a string. Everything that has to touch a database — reading the patient's
 * existing labels and checking for collisions — happens in the use case above it.
 *
 * **Labels are unique per patient**, enforced at two levels:
 * 1. A unique index on `sessions(patient_id, label)` in [com.agarthavision.data.local.entity.SessionEntity].
 * 2. A pre-check via [com.agarthavision.domain.repository.SessionRepository.isSessionLabelTaken]
 *    in [com.agarthavision.domain.usecase.sessions.GenerateSessionLabelUseCase] and in
 *    [com.agarthavision.ui.sessions.SessionsViewModel].
 *
 * Labels are still per-patient scoped, not globally unique — two patients may share a label
 * like `GarciaM-S01`. Labels remain user-editable, subject to the uniqueness guard.
 */
object SessionLabelGenerator {

    /** Width the sequence is padded to. Exceeding it widens the label rather than wrapping. */
    const val SEQUENCE_DIGITS = 2

    /**
     * `GarciaM-S01` for [patient] and [sequence].
     *
     * The lastname is whitespace-stripped and used as-is (preserving its stored casing).
     * The first-initial is the first letter of the firstname, uppercased — so a firstname
     * stored as `maria` still yields `M`. An accented first letter is preserved rather than
     * stripped.
     *
     * **Edge cases:**
     * - Empty/no-letter firstname → initial omitted (e.g. `Garcia-S01`). A placeholder
     *   char in a clinical label reads worse than a slightly shorter one.
     * - Lastname with no letters → no lastname segment (e.g. `M-S01` if firstname has a
     *   letter; `-S01` if neither name has any letters). The session ID is the real key;
     *   the label still functions as an ordinal marker.
     * - Whitespace in lastname → stripped.
     *
     * A [sequence] past [SEQUENCE_DIGITS] digits prints in full. Truncating to two digits
     * would silently collide `100` with `00`, and the label field is long enough to carry
     * the extra character.
     */
    fun generate(patient: Patient, sequence: Int): String {
        val lastname = patient.lastname.trim().uppercase()
        val firstInitial = initial(patient.firstname)
        val padded = sequence.coerceAtLeast(1).toString().padStart(SEQUENCE_DIGITS, '0')
        return "$lastname$firstInitial-S$padded".uppercase()
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
     * The trailing sequence in [label], or null when it does not end in the `-S<digits>` tail.
     *
     * The whole tail is matched with a case-insensitive end-anchor so an edited label that
     * happens to end in a bare digit run (but not the `-S` prefix) does not accidentally parse
     * as a sequence number.
     */
    private fun sequenceOf(label: String): Int? =
        SEQUENCE_SUFFIX.find(label.trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()

    /** `-S<sequence>` at the end of the label (case-insensitive). */
    private val SEQUENCE_SUFFIX = Regex("""-S(\d+)$""", RegexOption.IGNORE_CASE)
}
