package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportOrganizationUtilsTest {

    // --- deriveOrganizationFromEmail tests ---

    @Test
    fun `deriveOrganizationFromEmail returns domain for standard email`() {
        assertEquals("hospital.org", deriveOrganizationFromEmail("medtech@hospital.org"))
        assertEquals("lab.research.gov.ph", deriveOrganizationFromEmail("analyst@lab.research.gov.ph"))
    }

    @Test
    fun `deriveOrganizationFromEmail trims whitespace around email and domain`() {
        assertEquals("clinic.ph", deriveOrganizationFromEmail(" medtech@clinic.ph "))
        assertEquals("sub.domain.com", deriveOrganizationFromEmail("user@ sub.domain.com "))
    }

    @Test
    fun `deriveOrganizationFromEmail returns null for null, empty or blank email`() {
        assertNull(deriveOrganizationFromEmail(null))
        assertNull(deriveOrganizationFromEmail(""))
        assertNull(deriveOrganizationFromEmail("   "))
    }

    @Test
    fun `deriveOrganizationFromEmail returns null when missing @ sign`() {
        assertNull(deriveOrganizationFromEmail("invalid-email-string"))
        assertNull(deriveOrganizationFromEmail("medtech.clinic.ph"))
    }

    @Test
    fun `deriveOrganizationFromEmail returns null for empty or whitespace domain after @`() {
        assertNull(deriveOrganizationFromEmail("medtech@"))
        assertNull(deriveOrganizationFromEmail("medtech@   "))
    }

    @Test
    fun `deriveOrganizationFromEmail returns null for multiple @ signs`() {
        assertNull(deriveOrganizationFromEmail("first@second@domain.com"))
        assertNull(deriveOrganizationFromEmail("user@@domain.com"))
    }

    // --- formatPatientNameForReport tests ---

    private fun createPatient(
        lastname: String = "Cruz",
        firstname: String = "Gerald",
        middleName: String? = "Mercado",
    ) = Patient(
        id = "patient-1",
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1995, 5, 10),
        psgcBarangayCode = "072217001",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `formatPatientNameForReport with SHOW returns full displayName`() {
        val patient = createPatient(lastname = "Cruz", firstname = "Gerald", middleName = "Mercado")
        val formatted = formatPatientNameForReport(patient, PatientNameOption.SHOW)
        assertEquals("Cruz, Gerald M.", formatted)
    }

    @Test
    fun `formatPatientNameForReport with CENSOR masks patient name`() {
        val patient = createPatient(lastname = "Cruz", firstname = "Gerald", middleName = "Mercado")
        val formatted = formatPatientNameForReport(patient, PatientNameOption.CENSOR)
        assertEquals(patient.maskedDisplayName, formatted)
        assertEquals("C••Z, G••D M.", formatted)
    }

    @Test
    fun `formatPatientNameForReport with CENSOR preserves codename unmasked`() {
        val codenamedPatient = createPatient(lastname = "ALPHA-M24", firstname = "", middleName = null)
        val formatted = formatPatientNameForReport(codenamedPatient, PatientNameOption.CENSOR)
        assertEquals("ALPHA-M24", formatted)
    }

    @Test
    fun `formatPatientNameForReport with EXCLUDE returns default or custom excluded label`() {
        val patient = createPatient()
        assertEquals("Excluded", formatPatientNameForReport(patient, PatientNameOption.EXCLUDE))
        assertEquals(
            "Anonymized",
            formatPatientNameForReport(patient, PatientNameOption.EXCLUDE, excludedLabel = "Anonymized"),
        )
    }

    // --- buildAppendixSamples tests ---

    private fun createSample(
        id: String,
        sessionId: String = "session-1",
        filePath: String = "/data/samples/$id.jpg",
    ) = Sample(
        id = id,
        userId = "user-1",
        timestamp = 1000L,
        verifiedAt = 2000L,
        deviceId = "dev-1",
        sessionId = sessionId,
        filePath = filePath,
        storagePath = "remote/$id.jpg",
        inferenceModelVersion = "v1",
        isManual = false,
        status = SampleStatus.SYNCED,
    )

    private fun createDetection(
        id: String,
        sampleId: String,
        classLabel: String,
        verdict: DetectionVerdict = DetectionVerdict.CONFIRMED,
        expertClass: String? = null,
    ) = Detection(
        id = id,
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = 0.95f,
        bboxX = 0.1f,
        bboxY = 0.1f,
        bboxW = 0.2f,
        bboxH = 0.2f,
        verdict = verdict,
        expertClass = expertClass,
    )

    @Test
    fun `buildAppendixSamples assigns 1-indexed fieldIndex`() {
        val samples = listOf(createSample("s1"), createSample("s2"), createSample("s3"))
        val appendix = buildAppendixSamples(samples = samples, detectionsBySample = emptyMap())

        assertEquals(3, appendix.size)
        assertEquals(1, appendix[0].fieldIndex)
        assertEquals(2, appendix[1].fieldIndex)
        assertEquals(3, appendix[2].fieldIndex)
    }

    @Test
    fun `buildAppendixSamples filters out unconfirmed and non-confirmed detections`() {
        val sample = createSample("s1")
        val detections = listOf(
            createDetection("d1", "s1", "Ascaris lumbricoides", verdict = DetectionVerdict.CONFIRMED),
            createDetection("d2", "s1", "Trichuris trichiura", verdict = DetectionVerdict.FALSE_POSITIVE),
            createDetection("d3", "s1", "Hookworm", verdict = DetectionVerdict.WRONG_CLASS),
            createDetection("d4", "s1", "Ascaris lumbricoides", verdict = DetectionVerdict.BOX_INCORRECT),
        )
        val appendix = buildAppendixSamples(
            samples = listOf(sample),
            detectionsBySample = mapOf("s1" to detections),
        )

        assertEquals(1, appendix[0].eggCount)
        assertEquals("Ascaris lumbricoides", appendix[0].findingsSummary)
    }

    @Test
    fun `buildAppendixSamples produces canonical species summary when eggs are confirmed`() {
        val sample = createSample("s1")
        val detections = listOf(
            createDetection("d1", "s1", "Ascaris lumbricoides", verdict = DetectionVerdict.CONFIRMED),
            createDetection("d2", "s1", "ascaris", verdict = DetectionVerdict.CONFIRMED), // mapping to canonical
            createDetection(
                "d3",
                "s1",
                "unknown",
                verdict = DetectionVerdict.CONFIRMED,
                expertClass = "Trichuris trichiura",
            ),
        )
        val appendix = buildAppendixSamples(
            samples = listOf(sample),
            detectionsBySample = mapOf("s1" to detections),
        )

        assertEquals(3, appendix[0].eggCount)
        assertEquals("Ascaris lumbricoides, Trichuris trichiura", appendix[0].findingsSummary)
    }

    @Test
    fun `buildAppendixSamples uses fallback summary when 0 confirmed eggs`() {
        val sample = createSample("s1")
        val appendixDefault = buildAppendixSamples(
            samples = listOf(sample),
            detectionsBySample = emptyMap(),
        )
        assertEquals("No parasites detected", appendixDefault[0].findingsSummary)

        val appendixCustom = buildAppendixSamples(
            samples = listOf(sample),
            detectionsBySample = emptyMap(),
            noParasitesSummary = "Clean smear",
        )
        assertEquals("Clean smear", appendixCustom[0].findingsSummary)
    }

    @Test
    fun `buildAppendixSamples sets imagePath to null when filePath is blank`() {
        val sampleBlank = createSample("s1", filePath = "   ")
        val sampleEmpty = createSample("s2", filePath = "")
        val sampleValid = createSample("s3", filePath = "/path/to/img.jpg")

        val appendix = buildAppendixSamples(
            samples = listOf(sampleBlank, sampleEmpty, sampleValid),
            detectionsBySample = emptyMap(),
        )

        assertNull(appendix[0].imagePath)
        assertNull(appendix[1].imagePath)
        assertEquals("/path/to/img.jpg", appendix[2].imagePath)
    }

    @Test
    fun `buildAppendixSamples resolves sessionLabel with priority map over default over sessionId`() {
        val s1 = createSample("sample-1", sessionId = "sess-1")
        val s2 = createSample("sample-2", sessionId = "sess-2")
        val s3 = createSample("sample-3", sessionId = "sess-3")

        val appendix = buildAppendixSamples(
            samples = listOf(s1, s2, s3),
            detectionsBySample = emptyMap(),
            sessionLabelById = mapOf("sess-1" to "Mapped Smear 1"),
            defaultSessionLabel = "Default Smear",
        )

        assertEquals("Mapped Smear 1", appendix[0].sessionLabel)
        assertEquals("Default Smear", appendix[1].sessionLabel) // not in map, uses default
        // Now test fallback to sessionId when defaultSessionLabel is null
        val appendixNoDefault = buildAppendixSamples(
            samples = listOf(s3),
            detectionsBySample = emptyMap(),
            sessionLabelById = emptyMap(),
            defaultSessionLabel = null,
        )
        assertEquals("sess-3", appendixNoDefault[0].sessionLabel)
    }
}
