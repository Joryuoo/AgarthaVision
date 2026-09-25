package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PatientMapperTest {

    private val birthdateMillis =
        LocalDate.of(1998, 7, 30).atStartOfDay(CLINICAL_ZONE).toInstant().toEpochMilli()

    private fun entity(
        sex: String = "M",
        middleName: String? = "Mendoza",
        supabaseStatus: String = "synced",
    ) = PatientEntity(
        patientId = "p-1",
        lastname = "Cruz",
        firstname = "Gerald",
        middleName = middleName,
        sex = sex,
        birthdate = birthdateMillis,
        psgcBarangayCode = "0102801001",
        createdBy = "u-1",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_500_000,
        supabaseStatus = supabaseStatus,
    )

    @Test
    fun `entity maps to the domain model`() {
        val domain = entity().toDomain()

        assertEquals("p-1", domain.id)
        assertEquals("Cruz", domain.lastname)
        assertEquals("Gerald", domain.firstname)
        assertEquals("Mendoza", domain.middleName)
        assertEquals(Sex.MALE, domain.sex)
        assertEquals(LocalDate.of(1998, 7, 30), domain.birthdate)
        assertEquals("0102801001", domain.psgcBarangayCode)
        assertEquals("u-1", domain.createdBy)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), domain.createdAt)
        assertEquals(Instant.ofEpochMilli(1_700_000_500_000), domain.updatedAt)
    }

    @Test
    fun `birthdate round-trips without shifting a day`() {
        val domain = entity().toDomain()
        assertEquals(birthdateMillis, domain.toEntity().birthdate)
    }

    @Test
    fun `a full round trip preserves every domain field`() {
        val domain = entity().toDomain()
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `sex maps both ways`() {
        assertEquals(Sex.FEMALE, entity(sex = "F").toDomain().sex)
        assertEquals("F", entity(sex = "F").toDomain().toEntity().sex)
    }

    @Test
    fun `an unrecognised stored sex reads as null rather than a guess`() {
        assertNull(entity(sex = "?").toDomain().sex)
    }

    @Test
    fun `a patient with no readable sex refuses to be written back`() {
        val unreadable = entity(sex = "?").toDomain()
        assertThrows(IllegalArgumentException::class.java) { unreadable.toEntity() }
    }

    @Test
    fun `birthdate is stored at Philippine midnight, not UTC midnight`() {
        // Philippine midnight is 16:00 UTC the previous day. Storing at UTC midnight
        // instead would read back as 29 July for a patient born on the 30th.
        val domain = entity().toDomain()
        val utcMidnight = LocalDate.of(1998, 7, 30)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(birthdateMillis, domain.toEntity().birthdate)
        assertEquals(LocalDate.of(1998, 7, 30), domain.birthdate)
        assertEquals(utcMidnight - 8 * 60 * 60 * 1000, birthdateMillis)
    }

    @Test
    fun `a null middle name survives the round trip`() {
        assertEquals(null, entity(middleName = null).toDomain().toEntity().middleName)
    }

    @Test
    fun `toEntity defaults to pending because a local write is never already synced`() {
        val roundTripped = entity(supabaseStatus = "synced").toDomain().toEntity()
        assertEquals("pending", roundTripped.supabaseStatus)
    }

    // ── Anonymous / codenamed patient contract ────────────────────────────────
    // PatientFormViewModel.persist() writes firstname = "" for every codename path.
    // PatientRemoteDataSource.toRow() passes that value straight to Supabase unchanged,
    // so the only guard is the DB-side CHECK — which 0002_optional_patient_firstname.sql
    // drops.  These tests confirm the app-side mapping never rejects or mangles "".
    // (toRow() itself is private; these cover the entity ↔ domain layer that feeds it.)

    @Test
    fun `an anonymous patient with empty firstname survives entity-to-domain mapping`() {
        val anon = entity().copy(lastname = "WILDCAT-F22", firstname = "")
        val domain = anon.toDomain()
        assertEquals("WILDCAT-F22", domain.lastname)
        assertEquals("", domain.firstname)
    }

    @Test
    fun `an anonymous patient with empty firstname round-trips through entity and domain`() {
        val anon = entity().copy(lastname = "WILDCAT-F22", firstname = "")
        assertEquals("", anon.toDomain().toEntity().firstname)
    }
}
