package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SessionSyncStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject

/**
 * Persists recording session lifecycle changes to Supabase Postgres.
 *
 * The local Room entity uses `session_id`, while the Supabase table's primary key
 * is `id`; this data source is the translation boundary between those shapes.
 */
class SessionRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
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

    // ── Pull (read from server) ────────────────────────────────────────────────

    /**
     * Fetches all sessions owned by [userId], ordered by start time ascending.
     */
    suspend fun fetchSessions(userId: String): List<SessionEntity> =
        supabase.postgrest[SESSIONS_TABLE].select {
            filter { eq("user_id", userId) }
            order("started_at", Order.ASCENDING)
        }.decodeList<SessionRow>().map { it.toEntity() }

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

    // ── Select DTO (read path) ────────────────────────────────────────────────

    @Serializable
    private data class SessionRow(
        @SerialName("id") val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("patient_id") val patientId: String,
        @SerialName("device_id") val deviceId: String,
        @SerialName("started_at") val startedAt: String,
        @SerialName("label") val label: String? = null,
    )

    private fun SessionRow.toEntity(): SessionEntity = SessionEntity(
        sessionId = id,
        userId = userId,
        patientId = patientId,
        deviceId = deviceId,
        startedAt = Instant.parse(startedAt).toEpochMilli(),
        label = label,
        supabaseStatus = SessionSyncStatus.SYNCED.value,
    )

    private companion object {
        private const val SESSIONS_TABLE = "sessions"
    }
}
