package com.agarthavision.domain.model

/**
 * Domain model for one continuous recording session.
 */
data class Session(
    val id: String,
    val userId: String?,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val notes: String?,
)
