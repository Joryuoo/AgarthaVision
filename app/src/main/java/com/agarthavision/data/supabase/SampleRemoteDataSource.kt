package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.mapper.SamplePrediction
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.SampleStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Writes verified sample images and metadata to Supabase Storage and Postgres, and
 * reads them back for the sync-down (initial fetch) path.
 */
@Suppress("TooManyFunctions")
class SampleRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    /**
     * Uploads the sample image and writes its `samples`, `predictions` and `detections` rows.
     *
     * **The predictions travel with the sample, in the same call, and only here.** This is the
     * one path that writes a sample, and it runs only for verified ones, so a prediction row
     * cannot exist for a frame nobody reviewed. Order is the FK chain: the sample first, because
     * a prediction references it; the predictions next, because a detection references its
     * prediction; the detections last. A failure part-way throws, the sample is marked
     * `sync_failed`, and the next pass repeats the whole call — every write here is idempotent,
     * so a retry converges on the same rows rather than duplicating any.
     *
     * @return Supabase Storage object path for the uploaded JPEG.
     * @throws IllegalStateException when no Supabase user session is available.
     */
    suspend fun syncSample(
        sample: SampleEntity,
        detections: List<DetectionEntity>,
        findings: List<SampleSpeciesFindingEntity>,
        predictions: List<SamplePrediction>,
        imageBytes: ByteArray,
    ): String {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("A Supabase user session is required to sync samples.")
        val storagePath = "$userId/${sample.sampleId}.jpg"

        supabase.storage.from(SAMPLES_BUCKET).upload(storagePath, imageBytes) {
            upsert = true
        }

        // Upsert, not insert. A verified sample is re-editable now, so it syncs more than once;
        // the plain insert raised a primary-key conflict on the second attempt and left the row
        // marked sync_failed forever. Both tables take client-supplied ids, and detection ids
        // are derived rather than random, so an edit updates the rows it corrects.
        //
        // This needs the UPDATE policies added in 0012: 0001_init.sql gave samples and
        // detections select and insert only, so a conflicting upsert is rejected by RLS
        // without them.
        supabase.postgrest[SAMPLES_TABLE].upsert(sample.toInsertRow(userId, storagePath))

        // Insert-if-absent, not upsert. A model's output never changes, so a re-sync of an
        // edited sample has nothing to write here — and 0004 grants no UPDATE policy on the
        // table, which an ON CONFLICT DO UPDATE would need.
        if (predictions.isNotEmpty()) {
            supabase.postgrest[PREDICTIONS_TABLE].upsert(predictions.map { it.toInsertRow() }) {
                onConflict = "id"
                ignoreDuplicates = true
            }
        }

        if (detections.isNotEmpty()) {
            if (predictions.isEmpty()) {
                // This device does not hold the frame's model output — a manual capture, or a
                // sample whose predictions never reached it. The link is left out of the payload
                // entirely rather than sent as null, so PostgREST leaves the column alone and a
                // link the server already has (0004's backfill, another device's push) survives.
                supabase.postgrest[DETECTIONS_TABLE].upsert(detections.map { it.toInsertRow() })
            } else {
                // A detection links to the prediction at its own ordinal. One absent from the map
                // is an egg the medtech added: it rules on no model claim, and null says so.
                val predictionIdByDetection = predictions.associate { it.detectionId to it.id }
                supabase.postgrest[DETECTIONS_TABLE].upsert(
                    detections.map { it.toLinkedInsertRow(predictionIdByDetection[it.detectionId]) },
                )
            }
        }

        // Findings are replaced wholesale rather than upserted: a species the medtech removed
        // on re-open has to actually disappear, and an upsert would leave the stale row behind
        // to inflate the count. Not a C8 deletion - a count is a current statement, and the
        // detections and the Storage object it protects are untouched.
        supabase.postgrest[FINDINGS_TABLE].delete {
            filter { eq("sample_id", sample.sampleId) }
        }
        if (findings.isNotEmpty()) {
            supabase.postgrest[FINDINGS_TABLE].insert(findings.map { it.toInsertRow() })
        }

        return storagePath
    }

    // ── Pull (read from server) ────────────────────────────────────────────────

    /**
     * Fetches a page of samples owned by [userId], ordered by capture time ascending.
     * Inclusive range: rows [offset, offset+limit-1].
     */
    suspend fun fetchSamples(userId: String, offset: Long, limit: Long): List<SampleEntity> =
        supabase.postgrest[SAMPLES_TABLE].select {
            filter { eq("user_id", userId) }
            order("captured_at", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<SampleRow>().map { it.toEntity() }

    /**
     * Fetches all detections for the given [sampleIds].
     * Callers must guard against an empty list — isIn with no values is undefined.
     */
    suspend fun fetchDetections(sampleIds: List<String>): List<DetectionEntity> =
        supabase.postgrest[DETECTIONS_TABLE].select {
            filter { isIn("sample_id", sampleIds) }
        }.decodeList<DetectionRow>().map { it.toEntity() }

    /**
     * Fetches the model's own output for the given [sampleIds].
     * Callers must guard against an empty list.
     */
    suspend fun fetchPredictions(sampleIds: List<String>): List<SamplePrediction> =
        supabase.postgrest[PREDICTIONS_TABLE].select {
            filter { isIn("sample_id", sampleIds) }
        }.decodeList<PredictionRow>().map { it.toSamplePrediction() }

    /**
     * Fetches all findings for the given [sampleIds].
     * Callers must guard against an empty list.
     */
    suspend fun fetchFindings(sampleIds: List<String>): List<SampleSpeciesFindingEntity> =
        supabase.postgrest[FINDINGS_TABLE].select {
            filter { isIn("sample_id", sampleIds) }
        }.decodeList<FindingRow>().map { it.toEntity() }

    /**
     * Downloads a private sample image from Storage, as the signed-in medtech.
     *
     * Authenticated rather than signed: this runs inside the sync pass, where a session
     * already exists, and a signed URL would add a round trip and a 15-minute expiry to a
     * transfer that starts immediately. [createSignedSampleImageUrl] stays for the on-open
     * path, where the URL is handed to Coil rather than read here.
     *
     * Throws on a missing object or a lost connection, which is what the caller counts.
     */
    suspend fun downloadSampleImage(storagePath: String): ByteArray =
        supabase.storage.from(SAMPLES_BUCKET).downloadAuthenticated(storagePath)

    /**
     * Creates a short-lived URL for reading a private sample image from Storage.
     */
    suspend fun createSignedSampleImageUrl(storagePath: String): String =
        supabase.storage.from(SAMPLES_BUCKET).createSignedUrl(
            path = storagePath,
            expiresIn = SIGNED_URL_EXPIRY,
        )

    private fun SampleEntity.toInsertRow(
        userId: String,
        storagePath: String,
    ): SampleInsertRow {
        val verifiedAtMillis = verifiedAt.takeIf { it > 0L } ?: Instant.now().toEpochMilli()
        return SampleInsertRow(
            id = sampleId,
            sessionId = sessionId,
            userId = userId,
            capturedAt = Instant.ofEpochMilli(timestamp).toString(),
            verifiedAt = Instant.ofEpochMilli(verifiedAtMillis).toString(),
            storagePath = storagePath,
            inferenceModelVersion = inferenceModelVersion.ifBlank { UNKNOWN_MODEL_VERSION },
            needsReannotation = needsReannotation,
            isManual = isManual,
            userNote = userNote?.takeIf { it.isNotBlank() },
        )
    }

    private fun SamplePrediction.toInsertRow(): PredictionInsertRow = PredictionInsertRow(
        id = id,
        sampleId = sampleId,
        ordinal = ordinal,
        classLabel = prediction.classLabel,
        confidence = prediction.confidence,
        bboxX = prediction.x,
        bboxY = prediction.y,
        bboxW = prediction.width,
        bboxH = prediction.height,
    )

    private fun DetectionEntity.toLinkedInsertRow(predictionId: String?): LinkedDetectionInsertRow =
        toInsertRow().let { row ->
            LinkedDetectionInsertRow(
                id = row.id,
                sampleId = row.sampleId,
                classLabel = row.classLabel,
                confidence = row.confidence,
                bboxX = row.bboxX,
                bboxY = row.bboxY,
                bboxW = row.bboxW,
                bboxH = row.bboxH,
                verdict = row.verdict,
                expertClass = row.expertClass,
                predictionId = predictionId,
            )
        }

    private fun DetectionEntity.toInsertRow(): DetectionInsertRow {
        val resolvedVerdict =
            if (verdict.isNotBlank()) DetectionVerdict.fromValue(verdict) else DetectionVerdict.CONFIRMED
        return DetectionInsertRow(
            id = detectionId,
            sampleId = sampleId,
            classLabel = classLabel,
            confidence = confidence,
            bboxX = bboxX,
            bboxY = bboxY,
            bboxW = bboxW,
            bboxH = bboxH,
            verdict = resolvedVerdict.remoteValue,
            expertClass = expertClass,
        )
    }

    @Serializable
    private data class SampleInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("captured_at")
        val capturedAt: String,
        @SerialName("verified_at")
        val verifiedAt: String,
        @SerialName("storage_path")
        val storagePath: String,
        @SerialName("inference_model_version")
        val inferenceModelVersion: String,
        @SerialName("needs_reannotation")
        val needsReannotation: Boolean,
        @SerialName("is_manual")
        val isManual: Boolean,
        @SerialName("user_note")
        val userNote: String?,
    )

    @Serializable
    private data class DetectionInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("sample_id")
        val sampleId: String,
        @SerialName("class_label")
        val classLabel: String,
        @SerialName("confidence")
        val confidence: Float,
        // Nullable per ADR-005: manual captures (samples.is_manual = true) have no bbox.
        @SerialName("bbox_x")
        val bboxX: Float?,
        @SerialName("bbox_y")
        val bboxY: Float?,
        @SerialName("bbox_w")
        val bboxW: Float?,
        @SerialName("bbox_h")
        val bboxH: Float?,
        @SerialName("verdict")
        val verdict: String,
        @SerialName("expert_class")
        val expertClass: String?,
    )

    /**
     * [DetectionInsertRow] plus the link to the prediction it rules on.
     *
     * A second class rather than a nullable field on the first, because the two have to differ
     * in whether the key is *present*, not only in its value: PostgREST updates exactly the
     * columns a payload names, and only a payload without `prediction_id` leaves an existing
     * link alone. Every row of one push uses one class, so the column list stays uniform.
     */
    @Serializable
    private data class LinkedDetectionInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("sample_id")
        val sampleId: String,
        @SerialName("class_label")
        val classLabel: String,
        @SerialName("confidence")
        val confidence: Float,
        @SerialName("bbox_x")
        val bboxX: Float?,
        @SerialName("bbox_y")
        val bboxY: Float?,
        @SerialName("bbox_w")
        val bboxW: Float?,
        @SerialName("bbox_h")
        val bboxH: Float?,
        @SerialName("verdict")
        val verdict: String,
        @SerialName("expert_class")
        val expertClass: String?,
        @SerialName("prediction_id")
        val predictionId: String?,
    )

    @Serializable
    private data class PredictionInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("sample_id")
        val sampleId: String,
        @SerialName("ordinal")
        val ordinal: Int,
        @SerialName("class_label")
        val classLabel: String,
        @SerialName("confidence")
        val confidence: Float,
        @SerialName("bbox_x")
        val bboxX: Float,
        @SerialName("bbox_y")
        val bboxY: Float,
        @SerialName("bbox_w")
        val bboxW: Float,
        @SerialName("bbox_h")
        val bboxH: Float,
    )

    @Serializable
    private data class FindingInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("sample_id")
        val sampleId: String,
        @SerialName("species")
        val species: String,
        // Always null. The column is in the applied migration and cannot be dropped (C6),
        // but nothing writes a stage since 86d4a6jwy was reverted.
        @SerialName("stage")
        val stage: String?,
        @SerialName("egg_count")
        val eggCount: Int,
    )

    private fun SampleSpeciesFindingEntity.toInsertRow(): FindingInsertRow = FindingInsertRow(
        id = findingId,
        sampleId = sampleId,
        species = species,
        stage = stage,
        eggCount = eggCount,
    )

    // ── Select DTOs (read path) ────────────────────────────────────────────────

    @Serializable
    private data class SampleRow(
        @SerialName("id") val id: String,
        @SerialName("session_id") val sessionId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("captured_at") val capturedAt: String,
        @SerialName("verified_at") val verifiedAt: String? = null,
        @SerialName("storage_path") val storagePath: String,
        @SerialName("inference_model_version") val inferenceModelVersion: String,
        @SerialName("needs_reannotation") val needsReannotation: Boolean,
        @SerialName("is_manual") val isManual: Boolean,
        @SerialName("user_note") val userNote: String? = null,
        @SerialName("deleted_at") val deletedAt: String? = null,
    )

    @Serializable
    private data class DetectionRow(
        @SerialName("id") val id: String,
        @SerialName("sample_id") val sampleId: String,
        @SerialName("class_label") val classLabel: String,
        @SerialName("confidence") val confidence: Float,
        @SerialName("bbox_x") val bboxX: Float? = null,
        @SerialName("bbox_y") val bboxY: Float? = null,
        @SerialName("bbox_w") val bboxW: Float? = null,
        @SerialName("bbox_h") val bboxH: Float? = null,
        @SerialName("verdict") val verdict: String,
        @SerialName("expert_class") val expertClass: String? = null,
    )

    @Serializable
    private data class PredictionRow(
        @SerialName("sample_id") val sampleId: String,
        @SerialName("ordinal") val ordinal: Int,
        @SerialName("class_label") val classLabel: String,
        @SerialName("confidence") val confidence: Float,
        @SerialName("bbox_x") val bboxX: Float,
        @SerialName("bbox_y") val bboxY: Float,
        @SerialName("bbox_w") val bboxW: Float,
        @SerialName("bbox_h") val bboxH: Float,
    )

    @Serializable
    private data class FindingRow(
        @SerialName("id") val id: String,
        @SerialName("sample_id") val sampleId: String,
        @SerialName("species") val species: String,
        @SerialName("stage") val stage: String? = null,
        @SerialName("egg_count") val eggCount: Int,
    )

    // ── DTO → Entity mappers (read path) ─────────────────────────────────────

    private fun SampleRow.toEntity(): SampleEntity = SampleEntity(
        sampleId = id,
        sessionId = sessionId,
        userId = userId,
        deviceId = "",   // D2: device identity not stored in remote
        timestamp = parseSupabaseInstant(capturedAt).toEpochMilli(),
        verifiedAt = verifiedAt?.let { parseSupabaseInstant(it).toEpochMilli() } ?: 0L,
        imagePath = "",  // D1: image is in Storage, not local disk
        storagePath = storagePath,
        inferenceModelVersion = inferenceModelVersion,
        needsReannotation = needsReannotation,
        isManual = isManual,
        userNote = userNote,
        status = SampleStatus.SYNCED.value,
        predictionsJson = null,
        imageWidth = null,
        imageHeight = null,
        deletedAt = deletedAt?.let { parseSupabaseInstant(it).toEpochMilli() },
    )

    private fun DetectionRow.toEntity(): DetectionEntity = DetectionEntity(
        detectionId = id,
        sampleId = sampleId,
        classLabel = classLabel,
        confidence = confidence,
        bboxX = bboxX,
        bboxY = bboxY,
        bboxW = bboxW,
        bboxH = bboxH,
        verdict = DetectionVerdict.fromValue(verdict).value,
        expertClass = expertClass,
    )

    private fun PredictionRow.toSamplePrediction(): SamplePrediction = SamplePrediction(
        sampleId = sampleId,
        ordinal = ordinal,
        prediction = Prediction(
            classLabel = classLabel,
            confidence = confidence,
            x = bboxX,
            y = bboxY,
            width = bboxW,
            height = bboxH,
        ),
    )

    private fun FindingRow.toEntity(): SampleSpeciesFindingEntity = SampleSpeciesFindingEntity(
        findingId = id,
        sampleId = sampleId,
        species = species,
        stage = stage,
        eggCount = eggCount,
    )

    private companion object {
        private const val SAMPLES_BUCKET = "samples"
        private const val SAMPLES_TABLE = "samples"
        private const val DETECTIONS_TABLE = "detections"
        private const val PREDICTIONS_TABLE = "predictions"
        private const val FINDINGS_TABLE = "sample_species_findings"
        private const val UNKNOWN_MODEL_VERSION = "unknown"
        private val SIGNED_URL_EXPIRY = 15.minutes
    }
}
