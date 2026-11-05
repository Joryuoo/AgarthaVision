package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.LpfDescriptor
import com.agarthavision.domain.model.ParasiteBurdenLevel
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPatient
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.Sex
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SESSION_STARTED_AT = 10_000L
private const val SESSION_PATIENT_ID = "patient-1"
private const val SAMPLE_TIMESTAMP = 1_000L
private const val SAMPLE_VERIFIED_AT = 2_000L
private const val GENERATED_AT_MILLIS = 30_000L
private const val TOTAL_SAMPLES = 2
private const val TOTAL_EGGS_CONFIRMED = 3
private const val PATIENT_AGE_YEARS = 34
private const val SAMPLE_CONFIDENCE = 0.91f
private const val SAMPLE_BBOX_X = 0.1f
private const val SAMPLE_BBOX_Y = 0.2f
private const val SAMPLE_BBOX_W = 0.3f
private const val SAMPLE_BBOX_H = 0.4f

private fun reportPatient() = ReportPatient(
    name = "Cruz, Gerald M.",
    sex = Sex.MALE,
    ageYears = PATIENT_AGE_YEARS,
    barangayLabel = "Lahug · City of Cebu · Region VII (Central Visayas)",
)

private fun sampleFixture(
    id: String,
    filePath: String = "/tmp/$id.jpg",
    sessionId: String = "session-1",
) = Sample(
    id = id,
    userId = "user-1",
    timestamp = SAMPLE_TIMESTAMP,
    verifiedAt = SAMPLE_VERIFIED_AT,
    deviceId = "device-1",
    sessionId = sessionId,
    filePath = filePath,
    storagePath = "user-1/$id.jpg",
    inferenceModelVersion = "model-1",
    isManual = false,
    status = SampleStatus.SYNCED,
)

class ReportPdfBuilderTest {

    @Suppress("LongMethod")
    @Test
    fun `builds pdf document with header and sorted egg-species rows`() {
        val builder = ReportPdfBuilder()
        val session = Session(
            id = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = SESSION_STARTED_AT,
            patientId = SESSION_PATIENT_ID,
            label = "Smear A",
        )
        val samples = listOf(
            Sample(
                id = "sample-1",
                userId = "user-1",
                timestamp = SAMPLE_TIMESTAMP,
                verifiedAt = SAMPLE_VERIFIED_AT,
                deviceId = "device-1",
                sessionId = "session-1",
                filePath = "/tmp/sample-1.jpg",
                storagePath = "user-1/sample-1.jpg",
                inferenceModelVersion = "model-1",
                isManual = false,
                status = SampleStatus.SYNCED,
            ),
        )
        val detectionsBySample = mapOf(
            "sample-1" to listOf(
                Detection(
                    id = "det-1",
                    sampleId = "sample-1",
                    classLabel = "Ascaris lumbricoides",
                    confidence = SAMPLE_CONFIDENCE,
                    bboxX = SAMPLE_BBOX_X,
                    bboxY = SAMPLE_BBOX_Y,
                    bboxW = SAMPLE_BBOX_W,
                    bboxH = SAMPLE_BBOX_H,
                    verdict = DetectionVerdict.CONFIRMED,
                    expertClass = "Ascaris lumbricoides",
                ),
            ),
        )
        val generatedAt = Instant.ofEpochMilli(GENERATED_AT_MILLIS)
        val patient = reportPatient()
        val metadata = ReportMetadata(
            reportId = "report-1",
            session = session,
            patient = patient,
            generatedBy = "user-1",
            generatedByName = "Dr. Medtech",
            generatedAt = generatedAt,
            totalSamples = TOTAL_SAMPLES,
            totalEggsConfirmed = TOTAL_EGGS_CONFIRMED,
            positiveSpecies = listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            // Deliberately unsorted, plus a non-egg finding, to prove buildSpeciesRows both
            // sorts and filters down to recognized egg species only.
            lpfPerSpecies = mapOf(
                "Trichuris trichiura" to LpfDensity(min = 0, max = 1),
                "Ascaris lumbricoides" to LpfDensity(min = 0, max = 2),
                "Mucus" to LpfDensity(min = 1, max = 1),
            ),
        )

        val document = builder.build(
            metadata = metadata,
            samples = samples,
            detectionsBySample = detectionsBySample,
        )

        val expected = ReportPdfDocument(
            header = ReportPdfHeader(
                reportId = "report-1",
                sessionId = "session-1",
                sessionLabel = "Smear A",
                patientName = patient.name,
                patientSex = patient.sex,
                patientAgeYears = patient.ageYears,
                barangayLabel = patient.barangayLabel,
                fieldsExamined = TOTAL_SAMPLES,
                generatedByName = "Dr. Medtech",
                generatedAt = generatedAt,
                totalEggsConfirmed = TOTAL_EGGS_CONFIRMED,
                positiveSpecies = listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            ),
            speciesRows = listOf(
                ReportPdfSpeciesRow(
                    speciesDisplayName = "Ascaris lumbricoides",
                    min = 0,
                    max = 2,
                    descriptor = LpfDescriptor.RARE,
                    burdenLevel = ParasiteBurdenLevel.LOW,
                ),
                ReportPdfSpeciesRow(
                    speciesDisplayName = "Trichuris trichiura",
                    min = 0,
                    max = 1,
                    descriptor = LpfDescriptor.RARE,
                    burdenLevel = ParasiteBurdenLevel.LOW,
                ),
            ),
        )
        assertEquals(expected, document)
    }

    @Test
    fun `omits non-egg findings such as mucus, blood, and wbc from species rows`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-2",
            session = Session(
                id = "session-2",
                userId = "user-1",
                deviceId = "device-1",
                startedAt = SESSION_STARTED_AT,
                patientId = SESSION_PATIENT_ID,
                label = null,
            ),
            patient = reportPatient(),
            generatedBy = "user-1",
            generatedByName = "Dr. Medtech",
            generatedAt = Instant.ofEpochMilli(GENERATED_AT_MILLIS),
            totalSamples = 1,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = mapOf(
                "Mucus" to LpfDensity(5, 5),
                "Blood" to LpfDensity(2, 2),
                "WBC" to LpfDensity(1, 1),
            ),
        )

        val document = builder.build(metadata = metadata, samples = emptyList(), detectionsBySample = emptyMap())

        assertEquals(emptyList<ReportPdfSpeciesRow>(), document.speciesRows)
    }

    @Test
    fun `wholly-negative session still produces a header with zero fields examined`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-3",
            session = Session(
                id = "session-3",
                userId = "user-1",
                deviceId = "device-1",
                startedAt = SESSION_STARTED_AT,
                patientId = SESSION_PATIENT_ID,
                label = "Smear B",
            ),
            patient = reportPatient(),
            generatedBy = "user-1",
            generatedByName = "Dr. Medtech",
            generatedAt = Instant.ofEpochMilli(GENERATED_AT_MILLIS),
            totalSamples = 1,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = emptyMap(),
        )

        val document = builder.build(metadata = metadata, samples = emptyList(), detectionsBySample = emptyMap())

        assertEquals(emptyList<ReportPdfSpeciesRow>(), document.speciesRows)
        assertEquals(1, document.header.fieldsExamined)
        assertEquals(0, document.header.totalEggsConfirmed)
        assertEquals(emptyList<String>(), document.header.positiveSpecies)
    }

    @Test
    fun `build with includeSampleImages false defaults to single page and empty appendix`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-4",
            session = Session(
                id = "session-4",
                userId = "u1",
                deviceId = "d1",
                startedAt = 0L,
                patientId = "p1",
                label = "Smear 4",
            ),
            patient = reportPatient(),
            generatedBy = "u1",
            generatedByName = "Tech",
            generatedAt = Instant.now(),
            totalSamples = 5,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = emptyMap(),
        )
        val samples = (1..5).map { sampleFixture("sample-$it") }

        val document = builder.build(
            metadata = metadata,
            samples = samples,
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = false),
        )

        assertEquals(1, document.header.totalPages)
        assertEquals(false, document.header.includeSampleImages)
        assertTrue(document.appendixSamples.isEmpty())
    }

    @Test
    fun `build with includeSampleImages true generates appendix and paginates at 4 samples per page`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-5",
            session = Session(
                id = "session-5",
                userId = "u1",
                deviceId = "d1",
                startedAt = 0L,
                patientId = "p1",
                label = "Smear 5",
            ),
            patient = reportPatient(),
            generatedBy = "u1",
            generatedByName = "Tech",
            generatedAt = Instant.now(),
            totalSamples = 0,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = emptyMap(),
        )

        // 0 samples with includeSampleImages = true -> 1 + (0 + 3) / 4 = 1 page
        val doc0 = builder.build(
            metadata = metadata,
            samples = emptyList(),
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(1, doc0.header.totalPages)
        assertEquals(0, doc0.appendixSamples.size)

        // 1 sample -> 1 + (1 + 3) / 4 = 2 pages
        val doc1 = builder.build(
            metadata = metadata,
            samples = listOf(sampleFixture("s1")),
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(2, doc1.header.totalPages)
        assertEquals(1, doc1.appendixSamples.size)
        assertEquals(1, doc1.appendixSamples[0].fieldIndex)
        assertEquals("/tmp/s1.jpg", doc1.appendixSamples[0].imagePath)

        // 4 samples -> 1 + (4 + 3) / 4 = 2 pages
        val samples4 = (1..4).map { sampleFixture("s$it") }
        val doc4 = builder.build(
            metadata = metadata,
            samples = samples4,
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(2, doc4.header.totalPages)
        assertEquals(4, doc4.appendixSamples.size)

        // 5 samples -> 1 + (5 + 3) / 4 = 3 pages
        val samples5 = (1..5).map { sampleFixture("s$it") }
        val doc5 = builder.build(
            metadata = metadata,
            samples = samples5,
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(3, doc5.header.totalPages)
        assertEquals(5, doc5.appendixSamples.size)

        // 8 samples -> 1 + (8 + 3) / 4 = 3 pages
        val samples8 = (1..8).map { sampleFixture("s$it") }
        val doc8 = builder.build(
            metadata = metadata,
            samples = samples8,
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(3, doc8.header.totalPages)

        // 9 samples -> 1 + (9 + 3) / 4 = 4 pages
        val samples9 = (1..9).map { sampleFixture("s$it") }
        val doc9 = builder.build(
            metadata = metadata,
            samples = samples9,
            detectionsBySample = emptyMap(),
            config = ReportConfig(includeSampleImages = true),
        )
        assertEquals(4, doc9.header.totalPages)
    }

    @Test
    fun `build derives organization from email or respects explicit organization override`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-6",
            session = Session(
                id = "session-6",
                userId = "u1",
                deviceId = "d1",
                startedAt = 0L,
                patientId = "p1",
                label = null,
            ),
            patient = reportPatient(),
            generatedBy = "u1",
            generatedByName = "Tech",
            generatedAt = Instant.now(),
            totalSamples = 0,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            lpfPerSpecies = emptyMap(),
        )

        // Null email -> null organization
        val docNull = builder.build(metadata = metadata, samples = emptyList(), detectionsBySample = emptyMap())
        assertNull(docNull.header.generatedByEmail)
        assertNull(docNull.header.organizationName)

        // Email provided -> derived organization
        val docDerived = builder.build(
            metadata = metadata,
            samples = emptyList(),
            detectionsBySample = emptyMap(),
            generatedByEmail = "medtech@cebu-city-health.gov.ph",
        )
        assertEquals("medtech@cebu-city-health.gov.ph", docDerived.header.generatedByEmail)
        assertEquals("cebu-city-health.gov.ph", docDerived.header.organizationName)

        // Explicit override overrides derived organization
        val docExplicit = builder.build(
            metadata = metadata,
            samples = emptyList(),
            detectionsBySample = emptyMap(),
            generatedByEmail = "medtech@cebu-city-health.gov.ph",
            organizationName = "City Health Office Surveillance",
        )
        assertEquals("City Health Office Surveillance", docExplicit.header.organizationName)
    }

    @Test
    fun `populates correct parasite burden levels across descriptors`() {
        val builder = ReportPdfBuilder()
        val metadata = ReportMetadata(
            reportId = "report-7",
            session = Session(
                id = "session-7",
                userId = "u1",
                deviceId = "d1",
                startedAt = 0L,
                patientId = "p1",
                label = null,
            ),
            patient = reportPatient(),
            generatedBy = "u1",
            generatedByName = "Tech",
            generatedAt = Instant.now(),
            totalSamples = 4,
            totalEggsConfirmed = 20,
            positiveSpecies = listOf(
                "Ascaris lumbricoides",
                "Trichuris trichiura",
                "Hookworm",
            ),
            lpfPerSpecies = mapOf(
                "Ascaris lumbricoides" to LpfDensity(min = 0, max = 2), // RARE -> LOW
                "Trichuris trichiura" to LpfDensity(min = 1, max = 8), // MODERATE -> MODERATE
                "Hookworm" to LpfDensity(min = 2, max = 15), // NUMEROUS -> HIGH
            ),
        )

        val doc = builder.build(metadata = metadata, samples = emptyList(), detectionsBySample = emptyMap())

        val rowsBySpecies = doc.speciesRows.associateBy { it.speciesDisplayName }
        assertEquals(ParasiteBurdenLevel.LOW, rowsBySpecies["Ascaris lumbricoides"]?.burdenLevel)
        assertEquals(ParasiteBurdenLevel.MODERATE, rowsBySpecies["Trichuris trichiura"]?.burdenLevel)
        assertEquals(ParasiteBurdenLevel.HIGH, rowsBySpecies["Hookworm"]?.burdenLevel)

        // Also verify FEW maps to LOW
        val docFew = builder.build(
            metadata = metadata.copy(
                lpfPerSpecies = mapOf("Ascaris lumbricoides" to LpfDensity(min = 0, max = 4)), // FEW -> LOW
            ),
            samples = emptyList(),
            detectionsBySample = emptyMap(),
        )
        assertEquals(ParasiteBurdenLevel.LOW, docFew.speciesRows.first().burdenLevel)
    }
}
