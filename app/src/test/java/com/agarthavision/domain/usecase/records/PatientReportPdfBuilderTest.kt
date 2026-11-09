package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.ParasiteBurdenLevel
import com.agarthavision.domain.model.PatientReportScope
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportPdfSampleItem
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

    private fun sampleItemFixture(id: String, fieldIndex: Int) = ReportPdfSampleItem(
        sampleId = id,
        sessionLabel = "Session 1",
        fieldIndex = fieldIndex,
        imagePath = "/data/$id.jpg",
        eggCount = 1,
        findingsSummary = "Ascaris lumbricoides",
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
    fun `the pooled species table only lists recognized egg species, sorted with burden level`() {
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
                "Ascaris lumbricoides" to LpfDensity(min = 0, max = 8),
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

        val ascarisRow = doc.speciesRows.first { it.speciesDisplayName == "Ascaris lumbricoides" }
        assertEquals(ParasiteBurdenLevel.MODERATE, ascarisRow.burdenLevel)

        val trichurisRow = doc.speciesRows.first { it.speciesDisplayName == "Trichuris trichiura" }
        assertEquals(ParasiteBurdenLevel.LOW, trichurisRow.burdenLevel)
    }

    @Test
    fun `default config excludes sample images and sets totalPages to 1`() {
        val sampleItems = (1..5).map { sampleItemFixture("s$it", it) }
        val doc = builder.build(
            reportId = "rep-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 30,
            sex = Sex.MALE,
            address = "Cebu",
            scope = PatientReportScope(),
            generatedByName = "Medtech",
            generatedAt = Instant.now(),
            pooledLpfPerSpecies = emptyMap(),
            totalSamples = 5,
            totalEggsConfirmed = 5,
            positiveSpecies = emptyList(),
            sessionRows = emptyList(),
            config = ReportConfig(includeSampleImages = false),
            appendixSamples = sampleItems,
        )

        assertEquals(1, doc.header.totalPages)
        assertEquals(false, doc.header.includeSampleImages)
        assertTrue(doc.appendixSamples.isEmpty())
    }

    @Test
    fun `includeSampleImages true retains appendix samples and paginates at 4 per page`() {
        val baseBuild = { items: List<ReportPdfSampleItem> ->
            builder.build(
                reportId = "rep-1",
                patientDisplayName = "Cruz, Gerald",
                ageYears = 30,
                sex = Sex.MALE,
                address = "Cebu",
                scope = PatientReportScope(),
                generatedByName = "Medtech",
                generatedAt = Instant.now(),
                pooledLpfPerSpecies = emptyMap(),
                totalSamples = items.size,
                totalEggsConfirmed = items.size,
                positiveSpecies = emptyList(),
                sessionRows = emptyList(),
                config = ReportConfig(includeSampleImages = true),
                appendixSamples = items,
            )
        }

        // 0 samples -> 1 page
        val doc0 = baseBuild(emptyList())
        assertEquals(1, doc0.header.totalPages)
        assertTrue(doc0.appendixSamples.isEmpty())

        // 1 sample -> 2 pages
        val doc1 = baseBuild(listOf(sampleItemFixture("s1", 1)))
        assertEquals(2, doc1.header.totalPages)
        assertEquals(1, doc1.appendixSamples.size)

        // 4 samples -> 2 pages
        val doc4 = baseBuild((1..4).map { sampleItemFixture("s$it", it) })
        assertEquals(2, doc4.header.totalPages)
        assertEquals(4, doc4.appendixSamples.size)

        // 5 samples -> 3 pages
        val doc5 = baseBuild((1..5).map { sampleItemFixture("s$it", it) })
        assertEquals(3, doc5.header.totalPages)
        assertEquals(5, doc5.appendixSamples.size)

        // 8 samples -> 3 pages
        val doc8 = baseBuild((1..8).map { sampleItemFixture("s$it", it) })
        assertEquals(3, doc8.header.totalPages)

        // 9 samples -> 4 pages
        val doc9 = baseBuild((1..9).map { sampleItemFixture("s$it", it) })
        assertEquals(4, doc9.header.totalPages)
    }

    @Test
    fun `derives organization from email or respects explicit override`() {
        val docDerived = builder.build(
            reportId = "rep-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 30,
            sex = Sex.MALE,
            address = "Cebu",
            scope = PatientReportScope(),
            generatedByName = "Medtech",
            generatedAt = Instant.now(),
            pooledLpfPerSpecies = emptyMap(),
            totalSamples = 0,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            sessionRows = emptyList(),
            generatedByEmail = "jane@doh.gov.ph",
        )
        assertEquals("jane@doh.gov.ph", docDerived.header.generatedByEmail)
        assertEquals("doh.gov.ph", docDerived.header.organizationName)

        val docExplicit = builder.build(
            reportId = "rep-1",
            patientDisplayName = "Cruz, Gerald",
            ageYears = 30,
            sex = Sex.MALE,
            address = "Cebu",
            scope = PatientReportScope(),
            generatedByName = "Medtech",
            generatedAt = Instant.now(),
            pooledLpfPerSpecies = emptyMap(),
            totalSamples = 0,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            sessionRows = emptyList(),
            generatedByEmail = "jane@doh.gov.ph",
            organizationName = "Department of Health - Region VII",
        )
        assertEquals("Department of Health - Region VII", docExplicit.header.organizationName)
    }
}
