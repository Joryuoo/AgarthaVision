package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SESSION_STARTED_AT = 10_000L
private const val SESSION_ENDED_AT = 20_000L
private const val SAMPLE_TIMESTAMP = 1_000L
private const val SAMPLE_VERIFIED_AT = 2_000L
private const val GENERATED_AT_MILLIS = 30_000L
private const val TOTAL_SAMPLES = 2
private const val TOTAL_EGGS_CONFIRMED = 3
private const val ASCARIS_EPG = 48
private const val TRICHURIS_EPG = 24
private const val SAMPLE_CONFIDENCE = 0.91f
private const val SAMPLE_BBOX_X = 0.1f
private const val SAMPLE_BBOX_Y = 0.2f
private const val SAMPLE_BBOX_W = 0.3f
private const val SAMPLE_BBOX_H = 0.4f

class ReportPdfBuilderTest {
    // Long by nature: a golden-style assertion on the full ReportPdfDocument data model
    // (fixture setup + expected header/rows). Splitting it would scatter one assertion's
    // context across several helpers without reducing real complexity.
    @Suppress("LongMethod")
    @Test
    fun `builds pdf document with header and sorted egg-species rows`() {
        val builder = ReportPdfBuilder()
        val session = Session(
            id = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = SESSION_STARTED_AT,
            endedAt = SESSION_ENDED_AT,
            notes = null,
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
                isRepeat = false,
                latitude = null,
                longitude = null,
                accuracyMeters = null,
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
                    verifiedByUser = true,
                ),
            ),
        )
        val generatedAt = Instant.ofEpochMilli(GENERATED_AT_MILLIS)
        val metadata = ReportMetadata(
            reportId = "report-1",
            session = session,
            generatedBy = "user-1",
            generatedAt = generatedAt,
            totalSamples = TOTAL_SAMPLES,
            totalEggsConfirmed = TOTAL_EGGS_CONFIRMED,
            positiveSpecies = listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            // Deliberately unsorted, plus a non-egg finding, to prove buildSpeciesRows both
            // sorts and filters down to recognized egg species only.
            epgPerSpecies = mapOf(
                "Trichuris trichiura" to TRICHURIS_EPG,
                "Ascaris lumbricoides" to ASCARIS_EPG,
                "Mucus" to 1,
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
                generatedBy = "user-1",
                generatedAt = generatedAt,
                deviceId = "device-1",
                totalSamples = TOTAL_SAMPLES,
                totalEggsConfirmed = TOTAL_EGGS_CONFIRMED,
                positiveSpecies = listOf("Ascaris lumbricoides", "Trichuris trichiura"),
            ),
            speciesRows = listOf(
                ReportPdfSpeciesRow(speciesDisplayName = "Ascaris lumbricoides", epg = ASCARIS_EPG),
                ReportPdfSpeciesRow(speciesDisplayName = "Trichuris trichiura", epg = TRICHURIS_EPG),
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
                endedAt = SESSION_ENDED_AT,
                notes = null,
                label = null,
            ),
            generatedBy = "user-1",
            generatedAt = Instant.ofEpochMilli(GENERATED_AT_MILLIS),
            totalSamples = 1,
            totalEggsConfirmed = 0,
            positiveSpecies = emptyList(),
            epgPerSpecies = mapOf(
                "Mucus" to 5,
                "Blood" to 2,
                "WBC" to 1,
            ),
        )

        val document = builder.build(metadata = metadata, samples = emptyList(), detectionsBySample = emptyMap())

        assertEquals(emptyList<ReportPdfSpeciesRow>(), document.speciesRows)
    }
}
