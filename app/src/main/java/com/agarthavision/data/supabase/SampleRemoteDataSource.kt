package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.DetectionVerdict
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Writes verified sample images and metadata to Supabase Storage and Postgres.
 */
class SampleRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    /**
     * Uploads the sample image and inserts matching `samples` and `detections` rows.
     *
     * @return Supabase Storage object path for the uploaded JPEG.
     * @throws IllegalStateException when no Supabase user session is available.
     */
    suspend fun syncSample(
        sample: SampleEntity,
        detections: List<DetectionEntity>,
        imageBytes: ByteArray,
    ): String {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("A Supabase user session is required to sync samples.")
        val storagePath = "$userId/${sample.sampleId}.jpg"

        supabase.storage.from(SAMPLES_BUCKET).upload(storagePath, imageBytes) {
            upsert = true
        }
        supabase.postgrest[SAMPLES_TABLE].insert(sample.toInsertRow(userId, storagePath))
        if (detections.isNotEmpty()) {
            supabase.postgrest[DETECTIONS_TABLE].insert(detections.map { it.toInsertRow() })
        }

        return storagePath
    }

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
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAccuracy = gpsAccuracy,
            storagePath = storagePath,
            inferenceModelVersion = inferenceModelVersion.ifBlank { UNKNOWN_MODEL_VERSION },
            needsReannotation = needsReannotation,
            userNote = null,
        )
    }

    private fun DetectionEntity.toInsertRow(): DetectionInsertRow {
        val resolvedVerdict = when {
            verdict.isNotBlank() -> DetectionVerdict.fromValue(verdict)
            !verifiedByUser -> DetectionVerdict.FALSE_POSITIVE
            else -> DetectionVerdict.CONFIRMED
        }
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
        @SerialName("gps_latitude")
        val gpsLatitude: Double?,
        @SerialName("gps_longitude")
        val gpsLongitude: Double?,
        @SerialName("gps_accuracy")
        val gpsAccuracy: Float?,
        @SerialName("storage_path")
        val storagePath: String,
        @SerialName("inference_model_version")
        val inferenceModelVersion: String,
        @SerialName("needs_reannotation")
        val needsReannotation: Boolean,
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
        @SerialName("bbox_x")
        val bboxX: Float,
        @SerialName("bbox_y")
        val bboxY: Float,
        @SerialName("bbox_w")
        val bboxW: Float,
        @SerialName("bbox_h")
        val bboxH: Float,
        @SerialName("verdict")
        val verdict: String,
        @SerialName("expert_class")
        val expertClass: String?,
    )

    private companion object {
        private const val SAMPLES_BUCKET = "samples"
        private const val SAMPLES_TABLE = "samples"
        private const val DETECTIONS_TABLE = "detections"
        private const val UNKNOWN_MODEL_VERSION = "unknown"
        private val SIGNED_URL_EXPIRY = 15.minutes
    }
}
