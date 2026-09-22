package com.agarthavision.domain.session

import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure tests for the smear-label generator. No Room, no Hilt, no coroutines — the whole point
 * of keeping it out of the repository is that it can be checked like this.
 *
 * Format: `<abbrev><firstInitial>-<SEXAGE>-S<NN>` — e.g. `LDNJ-M21-S01`, where the abbrev
 * is the 3-letter lastname abbreviation (first + middle-index + last letter, letters only)
 * and SEXAGE is the sex-and-age token from [com.agarthavision.domain.patient.CodenameGenerator].
 */
class SessionLabelGeneratorTest {

    // ---------- the format ----------

    @Test
    fun `builds the documented label`() {
        // Garcia -> GARCIA (6 letters), 6/2=3 -> C -> GCA; Maria -> M; F21
        assertEquals(
            "GCAM-F21-S01",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 1,
                asOf = asOf,
            ),
        )
    }

    @Test
    fun `firstname initial is uppercased when stored lowercase`() {
        // cruz -> CRUZ (4 letters), 4/2=2 -> U -> CUZ; gerald -> G; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "cruz", firstname = "gerald"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("CUZG-F21-S01", label)
    }

    @Test
    fun `an accented initial survives`() {
        // Ñ is a real first letter of Philippine surnames (Ñuñez). Stripping the diacritic
        // would print a different person's initial, so it is kept as-is.
        // ñuñez -> ÑUÑEZ (5 letters), 5/2=2 -> Ñ -> ÑÑZ; Élia -> É; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "ñuñez", firstname = "Élia"),
            sequence = 2,
            asOf = asOf,
        )
        assertEquals("ÑÑZÉ-F21-S02", label)
    }

    @Test
    fun `leading and trailing whitespace in lastname is stripped`() {
        // "  Cruz  " -> filter letters -> CRUZ (4 letters), 4/2=2 -> U -> CUZ; Gerald -> G; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "  Cruz  ", firstname = "Gerald"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("CUZG-F21-S01", label)
    }

    @Test
    fun `empty firstname yields no initial`() {
        // A placeholder char in a clinical label reads worse than a slightly shorter one.
        // Garcia -> GCA; "" -> no initial; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Garcia", firstname = ""),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("GCA-F21-S01", label)
    }

    @Test
    fun `firstname with no letters yields no initial`() {
        // Garcia -> GCA; "123" -> no letter initial; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Garcia", firstname = "123"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("GCA-F21-S01", label)
    }

    @Test
    fun `firstname starting with punctuation still yields a letter initial`() {
        // The initial() helper skips non-letter characters and takes the first letter.
        // "Dela Cruz" -> filter letters -> DELACRUZ (8 letters), 8/2=4 -> C -> DCZ; "'Gerald" -> G; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Dela Cruz", firstname = "'Gerald"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("DCZG-F21-S01", label)
    }

    @Test
    fun `a sequence past two digits widens rather than wrapping`() {
        // Truncating to the last two digits would print 00 and collide with a label that
        // already exists. The field is 32 characters; it has the room.
        assertEquals(
            "GCAM-F21-S100",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 100,
                asOf = asOf,
            ),
        )
    }

    @Test
    fun `sequence zero is coerced to one`() {
        // coerceAtLeast(1) prevents S00 which would be confusing and imply ordinal 0.
        assertEquals(
            "GCAM-F21-S01",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 0,
                asOf = asOf,
            ),
        )
    }

    @Test
    fun `single-digit sequences are zero-padded to two digits`() {
        assertEquals(
            "GCAM-F21-S09",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 9,
                asOf = asOf,
            ),
        )
    }

    // ---------- lastname abbreviation edge cases ----------

    @Test
    fun `ticket example - Ledon Jhon male age 21 yields LDNJ-M21-S01`() {
        // LEDON (5 letters), 5/2=2 -> D -> LDN; Jhon -> J; MALE age 21 -> M21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Ledon", firstname = "Jhon", sex = Sex.MALE),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("LDNJ-M21-S01", label)
    }

    @Test
    fun `1-letter lastname uses the single letter as abbreviation`() {
        // "A" -> letters = "A", length = 1 -> abbrev = "A"; Jose -> J; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "A", firstname = "Jose"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("AJ-F21-S01", label)
    }

    @Test
    fun `2-letter lastname uses both letters as abbreviation`() {
        // "Li" -> letters = "LI", length = 2 -> abbrev = "LI"; Ana -> A; F21
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Li", firstname = "Ana"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("LIA-F21-S01", label)
    }

    @Test
    fun `all-non-letter lastname yields empty abbreviation segment`() {
        // "---" -> filter letters -> "" -> abbrev = ""; Maria -> M; F21; prefix = "M"
        val label = SessionLabelGenerator.generate(
            patient(lastname = "---", firstname = "Maria"),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("M-F21-S01", label)
    }

    @Test
    fun `null sex omits the entire sex-age segment`() {
        // Garcia -> GCA; Maria -> M; sex = null -> no -SEXAGE- segment
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Garcia", firstname = "Maria", sex = null),
            sequence = 1,
            asOf = asOf,
        )
        assertEquals("GCAM-S01", label)
    }

    // ---------- the sequence ----------

    @Test
    fun `a patient with no sessions starts at one`() {
        assertEquals(1, SessionLabelGenerator.nextSequence(emptyList()))
    }

    @Test
    fun `three smears yield the fourth`() {
        val existing = listOf(
            "GarciaM-S01",
            "GarciaM-S02",
            "GarciaM-S03",
        )
        assertEquals(4, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `the highest sequence wins regardless of order`() {
        val existing = listOf(
            "GarciaM-S03",
            "GarciaM-S01",
            "GarciaM-S02",
        )
        assertEquals(4, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `an edited label without the -S suffix is ignored rather than read as zero`() {
        // Reading "Morning batch" as 0 would hand the next smear S01, which may be taken.
        val existing = listOf("GarciaM-S07", "Morning batch", "")
        assertEquals(8, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `a bare trailing digit run without -S prefix is not parsed as a sequence`() {
        // An unanchored match on digits alone would read the last run here and inflate the counter.
        val existing = listOf("Garcia-42")
        assertEquals(1, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `sequences past two digits keep counting`() {
        assertEquals(100, SessionLabelGenerator.nextSequence(listOf("GarciaM-S99")))
    }

    @Test
    fun `sequences past three digits keep counting`() {
        // 999 → 1000; widening must not wrap or truncate.
        assertEquals(1_000, SessionLabelGenerator.nextSequence(listOf("GarciaM-S999")))
    }

    @Test
    fun `-S suffix match is case-insensitive`() {
        // A medtech who typed "garciam-s03" should still have their sequence parsed.
        val existing = listOf("garciam-s03")
        assertEquals(4, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `old-format labels with barangay code are not parsed as a sequence`() {
        // Pre-v17 labels look like "C.G.-0730600000-003". The new regex requires -S before
        // the digits, so these labels are treated as edited (non-matching) and ignored.
        val existing = listOf("C.G.-0730600000-003")
        assertEquals(1, SessionLabelGenerator.nextSequence(existing))
    }

    // ---------- codename ----------

    @Test
    fun `codenamed patient produces -SEXAGE-S01 label`() {
        val labelVision = SessionLabelGenerator.generate(
            patient(lastname = "VISION-M22-001", firstname = ""),
            sequence = 1,
        )
        assertEquals("-M22-S01", labelVision)

        val label = SessionLabelGenerator.generate(
            patient(lastname = "M24-001", firstname = ""),
            sequence = 1,
        )
        assertEquals("-M24-S01", label)

        val labelFemale = SessionLabelGenerator.generate(
            patient(lastname = "VISION-F05-002", firstname = ""),
            sequence = 3,
        )
        assertEquals("-F05-S03", labelFemale)
    }

    /**
     * Fixed reference instant for all named-patient tests: 2021-06-15 UTC, which resolves to
     * 2021-06-15 in CLINICAL_ZONE (Asia/Manila, UTC+8) and yields age 21 for the default
     * birthdate of 2000-01-01.
     */
    private val asOf: Instant = Instant.parse("2021-06-15T00:00:00Z")

    private fun patient(
        lastname: String = "Garcia",
        firstname: String = "Maria",
        sex: Sex? = Sex.FEMALE,
    ) = Patient(
        id = "patient-1",
        lastname = lastname,
        firstname = firstname,
        middleName = null,
        sex = sex,
        birthdate = LocalDate.of(2000, 1, 1),
        psgcBarangayCode = "0730600000",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
