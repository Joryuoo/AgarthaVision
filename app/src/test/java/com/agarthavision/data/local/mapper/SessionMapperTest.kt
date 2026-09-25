package com.agarthavision.data.local.mapper

import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SessionLinkState
import com.agarthavision.domain.model.SessionSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the one thing PB-09 exists for: the patient survives the trip from the Room row to
 * the domain model.
 *
 * It is worth a test rather than being obvious from the code because `patientId` is the only
 * field here that a caller cannot recover from anywhere else. A session whose patient is lost
 * in the mapper still renders, still syncs and still generates a report — it just belongs to
 * nobody, and the screen that lists it by patient would quietly stop showing it.
 */
class SessionMapperTest {

    @Test
    fun `patient id survives the mapping`() {
        val domain = entity(patientId = "patient-42").toDomain()

        assertEquals("patient-42", domain.patientId)
    }

    @Test
    fun `every entity field lands on its domain counterpart`() {
        val domain = entity(patientId = "patient-1").toDomain()

        assertEquals("session-1", domain.id)
        assertEquals("user-1", domain.userId)
        assertEquals("device-1", domain.deviceId)
        assertEquals(1_000L, domain.startedAt)
        assertEquals("Smear A", domain.label)
        assertEquals(SessionSyncStatus.SYNCED, domain.supabaseStatus)
    }

    @Test
    fun `a pending row maps to the pending link state`() {
        val domain = entity(patientId = "patient-1", status = SessionSyncStatus.PENDING.value).toDomain()

        assertEquals(SessionSyncStatus.PENDING, domain.supabaseStatus)
        assertEquals(SessionLinkState.PENDING, domain.linkState)
    }

    @Test
    fun `a pulled row with no local owner still maps`() {
        // `userId` is nullable on the entity for rows that arrive from Supabase before the
        // profile does. Nothing else about the session is conditional on it.
        val domain = entity(patientId = "patient-1", userId = null).toDomain()

        assertEquals(null, domain.userId)
        assertEquals("patient-1", domain.patientId)
    }

    private fun entity(
        patientId: String,
        userId: String? = "user-1",
        status: String = SessionSyncStatus.SYNCED.value,
    ) = SessionEntity(
        sessionId = "session-1",
        userId = userId,
        patientId = patientId,
        deviceId = "device-1",
        startedAt = 1_000L,
        label = "Smear A",
        supabaseStatus = status,
    )
}
