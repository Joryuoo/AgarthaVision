package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientMapperTest {

    private val birthdateMillis =
        LocalDate.of(1998, 7, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

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
    fun `an unrecognised stored sex does not throw`() {
        assertEquals(Sex.MALE, entity(sex = "?").toDomain().sex)
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
}
