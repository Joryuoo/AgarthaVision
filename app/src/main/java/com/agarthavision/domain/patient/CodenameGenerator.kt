package com.agarthavision.domain.patient

import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Sex
import java.time.LocalDate
import java.time.Period

/**
 * Generates patient codenames formatted as `<WORD(S)>-<SEXAGE>` (e.g. `ALPHA-M24`, `BRAVO-F05`,
 * `ALPHATANGO-M24` when stacking on collision).
 *
 * Used when a patient record is created without a name for privacy or convenience.
 * Pure Kotlin (C2) with clinical dates resolved in [CLINICAL_ZONE] (Asia/Manila).
 */
object CodenameGenerator {

    /**
     * Recognises both the legacy numbered format and the new word-stacked format:
     * - Old: `VISION-M22-001`, `M24-001`, `VISION-M00-1234`
     * - New: `ALPHA-F22`, `ALPHATEKNOY-F22`, `M24`
     */
    val CODENAME_REGEX = Regex("""^(?:[A-Z]+-)?[MF]\d{2}(?:-\d{3,})?$""")

    private const val AGE_DIGITS = 2

    private val WORD_POOL: List<String> = listOf(
        "ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL",
        "INDIA", "JULIETT", "KILO", "LIMA", "MIKE", "NOVEMBER", "OSCAR", "PAPA",
        "QUEBEC", "ROMEO", "SIERRA", "TANGO", "UNIFORM", "VICTOR", "WHISKEY",
        "XRAY", "YANKEE", "ZULU", "TEKNOY", "AGARTHA", "VISION", "WILDCAT",
    )

    /**
     * True if [name] matches the codename pattern — either the legacy `{SEXAGE}-{NUM}` /
     * `VISION-{SEXAGE}-{NUM}` shape or the new `<WORD(S)>-{SEXAGE}` shape.
     */
    fun isCodename(name: String?): Boolean =
        name != null && CODENAME_REGEX.matches(name.trim())

    /**
     * Computes the bucket prefix (e.g. `M24`, `F05`, `M00`) from sex and birthdate
     * at Manila midnight.
     */
    fun bucketPrefix(
        sex: Sex,
        birthdate: LocalDate,
        today: LocalDate = LocalDate.now(CLINICAL_ZONE),
    ): String {
        val sexChar = when (sex) {
            Sex.MALE -> 'M'
            Sex.FEMALE -> 'F'
        }
        val age = if (birthdate.isAfter(today)) 0 else Period.between(birthdate, today).years
        val ageStr = age.coerceAtLeast(0).toString().padStart(AGE_DIGITS, '0')
        return "$sexChar$ageStr"
    }

    /**
     * Generates a codename in the format `<WORD(S)>-<SEXAGE>` (e.g. `ALPHA-M24`,
     * `BRAVO-F05`).
     *
     * A single random word from [WORD_POOL] is drawn and combined with the SEXAGE bucket prefix.
     * On collision (the candidate already appears in [existingCodenames]), another random word is
     * concatenated — without a separator — onto the existing word segment and the new candidate is
     * re-checked (e.g. `ALPHATANGO-M24`).
     *
     * This loop terminates: every pool word is non-empty, so each stacking iteration strictly
     * increases the candidate's total length. A longer string cannot equal any previously seen
     * shorter candidate, so every pass either finds a non-colliding string immediately or
     * produces a candidate that has never been generated before in this loop. Given that the
     * existing set is finite, the loop must exit — there is no arbitrary retry cap and no
     * numeric fallback.
     */
    fun generate(
        sex: Sex,
        birthdate: LocalDate,
        existingCodenames: List<String>,
        today: LocalDate = LocalDate.now(CLINICAL_ZONE),
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): String {
        val prefix = bucketPrefix(sex, birthdate, today)
        val taken = existingCodenames.map { it.trim().uppercase() }.toSet()
        var words = WORD_POOL[random.nextInt(WORD_POOL.size)]
        var candidate = "$words-$prefix"
        while (candidate in taken) {
            words += WORD_POOL[random.nextInt(WORD_POOL.size)]
            candidate = "$words-$prefix"
        }
        return candidate
    }
}
