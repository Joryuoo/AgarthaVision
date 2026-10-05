package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [PatientReportPdfBuilder] (pure, no Android dependency). */
class PatientReportPdfBuilderTest {

    private val builder = PatientReportPdfBuilder()

    private fun sessionRow(id: String) = PatientReportSessionRow(
        sessionId = id,
        sessionLabel = "Session $id",
        startedAt = Instant.parse("2026-01-01T00:00:00Z"),
        sampleCount = 5,
        eggsConfirmed = 2,
        positiveSpecies = listOf("Ascaris lumbricoides"),
        lpfPerSpecies = mapOf("Ascaris lumbricoides" to LpfDensity(min = 0, max = 2)),
    )

    @Test
    fun `builds a header carrying patient identity, scope, and totals`() {
        val generatedAt = Instant.parse("2026-01-05T12:00:00Z")
        val doc = builder.build(
            reportId = "report-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 26,
            sex = Sex.MALE,
            address = "Lahug, City of Cebu",
            scope = PatientReportScope(startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 5)),
            generatedByName = "Dr. Reyes",
            generatedAt = generatedAt,
            pooledLpfPerSpecies = mapOf("Ascaris lumbricoides" to LpfDensity(min = 0, max = 2)),
            totalSamples = 10,
            totalEggsConfirmed = 3,
            positiveSpecies = listOf("Ascaris lumbricoides"),
            sessionRows = listOf(sessionRow("s1"), sessionRow("s2")),
        )

        val header = doc.header
        assertEquals("report-1", header.reportId)
        assertEquals("Cruz, Gerald", header.patientDisplayName)
        assertEquals(26, header.ageYears)
        assertEquals(Sex.MALE, header.sex)
        assertEquals("Lahug, City of Cebu", header.address)
        assertEquals(LocalDate.of(2026, 1, 1), header.scopeStart)
        assertEquals(LocalDate.of(2026, 1, 5), header.scopeEnd)
        assertEquals("Dr. Reyes", header.generatedBy)
        assertEquals(generatedAt, header.generatedAt)
        // totalSessions comes from sessionRows.size, not a caller-supplied count, so it can
        // never drift from the table actually printed.
        assertEquals(2, header.totalSessions)
        assertEquals(10, header.totalSamples)
        assertEquals(3, header.totalEggsConfirmed)
        assertEquals(listOf("Ascaris lumbricoides"), header.positiveSpecies)
        assertEquals(2, doc.sessionRows.size)
    }

    @Test
    fun `a null sex is carried through without failing`() {
        val doc = builder.build(
            reportId = "report-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 26,
            sex = null,
            address = "Lahug",
            scope = PatientReportScope(),
            generatedByName = "Dr. Reyes",
            generatedAt = Instant.now(),
            pooledLpfPerSpecies = emptyMap(),
            totalSamples = 0,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            sessionRows = emptyList(),
        )

        assertNull(doc.header.sex)
    }

    @Test
    fun `the pooled species table only lists recognized egg species, sorted`() {
        val doc = builder.build(
            reportId = "report-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 26,
            sex = Sex.FEMALE,
            address = "Lahug",
            scope = PatientReportScope(),
            generatedByName = "Dr. Reyes",
            generatedAt = Instant.now(),
            pooledLpfPerSpecies = mapOf(
                "Trichuris trichiura" to LpfDensity(min = 0, max = 1),
                "Ascaris lumbricoides" to LpfDensity(min = 0, max = 2),
                // Not an EggSpecies canonical class (e.g. mucus/blood-style finding) — must be
                // excluded from the species table.
                "Mucus" to LpfDensity(min = 0, max = 1),
            ),
            totalSamples = 10,
            totalEggsConfirmed = 3,
            positiveSpecies = listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            sessionRows = listOf(sessionRow("s1")),
        )

        assertEquals(
            listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            doc.speciesRows.map { it.speciesDisplayName },
        )
        assertTrue(doc.speciesRows.none { it.speciesDisplayName == "Mucus" })
    }
}
