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
 */
class SessionLabelGeneratorTest {

    // ---------- the format ----------

    @Test
    fun `builds the documented label`() {
        assertEquals(
            "C.G.-0730600000-001",
            SessionLabelGenerator.generate(patient(), sequence = 1),
        )
    }

    @Test
    fun `an initial is uppercased`() {
        val label = SessionLabelGenerator.generate(
            patient(lastname = "cruz", firstname = "gerald"),
            sequence = 1,
        )

        assertEquals("C.G.-0730600000-001", label)
    }

    @Test
    fun `an accented initial survives`() {
        // Ñ is a real first letter of Philippine surnames (Ñuñez). Stripping the diacritic
        // would print a different person's initial, so it is kept as-is.
        val label = SessionLabelGenerator.generate(
            patient(lastname = "ñuñez", firstname = "Élia"),
            sequence = 2,
        )

        assertEquals("Ñ.É.-0730600000-002", label)
    }

    @Test
    fun `a name that starts with punctuation still yields a letter`() {
        val label = SessionLabelGenerator.generate(
            patient(lastname = "  'Dela Cruz", firstname = "Gerald"),
            sequence = 1,
        )

        assertEquals("D.G.-0730600000-001", label)
    }

    @Test
    fun `a sequence past three digits widens rather than wrapping`() {
        // Truncating to the last three digits would print 000 and collide with a label that
        // already exists. The field is 32 characters; it has the room.
        assertEquals(
            "C.G.-0730600000-1000",
            SessionLabelGenerator.generate(patient(), sequence = 1_000),
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
            "C.G.-0730600000-001",
            "C.G.-0730600000-002",
            "C.G.-0730600000-003",
        )

        assertEquals(4, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `the highest sequence wins regardless of order`() {
        val existing = listOf(
            "C.G.-0730600000-003",
            "C.G.-0730600000-001",
            "C.G.-0730600000-002",
        )

        assertEquals(4, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `an edited label is ignored rather than read as zero`() {
        // Reading "Morning batch" as 0 would hand the next smear 001, which is taken.
        val existing = listOf("C.G.-0730600000-007", "Morning batch", "")

        assertEquals(8, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `a label whose sequence was edited away does not read the barangay as one`() {
        // The PSGC code is also a run of digits. An unanchored match would return 730600000
        // here and mint a ten-digit sequence for the next smear.
        val existing = listOf("C.G.-0730600000")

        assertEquals(1, SessionLabelGenerator.nextSequence(existing))
    }

    @Test
    fun `sequences past three digits keep counting`() {
        assertEquals(1_000, SessionLabelGenerator.nextSequence(listOf("C.G.-0730600000-999")))
    }

    private fun patient(
        lastname: String = "Cruz",
        firstname: String = "Gerald",
        psgcBarangayCode: String = "0730600000",
    ) = Patient(
        id = "patient-1",
        lastname = lastname,
        firstname = firstname,
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(2015, 6, 1),
        psgcBarangayCode = psgcBarangayCode,
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
