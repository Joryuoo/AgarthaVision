package com.agarthavision.data.supabase

import android.util.Log
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SessionSyncStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import javax.inject.Inject

/**
 * Persists recording session lifecycle changes to Supabase Postgres.
 *
 * The local Room entity uses `session_id`, while the Supabase table's primary key
 * is `id`; this data source is the translation boundary between those shapes.
 */
class SessionRemoteDataSource @Inject constructor(
    private val supabaseProvider: dagger.Lazy<SupabaseClient>,
) {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    /**
     * Returns the authenticated Supabase user id, or null when no session exists.
     */
    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    /**
     * Inserts a new row into the Supabase `sessions` table.
     */
    suspend fun insertSession(session: SessionEntity) {
        supabase.postgrest[SESSIONS_TABLE].insert(session.toInsertRow())
    }

    /**
     * Upserts the session row (insert or update on primary-key conflict). Idempotent so a
     * pending session can be re-pushed safely. Per ADR-007.
     */
    suspend fun upsertSession(session: SessionEntity) {
        supabase.postgrest[SESSIONS_TABLE].upsert(session.toInsertRow())
    }

    // `closeSession` is gone. It set `ended_at` and `notes`, and `0001_init.sql:152-176`
    // carries neither — the update would have been rejected by PostgREST as unknown columns.
    // Sessions do not end (86d4ab4vm) and the note was an ad-hoc patient identifier that
    // PatientEntity replaces, so there is nothing left for it to write.

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    // ── Pull (read from server) ────────────────────────────────────────────────

    // The phone holds the signed-in user's own sessions plus every session of a patient they are
    // assigned to: a patient's full history, colleagues' smears included (14zcqntjph5). That is
    // exactly what 0001's author policy and 0007's `sessions_select_via_patient` give a medtech,
    // and it is asked for in those two halves rather than left to RLS, because RLS gives an org
    // admin (console `admin/0002`) their whole laboratory (14zcqntjt3p).

    /**
     * Fetches a page of the sessions [userId] authored, ordered by start time ascending.
     * Inclusive range: rows [offset, offset+limit-1].
     */
    suspend fun fetchOwnSessions(userId: String, offset: Long = 0L, limit: Long = 500L): List<SessionEntity> =
        supabase.postgrest[SESSIONS_TABLE].select {
            filter { eq("user_id", userId) }
            order("started_at", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<JsonElement>().mapNotNull { element ->
            runCatching {
                jsonDecoder.decodeFromJsonElement<SessionRow>(element).toEntity()
            }.onFailure { error ->
                Log.w("SessionRemoteDataSource", "Skipping malformed session row: ${error.message}")
            }.getOrNull()
        }

    /**
     * Fetches a page of the sessions of the given [patientIds], whoever authored them, ordered by
     * start time ascending. Inclusive range: rows [offset, offset+limit-1]. Callers chunk the ids
     * and must guard against an empty list.
     */
    suspend fun fetchSessionsForPatients(
        patientIds: List<String>,
        offset: Long = 0L,
        limit: Long = 500L,
    ): List<SessionEntity> =
        supabase.postgrest[SESSIONS_TABLE].select {
            filter { isIn("patient_id", patientIds) }
            order("started_at", Order.ASCENDING)
            range(offset, offset + limit - 1)
        }.decodeList<JsonElement>().mapNotNull { element ->
            runCatching {
                jsonDecoder.decodeFromJsonElement<SessionRow>(element).toEntity()
            }.onFailure { error ->
                Log.w("SessionRemoteDataSource", "Skipping malformed session row: ${error.message}")
            }.getOrNull()
        }

    /**
     * `patient_id` is not optional on either side. `0001_init.sql:152-176` declares it
     * `not null references patients(id)`, matching the local Room foreign key, so a session
     * pushed ahead of its patient is rejected — which is why `SyncPendingDataUseCase` pushes
     * patients first and `FetchRemoteDataUseCase` pulls them first.
     */
    private fun SessionEntity.toInsertRow(): SessionInsertRow =
        SessionInsertRow(
            id = sessionId,
            userId = requireNotNull(userId) { "Session user id is required for Supabase sync." },
            patientId = patientId,
            deviceId = deviceId,
            startedAt = Instant.ofEpochMilli(startedAt).toString(),
            label = label,
        )

    @Serializable
    private data class SessionInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("patient_id")
        val patientId: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("started_at")
        val startedAt: String,
        @SerialName("label")
        val label: String?,
    )

    private companion object {
        private const val SESSIONS_TABLE = "sessions"
    }
}

// ── Select DTO (read path) ────────────────────────────────────────────────

@Serializable
internal data class SessionRow(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("label") val label: String? = null,
)

internal fun SessionRow.toEntity(): SessionEntity = SessionEntity(
    sessionId = id,
    userId = userId,
    patientId = patientId,
    deviceId = deviceId,
    startedAt = parseSupabaseInstant(startedAt).toEpochMilli(),
    label = label,
    supabaseStatus = SessionSyncStatus.SYNCED.value,
)
