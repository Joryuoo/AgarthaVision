package com.agarthavision.domain.patient

import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Sex
import java.time.LocalDate
import java.time.Period

/**
 * Generates patient codenames formatted as `{SEXAGE}-{PATIENTNUM}` (e.g. `M24-001`).
 *
 * Used when a patient record is created without a name for privacy or convenience.
 * Pure Kotlin (C2) with clinical dates resolved in [CLINICAL_ZONE] (Asia/Manila).
 */
object CodenameGenerator {

    val CODENAME_REGEX = Regex("""^(?:VISION-)?[MF]\d{2}-\d{3,}$""")
    const val CODENAME_PREFIX = "VISION"
    private const val AGE_DIGITS = 2
    private const val PATIENT_NUMBER_DIGITS = 3

    /**
     * True if [name] matches the codename pattern `{SEXAGE}-{PATIENTNUM}` or `VISION-{SEXAGE}-{PATIENTNUM}`.
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
     * Determines the next sequential patient number for [prefix] among [existingCodenames].
     * Starts at 1 (`001`) if none exist in this bucket.
     */
    fun nextPatientNumber(prefix: String, existingCodenames: List<String>): Int {
        val prefixRegex = Regex("""^(?:$CODENAME_PREFIX-)?$prefix-(\d+)$""")
        val max = existingCodenames.mapNotNull { name ->
            prefixRegex.find(name.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return max + 1
    }

    /**
     * Generates `VISION-{SEXAGE}-{PATIENTNUM}` (e.g. `VISION-M22-001`, `VISION-F05-002`).
     */
    fun generate(
        sex: Sex,
        birthdate: LocalDate,
        existingCodenames: List<String>,
        today: LocalDate = LocalDate.now(CLINICAL_ZONE),
    ): String {
        val prefix = bucketPrefix(sex, birthdate, today)
        val nextSeq = nextPatientNumber(prefix, existingCodenames)
        val seqStr = nextSeq.toString().padStart(PATIENT_NUMBER_DIGITS, '0')
        return "$CODENAME_PREFIX-$prefix-$seqStr"
    }
}
