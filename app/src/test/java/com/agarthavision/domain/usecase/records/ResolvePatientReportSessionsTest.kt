package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.Session
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure-function tests for [resolvePatientReportSessions] (C2). */
class ResolvePatientReportSessionsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Manila")

    private fun session(id: String, date: LocalDate): Session = Session(
        id = id,
        userId = "user-1",
        deviceId = "device-1",
        startedAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        patientId = "patient-1",
        label = id,
    )

    @Test
    fun `an unrestricted scope returns every session`() {
        val sessions = listOf(session("s1", LocalDate.of(2026, 1, 1)), session("s2", LocalDate.of(2026, 1, 2)))

        val result = resolvePatientReportSessions(sessions, PatientReportScope(), zone)

        assertEquals(sessions, result)
    }

    @Test
    fun `a date range keeps only sessions started within the inclusive bounds`() {
        val sessions = listOf(
            session("s1", LocalDate.of(2026, 1, 1)),
            session("s2", LocalDate.of(2026, 1, 2)),
            session("s3", LocalDate.of(2026, 1, 3)),
        )

        val result = resolvePatientReportSessions(
            sessions,
            PatientReportScope(startDate = LocalDate.of(2026, 1, 2), endDate = LocalDate.of(2026, 1, 2)),
            zone,
        )

        assertEquals(listOf(sessions[1]), result)
    }

    @Test
    fun `an end date includes the entirety of that day`() {
        val session = session("s1", LocalDate.of(2026, 1, 2))
        val lateOnEndDate = session.copy(
            startedAt = LocalDate.of(2026, 1, 2).atTime(23, 59)
                .atZone(zone).toInstant().toEpochMilli(),
        )

        val result = resolvePatientReportSessions(
            listOf(lateOnEndDate),
            PatientReportScope(endDate = LocalDate.of(2026, 1, 2)),
            zone,
        )

        assertEquals(listOf(lateOnEndDate), result)
    }

    @Test
    fun `a session id subset keeps only the named sessions`() {
        val sessions = listOf(session("s1", LocalDate.of(2026, 1, 1)), session("s2", LocalDate.of(2026, 1, 2)))

        val result = resolvePatientReportSessions(sessions, PatientReportScope(sessionIds = setOf("s1")), zone)

        assertEquals(listOf(sessions[0]), result)
    }

    @Test
    fun `an id not belonging to the patient's sessions is a caller error`() {
        val sessions = listOf(session("s1", LocalDate.of(2026, 1, 1)))

        assertThrows(IllegalArgumentException::class.java) {
            resolvePatientReportSessions(sessions, PatientReportScope(sessionIds = setOf("s1", "foreign")), zone)
        }
    }

    @Test
    fun `a subset combined with a date range applies both`() {
        val sessions = listOf(
            session("s1", LocalDate.of(2026, 1, 1)),
            session("s2", LocalDate.of(2026, 1, 2)),
        )

        val result = resolvePatientReportSessions(
            sessions,
            PatientReportScope(sessionIds = setOf("s1", "s2"), startDate = LocalDate.of(2026, 1, 2)),
            zone,
        )

        assertEquals(listOf(sessions[1]), result)
    }
}
