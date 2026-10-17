package com.agarthavision.data.supabase

import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.PatientSyncStatus
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.model.SessionSyncStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract test suite verifying that real Supabase JSON payloads decode into DTOs
 * and map accurately to Room entities, catching schema drift or mapper crashes on JVM.
 */
@RunWith(RobolectricTestRunner::class)
class SupabaseMapperContractTest {

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    // ── Patient & PatientUser Mappers ────────────────────────────────────────

    @Test
    fun `PatientRow maps well-formed Supabase JSON to PatientEntity`() {
        val capturedJson = """
            {
                "id": "pat-101",
                "lastname": "Santos",
                "firstname": "Maria",
                "middle_name": "Clara",
                "sex": "F",
                "birthdate": "1995-06-15",
                "psgc_barangay_code": "072217001",
                "created_by": "usr-888",
                "created_at": "2026-03-10T08:30:00.000Z",
                "updated_at": "2026-03-10T08:30:00.000Z",
                "unknown_extra_column": "ignored"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<PatientRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("pat-101", entity.patientId)
        assertEquals("Santos", entity.lastname)
        assertEquals("Maria", entity.firstname)
        assertEquals("Clara", entity.middleName)
        assertEquals("F", entity.sex)
        assertEquals("072217001", entity.psgcBarangayCode)
        assertEquals("usr-888", entity.createdBy)
        assertEquals(PatientSyncStatus.SYNCED.value, entity.supabaseStatus)
    }

    @Test
    fun `PatientRow maps plausibly malformed JSON with null optional fields`() {
        val capturedJson = """
            {
                "id": "pat-102",
                "lastname": "Dela Cruz",
                "firstname": "Juan",
                "middle_name": null,
                "sex": "M",
                "birthdate": "2000-01-01",
                "psgc_barangay_code": "072217002",
                "created_by": "usr-888",
                "created_at": "2026-03-10T08:30:00Z",
                "updated_at": "2026-03-10T08:30:00Z"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<PatientRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("pat-102", entity.patientId)
        assertNull(entity.middleName)
    }

    @Test
    fun `PatientUserRow maps well-formed Supabase link JSON`() {
        val capturedJson = """
            {
                "patient_id": "pat-101",
                "user_id": "usr-888",
                "linked_at": "2026-03-10T08:30:00.123456Z"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<PatientUserRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("pat-101", entity.patientId)
        assertEquals("usr-888", entity.userId)
        assertTrue(entity.linkedAt > 0L)
    }

    // ── Session Mapper ───────────────────────────────────────────────────────

    @Test
    fun `SessionRow maps well-formed Supabase JSON to SessionEntity`() {
        val capturedJson = """
            {
                "id": "sess-202",
                "user_id": "usr-888",
                "patient_id": "pat-101",
                "device_id": "dev-alpha",
                "started_at": "2026-03-10T09:00:00.000000+00:00",
                "label": "SANM-S01"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<SessionRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("sess-202", entity.sessionId)
        assertEquals("usr-888", entity.userId)
        assertEquals("pat-101", entity.patientId)
        assertEquals("dev-alpha", entity.deviceId)
        assertEquals("SANM-S01", entity.label)
        assertEquals(SessionSyncStatus.SYNCED.value, entity.supabaseStatus)
    }

    @Test
    fun `SessionRow maps plausibly malformed JSON with null label`() {
        val capturedJson = """
            {
                "id": "sess-203",
                "user_id": "usr-888",
                "patient_id": "pat-101",
                "device_id": "dev-alpha",
                "started_at": "2026-03-10T09:00:00Z",
                "label": null
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<SessionRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("sess-203", entity.sessionId)
        assertNull(entity.label)
    }

    // ── Sample, Detection, Prediction, Finding Mappers ──────────────────────

    @Test
    fun `SampleRow maps well-formed Supabase JSON to SampleEntity`() {
        val capturedJson = """
            {
                "id": "smp-301",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "captured_at": "2026-03-10T09:15:00.000Z",
                "verified_at": "2026-03-10T09:20:00.000Z",
                "storage_path": "usr-888/smp-301.jpg",
                "inference_model_version": "yolov8-v1.2",
                "needs_reannotation": false,
                "is_manual": false,
                "user_note": "Ascaris detected",
                "deleted_at": null
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<SampleRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("smp-301", entity.sampleId)
        assertEquals("sess-202", entity.sessionId)
        assertEquals("usr-888/smp-301.jpg", entity.storagePath)
        assertEquals("yolov8-v1.2", entity.inferenceModelVersion)
        assertEquals("Ascaris detected", entity.userNote)
        assertEquals(SampleStatus.SYNCED.value, entity.status)
        assertEquals("", entity.imagePath) // Remote path is empty for local disk
        assertNull(entity.deletedAt)
    }

    @Test
    fun `SampleRow maps tombstoned sample with deleted_at`() {
        val capturedJson = """
            {
                "id": "smp-302",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "captured_at": "2026-03-10T09:15:00Z",
                "verified_at": null,
                "storage_path": "usr-888/smp-302.jpg",
                "inference_model_version": "v1",
                "needs_reannotation": true,
                "is_manual": true,
                "user_note": null,
                "deleted_at": "2026-03-10T10:00:00.000Z"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<SampleRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("smp-302", entity.sampleId)
        assertEquals(0L, entity.verifiedAt)
        assertNotNull(entity.deletedAt)
        assertTrue((entity.deletedAt ?: 0L) > 0L)
    }

    @Test
    fun `DetectionRow maps well-formed Supabase JSON to DetectionEntity`() {
        val capturedJson = """
            {
                "id": "det-401",
                "sample_id": "smp-301",
                "class_label": "Ascaris lumbricoides",
                "confidence": 0.94,
                "bbox_x": 0.25,
                "bbox_y": 0.35,
                "bbox_w": 0.12,
                "bbox_h": 0.15,
                "verdict": "CONFIRMED",
                "expert_class": null
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<DetectionRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("det-401", entity.detectionId)
        assertEquals("smp-301", entity.sampleId)
        assertEquals("Ascaris lumbricoides", entity.classLabel)
        assertEquals(0.94f, entity.confidence, 0.001f)
        assertEquals(DetectionVerdict.CONFIRMED.value, entity.verdict)
    }

    @Test
    fun `PredictionRow maps well-formed Supabase prediction JSON`() {
        val capturedJson = """
            {
                "sample_id": "smp-301",
                "ordinal": 0,
                "class_label": "Trichuris trichiura",
                "confidence": 0.88,
                "bbox_x": 0.10,
                "bbox_y": 0.20,
                "bbox_w": 0.15,
                "bbox_h": 0.18
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<PredictionRow>(capturedJson)
        val samplePrediction = row.toSamplePrediction()

        assertEquals("smp-301", samplePrediction.sampleId)
        assertEquals(0, samplePrediction.ordinal)
        assertEquals("Trichuris trichiura", samplePrediction.prediction.classLabel)
        assertEquals(0.88f, samplePrediction.prediction.confidence, 0.001f)
    }

    @Test
    fun `FindingRow maps well-formed Supabase finding JSON`() {
        val capturedJson = """
            {
                "id": "fnd-501",
                "sample_id": "smp-301",
                "species": "Hookworm",
                "stage": null,
                "egg_count": 3
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<FindingRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("fnd-501", entity.findingId)
        assertEquals("Hookworm", entity.species)
        assertNull(entity.stage)
        assertEquals(3, entity.eggCount)
    }

    // ── Report Mapper ────────────────────────────────────────────────────────

    @Test
    fun `ReportRow maps well-formed Supabase report JSON to ReportEntity`() {
        val capturedJson = """
            {
                "id": "rep-601",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "report_type": "session",
                "generated_at": "2026-03-10T11:00:00.000Z",
                "total_samples": 4,
                "total_eggs_confirmed": 12,
                "positive_species": ["Ascaris lumbricoides", "Hookworm"],
                "lpf_per_species": {
                    "Ascaris lumbricoides": { "min": 1, "max": 4 },
                    "Hookworm": { "min": 0, "max": 2 }
                },
                "csv_file_path": "/storage/rep-601.csv",
                "pdf_file_path": "/storage/rep-601.pdf"
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<ReportRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("rep-601", entity.reportId)
        assertEquals("sess-202", entity.sessionId)
        assertEquals("usr-888", entity.userId)
        assertEquals("session", entity.reportType)
        assertEquals(4, entity.totalSamples)
        assertEquals(12, entity.totalEggsConfirmed)
        assertEquals("/storage/rep-601.csv", entity.csvFilePath)
        assertEquals("/storage/rep-601.pdf", entity.pdfFilePath)

        // Check positive species JSON
        assertNotNull(entity.positiveSpeciesJson)
        assertTrue(entity.positiveSpeciesJson.contains("Ascaris lumbricoides"))

        // Check LPF per species JSON
        assertNotNull(entity.lpfPerSpeciesJson)
        assertTrue(entity.lpfPerSpeciesJson.contains("\"min\":1"))
    }

    @Test
    fun `ReportRow maps plausibly malformed report JSON with empty or null fields`() {
        val capturedJson = """
            {
                "id": "rep-602",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "report_type": "session",
                "generated_at": "2026-03-10T11:00:00Z",
                "total_samples": 0,
                "total_eggs_confirmed": 0,
                "positive_species": [],
                "lpf_per_species": {},
                "csv_file_path": null,
                "pdf_file_path": null
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<ReportRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("rep-602", entity.reportId)
        assertNull(entity.csvFilePath)
        assertNull(entity.pdfFilePath)
        assertEquals("[]", entity.positiveSpeciesJson)
        assertEquals("{}", entity.lpfPerSpeciesJson)
    }

    @Test
    fun `ReportRow parses float min and max without becoming 0`() {
        val capturedJson = """
            {
                "id": "rep-603",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "report_type": "session",
                "generated_at": "2026-03-10T11:00:00Z",
                "total_samples": 2,
                "total_eggs_confirmed": 5,
                "positive_species": ["Ascaris lumbricoides"],
                "lpf_per_species": {
                    "Ascaris lumbricoides": { "min": 2.0, "max": 4.5 }
                }
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<ReportRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("rep-603", entity.reportId)
        assertTrue(entity.lpfPerSpeciesJson.contains("\"min\":2"))
        assertTrue(entity.lpfPerSpeciesJson.contains("\"max\":5"))
    }

    @Test
    fun `ReportRow parses missing min, missing max, or non-object value gracefully`() {
        val capturedJson = """
            {
                "id": "rep-604",
                "session_id": "sess-202",
                "user_id": "usr-888",
                "report_type": "session",
                "generated_at": "2026-03-10T11:00:00Z",
                "total_samples": 2,
                "total_eggs_confirmed": 5,
                "positive_species": ["Ascaris lumbricoides", "Hookworm"],
                "lpf_per_species": {
                    "Ascaris lumbricoides": { "max": 5 },
                    "Hookworm": "invalid_primitive"
                }
            }
        """.trimIndent()

        val row = jsonDecoder.decodeFromString<ReportRow>(capturedJson)
        val entity = row.toEntity()

        assertEquals("rep-604", entity.reportId)
        assertTrue(entity.lpfPerSpeciesJson.contains("\"min\":0"))
        assertTrue(entity.lpfPerSpeciesJson.contains("\"max\":5"))
    }

    @Test
    fun `tolerant list decoding skips bad row among good ones and imports valid ones`() {
        val capturedArrayJson = """
            [
                {
                    "id": "rep-good-1",
                    "session_id": "sess-1",
                    "user_id": "usr-1",
                    "report_type": "session",
                    "generated_at": "2026-03-10T11:00:00Z",
                    "total_samples": 1,
                    "total_eggs_confirmed": 2,
                    "positive_species": [],
                    "lpf_per_species": {}
                },
                {
                    "invalid_row_missing_required_fields": true
                },
                {
                    "id": "rep-good-2",
                    "session_id": "sess-1",
                    "user_id": "usr-1",
                    "report_type": "session",
                    "generated_at": "2026-03-10T11:05:00Z",
                    "total_samples": 2,
                    "total_eggs_confirmed": 4,
                    "positive_species": [],
                    "lpf_per_species": {}
                }
            ]
        """.trimIndent()

        val elements = jsonDecoder.decodeFromString<List<kotlinx.serialization.json.JsonElement>>(capturedArrayJson)
        val validEntities = elements.mapNotNull { element ->
            runCatching {
                jsonDecoder.decodeFromJsonElement(ReportRow.serializer(), element).toEntity()
            }.getOrNull()
        }

        assertEquals(2, validEntities.size)
        assertEquals("rep-good-1", validEntities[0].reportId)
        assertEquals("rep-good-2", validEntities[1].reportId)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
