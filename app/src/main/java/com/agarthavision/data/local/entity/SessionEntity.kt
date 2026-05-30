package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a capture session.
 *
 * Mirrors the Supabase `sessions` table. One row per `startSession()` call;
 * `endedAt` is set on `stopSession()`. See docs/03_MOBILE_APP_PLAN.md §1.1.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String?,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "label")
    val label: String? = null,
)
