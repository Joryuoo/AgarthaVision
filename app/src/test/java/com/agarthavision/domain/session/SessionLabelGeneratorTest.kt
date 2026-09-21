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
 * Format: `<lastname (trimmed)><firstInitial>-S<NN>` — e.g. `GarciaM-S01`.
 */
class SessionLabelGeneratorTest {

    // ---------- the format ----------

    @Test
    fun `builds the documented label`() {
        assertEquals(
            "GarciaM-S01",
            SessionLabelGenerator.generate(patient(lastname = "Garcia", firstname = "Maria"), sequence = 1),
        )
    }

    @Test
    fun `firstname initial is uppercased when stored lowercase`() {
        val label = SessionLabelGenerator.generate(
            patient(lastname = "cruz", firstname = "gerald"),
            sequence = 1,
        )
        // Lastname is preserved as-is (trimmed only); initial is uppercased.
        assertEquals("cruzG-S01", label)
    }

    @Test
    fun `an accented initial survives`() {
        // Ñ is a real first letter of Philippine surnames (Ñuñez). Stripping the diacritic
        // would print a different person's initial, so it is kept as-is.
        val label = SessionLabelGenerator.generate(
            patient(lastname = "ñuñez", firstname = "Élia"),
            sequence = 2,
        )
        assertEquals("ñuñezÉ-S02", label)
    }

    @Test
    fun `leading and trailing whitespace in lastname is stripped`() {
        val label = SessionLabelGenerator.generate(
            patient(lastname = "  Cruz  ", firstname = "Gerald"),
            sequence = 1,
        )
        assertEquals("CruzG-S01", label)
    }

    @Test
    fun `empty firstname yields no initial`() {
        // A placeholder char in a clinical label reads worse than a slightly shorter one.
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Garcia", firstname = ""),
            sequence = 1,
        )
        assertEquals("Garcia-S01", label)
    }

    @Test
    fun `firstname with no letters yields no initial`() {
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Garcia", firstname = "123"),
            sequence = 1,
        )
        assertEquals("Garcia-S01", label)
    }

    @Test
    fun `firstname starting with punctuation still yields a letter initial`() {
        // The initial() helper skips non-letter characters and takes the first letter.
        val label = SessionLabelGenerator.generate(
            patient(lastname = "Dela Cruz", firstname = "'Gerald"),
            sequence = 1,
        )
        assertEquals("Dela CruzG-S01", label)
    }

    @Test
    fun `a sequence past two digits widens rather than wrapping`() {
        // Truncating to the last two digits would print 00 and collide with a label that
        // already exists. The field is 32 characters; it has the room.
        assertEquals(
            "GarciaM-S100",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 100,
            ),
        )
    }

    @Test
    fun `sequence zero is coerced to one`() {
        // coerceAtLeast(1) prevents S00 which would be confusing and imply ordinal 0.
        assertEquals(
            "GarciaM-S01",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 0,
            ),
        )
    }

    @Test
    fun `single-digit sequences are zero-padded to two digits`() {
        assertEquals(
            "GarciaM-S09",
            SessionLabelGenerator.generate(
                patient(lastname = "Garcia", firstname = "Maria"),
                sequence = 9,
            ),
        )
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

    private fun patient(
        lastname: String = "Garcia",
        firstname: String = "Maria",
    ) = Patient(
        id = "patient-1",
        lastname = lastname,
        firstname = firstname,
        middleName = null,
        sex = Sex.FEMALE,
        birthdate = LocalDate.of(2000, 1, 1),
        psgcBarangayCode = "0730600000",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
