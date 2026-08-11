package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.ReportMetadata
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.Session
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCsvBuilderTest {
    // Long by nature: a data-table-heavy CSV golden test (two full sample fixtures + the
    // full expected header/row text block). Splitting it would scatter one assertion's
    // context across several helpers without reducing real complexity.
    @Suppress("LongMethod")
    @Test
    fun `builds session report csv with header and rows`() {
        val builder = ReportCsvBuilder()
        val session = Session(
            id = "session-1",
            userId = "user-1",
            deviceId = "device-1",
            startedAt = 10_000L,
            endedAt = 20_000L,
            notes = null,
            label = "Smear A",
        )
        val samples = listOf(
            sample(
                SampleTestData(
                    id = "sample-1",
                    timestamp = 1_000L,
                    verifiedAt = 2_000L,
                    inferenceModelVersion = "model-1",
                    userNote = "note,one",
                    isManual = false,
                    isRepeat = false,
                    latitude = 10.0,
                    longitude = 20.0,
                    accuracyMeters = 5f,
                ),
            ),
            sample(
                SampleTestData(
                    id = "sample-2",
                    timestamp = 3_000L,
                    verifiedAt = 4_000L,
                    inferenceModelVersion = "model-2",
                    userNote = null,
                    isManual = true,
                    isRepeat = true,
                    latitude = null,
                    longitude = null,
                    accuracyMeters = null,
                ),
            ),
        )
        val detectionsBySample = mapOf(
            "sample-1" to listOf(
                Detection(
                    id = "det-1",
                    sampleId = "sample-1",
                    classLabel = "Ascaris lumbricoides",
                    confidence = 0.91f,
                    bboxX = 0.1f,
                    bboxY = 0.2f,
                    bboxW = 0.3f,
                    bboxH = 0.4f,
                    verdict = DetectionVerdict.CONFIRMED,
                    expertClass = "Ascaris lumbricoides",
                    verifiedByUser = true,
                ),
            ),
        )

        val metadata = ReportMetadata(
            reportId = "report-1",
            session = session,
            generatedBy = "user-1",
            generatedAt = Instant.ofEpochMilli(30_000L),
            totalSamples = 2,
            totalEggsConfirmed = 3,
            positiveSpecies = listOf("Ascaris lumbricoides"),
            epgPerSpecies = mapOf("Ascaris lumbricoides" to 24),
        )
        val csv = builder.build(
            metadata = metadata,
            samples = samples,
            detectionsBySample = detectionsBySample,
        )

        val expected = """
            # AgarthaVision Session Report
            # report_id: report-1
            # session_id: session-1
            # session_label: Smear A
            # session_started_at: 1970-01-01T00:00:10Z
            # session_ended_at: 1970-01-01T00:00:20Z
            # device_id: device-1
            # generated_by: user-1
            # generated_at: 1970-01-01T00:00:30Z
            # total_samples: 2
            # total_eggs_confirmed: 3
            # positive_species: Ascaris lumbricoides
            # epg_ascaris_lumbricoides: 24
            # epg_trichuris_trichiura: 0
            # epg_hookworm: 0

            sample_id,captured_at,verified_at,model_class,model_confidence,expert_class,verdict,gps_lat,gps_lng,gps_accuracy,is_manual,is_repeat,user_note,model_version
            sample-1,1970-01-01T00:00:01Z,1970-01-01T00:00:02Z,Ascaris lumbricoides,0.91,Ascaris lumbricoides,confirmed,10.0,20.0,5.0,false,false,"note,one",model-1
            sample-2,1970-01-01T00:00:03Z,1970-01-01T00:00:04Z,,,,,,,,true,true,,model-2

        """.trimIndent() + "\n"

        assertEquals(expected, csv)
    }
}

/**
 * Test-fixture defaults for building a [Sample] — named per-field so a call site only needs
 * to override the 1-2 fields relevant to the case under test.
 */
private data class SampleTestData(
    val id: String,
    val sessionId: String = "session-1",
    val userId: String = "user-1",
    val timestamp: Long = 1_000L,
    val verifiedAt: Long = 2_000L,
    val inferenceModelVersion: String = "model-1",
    val userNote: String? = null,
    val isManual: Boolean = false,
    val isRepeat: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
)

private fun sample(data: SampleTestData): Sample =
    Sample(
        id = data.id,
        userId = data.userId,
        timestamp = data.timestamp,
        verifiedAt = data.verifiedAt,
        deviceId = "device-1",
        sessionId = data.sessionId,
        filePath = "/tmp/${data.id}.jpg",
        storagePath = "${data.userId}/${data.id}.jpg",
        inferenceModelVersion = data.inferenceModelVersion,
        userNote = data.userNote,
        isManual = data.isManual,
        isRepeat = data.isRepeat,
        latitude = data.latitude,
        longitude = data.longitude,
        accuracyMeters = data.accuracyMeters,
        status = SampleStatus.SYNCED,
    )
