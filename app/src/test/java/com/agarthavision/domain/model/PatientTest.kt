package com.agarthavision.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientTest {

    private fun patient(
        lastname: String = "Cruz",
        firstname: String = "Gerald",
        middleName: String? = "Mendoza",
        birthdate: LocalDate = LocalDate.of(2000, 3, 15),
    ) = Patient(
        id = "p-1",
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = Sex.MALE,
        birthdate = birthdate,
        psgcBarangayCode = "0102801001",
        createdBy = "u-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    /** Philippine midnight on [date] — the frame `ageYears` resolves against. */
    private fun at(date: LocalDate): Instant = date.atStartOfDay(CLINICAL_ZONE).toInstant()

    // ── displayName ───────────────────────────────────────────────────────────

    @Test
    fun `displayName is surname first with a middle initial`() {
        assertEquals("Cruz, Gerald M.", patient().displayName)
    }

    @Test
    fun `displayName omits the initial and its period when there is no middle name`() {
        assertEquals("Cruz, Gerald", patient(middleName = null).displayName)
    }

    @Test
    fun `a blank middle name is treated as absent rather than trailing a stray period`() {
        assertEquals("Cruz, Gerald", patient(middleName = "   ").displayName)
    }

    // ── ageYears ──────────────────────────────────────────────────────────────

    @Test
    fun `age is one less on the day before a birthday`() {
        val p = patient(birthdate = LocalDate.of(2000, 3, 15))
        assertEquals(25, p.ageYears(at(LocalDate.of(2026, 3, 14))))
    }

    @Test
    fun `age ticks over on the birthday itself`() {
        val p = patient(birthdate = LocalDate.of(2000, 3, 15))
        assertEquals(26, p.ageYears(at(LocalDate.of(2026, 3, 15))))
    }

    @Test
    fun `age holds the day after a birthday`() {
        val p = patient(birthdate = LocalDate.of(2000, 3, 15))
        assertEquals(26, p.ageYears(at(LocalDate.of(2026, 3, 16))))
    }

    @Test
    fun `a 29 February birthdate has not had its birthday on 28 February of a common year`() {
        val p = patient(birthdate = LocalDate.of(2000, 2, 29))
        assertEquals(25, p.ageYears(at(LocalDate.of(2026, 2, 28))))
    }

    @Test
    fun `a 29 February birthdate ticks over on 1 March of a common year`() {
        val p = patient(birthdate = LocalDate.of(2000, 2, 29))
        assertEquals(26, p.ageYears(at(LocalDate.of(2026, 3, 1))))
    }

    @Test
    fun `a 29 February birthdate ticks over on the day itself in a leap year`() {
        val p = patient(birthdate = LocalDate.of(2000, 2, 29))
        assertEquals(24, p.ageYears(at(LocalDate.of(2024, 2, 29))))
    }

    @Test
    fun `a newborn is zero`() {
        val p = patient(birthdate = LocalDate.of(2026, 9, 17))
        assertEquals(0, p.ageYears(at(LocalDate.of(2026, 9, 17))))
    }

    @Test
    fun `age is resolved in Philippine time, not UTC`() {
        // 2026-03-15T00:30+08:00 is still 2026-03-14 in UTC. The patient's birthday is
        // 03-15, so a UTC-resolved age would read one year low for the first eight hours
        // of every Philippine day.
        val p = patient(birthdate = LocalDate.of(2000, 3, 15))
        val justAfterPhilippineMidnight = LocalDate.of(2026, 3, 15)
            .atStartOfDay(CLINICAL_ZONE)
            .plusMinutes(30)
            .toInstant()
        assertEquals(26, p.ageYears(justAfterPhilippineMidnight))
    }

    @Test
    fun `a future birthdate yields zero rather than a negative age`() {
        val p = patient(birthdate = LocalDate.of(2027, 1, 1))
        assertEquals(0, p.ageYears(at(LocalDate.of(2026, 9, 17))))
    }
}
