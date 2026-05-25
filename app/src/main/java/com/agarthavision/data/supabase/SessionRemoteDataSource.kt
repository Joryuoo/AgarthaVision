package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.SessionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
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
     * Marks the matching Supabase `sessions` row as ended.
     */
    suspend fun closeSession(
        sessionId: String,
        endedAt: Instant,
    ) {
        supabase.postgrest[SESSIONS_TABLE].update(
            {
                set("ended_at", endedAt.toString())
            },
        ) {
            filter {
                eq("id", sessionId)
            }
        }
    }

    private fun SessionEntity.toInsertRow(): SessionInsertRow =
        SessionInsertRow(
            id = sessionId,
            userId = requireNotNull(userId) { "Session user id is required for Supabase sync." },
            deviceId = deviceId,
            startedAt = Instant.ofEpochMilli(startedAt).toString(),
            endedAt = endedAt?.let { Instant.ofEpochMilli(it).toString() },
            notes = notes,
        )

    @Serializable
    private data class SessionInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("started_at")
        val startedAt: String,
        @SerialName("ended_at")
        val endedAt: String?,
        @SerialName("notes")
        val notes: String?,
    )

    private companion object {
        private const val SESSIONS_TABLE = "sessions"
    }
}
