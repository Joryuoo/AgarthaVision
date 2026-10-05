package com.agarthavision.data.local.mapper

import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.google.gson.Gson
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Report.toEntity] / [ReportEntity.toDomain] round-trip, covering the patient fields added by
 * 14zcqntj2uz (`patient_id`, `session_ids_json`).
 */
class ReportMapperTest {

    private val gson = Gson()

    @Test
    fun `a patient report round-trips patientId and sessionIds through session_ids_json`() {
        val report = Report(
            id = "report-1",
            sessionId = null,
            patientId = "patient-1",
            sessionIds = listOf("session-1", "session-2"),
            userId = "user-1",
            reportType = ReportType.PATIENT,
            generatedAt = Instant.ofEpochMilli(1_000L),
            totalSamples = 4,
            totalEggsConfirmed = 2,
            positiveSpecies = listOf("Ascaris lumbricoides"),
            lpfPerSpecies = mapOf("Ascaris lumbricoides" to LpfDensity(min = 0, max = 2)),
            csvFilePath = null,
            pdfFilePath = "/documents/patient-report.pdf",
            supabaseStatus = ReportSyncStatus.PENDING,
        )

        val entity = report.toEntity(gson)

        assertNull(entity.sessionId)
        assertEquals("patient-1", entity.patientId)
        assertEquals("[\"session-1\",\"session-2\"]", entity.sessionIdsJson)

        val roundTripped = entity.toDomain(gson)

        assertNull(roundTripped.sessionId)
        assertEquals("patient-1", roundTripped.patientId)
        assertEquals(listOf("session-1", "session-2"), roundTripped.sessionIds)
        assertEquals(ReportType.PATIENT, roundTripped.reportType)
        assertEquals(report.totalSamples, roundTripped.totalSamples)
        assertEquals(report.lpfPerSpecies, roundTripped.lpfPerSpecies)
    }

    @Test
    fun `a session report has a null patientId and an empty sessionIds, not a stray json blob`() {
        val report = Report(
            id = "report-1",
            sessionId = "session-1",
            userId = "user-1",
            reportType = ReportType.SESSION,
            generatedAt = Instant.ofEpochMilli(1_000L),
            totalSamples = 1,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = emptyMap(),
            csvFilePath = null,
            pdfFilePath = "/documents/report.pdf",
            supabaseStatus = ReportSyncStatus.SYNCED,
        )

        val entity = report.toEntity(gson)

        assertEquals("session-1", entity.sessionId)
        assertNull(entity.patientId)
        assertNull(entity.sessionIdsJson)

        val roundTripped = entity.toDomain(gson)

        assertNull(roundTripped.patientId)
        assertEquals(emptyList<String>(), roundTripped.sessionIds)
    }
}
