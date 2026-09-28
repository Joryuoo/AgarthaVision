package com.agarthavision.domain.session

import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.patient.CodenameGenerator
import java.time.Instant

/**
 * Builds the auto-generated smear label, e.g. `LDNJ-M21-S01`.
 *
 * ```
 * LDNJ-M21-S01
 * ││││ │   └── sequence: zero-padded to 2 digits; widens past 99 without truncation
 * ││││ └────── literal "-S" separator
 * │││└──────── uppercase first-initial of firstname
 * ││└───────── sex-and-age token (M/F + zero-padded age), e.g. M21 or F05
 * │└────────── literal "-" separators
 * └─────────── 3-letter lastname abbreviation (first + middle-index + last letter, letters only)
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
 * like `LDNJ-M21-S01`. Labels remain user-editable, subject to the uniqueness guard.
 *
 * **Label length:** the generated label is well under [MAX_LABEL_LENGTH] (32 characters) by
 * construction — the abbreviation is at most 3 letters, keeping the output fixed-width and
 * short. [MAX_LABEL_LENGTH] must remain synchronized with `SESSION_LABEL_MAX_LENGTH` in
 * `com.agarthavision.ui.sessions.SessionInputLimits` — they are separate constants at the
 * domain and UI layers (not cross-layer references), but must be kept numerically in sync.
 */
object SessionLabelGenerator {

    /** Width the sequence is padded to. Exceeding it widens the label rather than wrapping. */
    const val SEQUENCE_DIGITS = 2

    /**
     * Maximum length of the generated label. Must equal `SESSION_LABEL_MAX_LENGTH` in the UI layer.
     * Kept as a separate constant rather than a cross-layer reference to respect layering.
     * The generator's output no longer needs a runtime length check — the fixed-width 3-letter
     * abbreviation keeps every label well under this cap.
     */
    const val MAX_LABEL_LENGTH = 32

    /**
     * `LDNJ-M21-S01` for [patient] and [sequence], as of [asOf].
     *
     * **Lastname abbreviation** — always 3 letters (or fewer for short names), letters only:
     * - 0 letters → empty segment (e.g., a lastname of pure punctuation omits the abbrev entirely)
     * - 1 or 2 letters → those letters verbatim, uppercased
     * - 3 or more letters → first letter + letter at index `length/2` + last letter (integer
     *   division; indices 0-based). For example `LEDON` (5 letters, indices 0-4): `5/2=2` →
     *   middle is `D` → `LDN`. `GARCIA` (6 letters): `6/2=3` → middle is `C` → `GCA`.
     *
     * Non-letter characters in the lastname are filtered out before abbreviating — hyphens,
     * spaces, apostrophes, and digits are all ignored.
     *
     * The first-initial is the first letter of the firstname, uppercased — so a firstname
     * stored as `maria` still yields `M`. An accented first letter is preserved rather than
     * stripped.
     *
     * **Sex/age token** — delegates to [CodenameGenerator.bucketPrefix] which produces the
     * zero-padded `M21` / `F05` format in `CLINICAL_ZONE`. [asOf] is converted to a clinical
     * date for this computation.
     *
     * **Edge cases:**
     * - Empty/no-letter firstname → initial omitted (e.g. `GCA-F21-S01`).
     * - Lastname with no letters → abbrev empty, prefix is just the first-initial.
     * - Null [Patient.sex] → entire `-SEXAGE-` segment omitted (e.g. `GCAM-S01`).
     *
     * A [sequence] past [SEQUENCE_DIGITS] digits prints in full. Truncating to two digits
     * would silently collide `100` with `00`, and the label field is long enough to carry
     * the extra character.
     */
    fun generate(patient: Patient, sequence: Int, asOf: Instant = Instant.now()): String {
        val padded = sequence.coerceAtLeast(1).toString().padStart(SEQUENCE_DIGITS, '0')
        if (patient.isCodename) {
            // Carry the word-segment (single or stacked) forward intact.
            // e.g. "VISION-M22-001" → word="VISION", sexAge="M22" → "VISION-M22-S01"
            //      "ALPHATEKNOY-F22" → word="ALPHATEKNOY", sexAge="F22" → "ALPHATEKNOY-F22-S01"
            //      "M24-001" / "M24" → no word → "-M24-S01" (leading hyphen intentional)
            val raw = patient.lastname.trim().uppercase()
            val sexAgeMatch = SEX_AGE_REGEX.find(raw)
            val sexAge = sexAgeMatch?.value ?: raw.substringBefore('-')
            val word = sexAgeMatch
                ?.let { raw.substring(0, it.range.first) }
                ?.trimEnd('-')
                ?: ""
            return if (word.isNotEmpty()) "$word-$sexAge-S$padded" else "-$sexAge-S$padded"
        }
        val sexAge = patient.sex?.let {
            CodenameGenerator.bucketPrefix(it, patient.birthdate, asOf.atZone(CLINICAL_ZONE).toLocalDate())
        }
        val abbrev = lastnameAbbrev(patient.lastname)
        val firstInitial = initial(patient.firstname)
        val prefix = "$abbrev$firstInitial"
        return if (sexAge != null) "$prefix-$sexAge-S$padded" else "$prefix-S$padded"
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

    /**
     * Computes the 3-letter (or fewer) uppercase abbreviation of [lastname], considering
     * letters only (non-letter characters are filtered out before computing).
     *
     * - 0 letters → `""`
     * - 1 or 2 letters → those letters verbatim
     * - 3+ letters → first + letter at `length/2` (integer division) + last
     *
     * Examples: `LEDON` → `LDN`; `GARCIA` → `GCA`; `CRUZ` → `CUZ`.
     */
    private fun lastnameAbbrev(lastname: String): String {
        val letters = lastname.filter { it.isLetter() }.uppercase()
        return when (letters.length) {
            0 -> ""
            1, 2 -> letters
            else -> "${letters.first()}${letters[letters.length / 2]}${letters.last()}"
        }
    }

    private fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()

    /** `-S<sequence>` at the end of the label (case-insensitive). */
    private val SEQUENCE_SUFFIX = Regex("""-S(\d+)$""", RegexOption.IGNORE_CASE)

    /** Pattern to extract sex and two-digit age from a codename like "VISION-M22-001" or "M22-001". */
    private val SEX_AGE_REGEX = Regex("""[MF]\d{2}""")
}
