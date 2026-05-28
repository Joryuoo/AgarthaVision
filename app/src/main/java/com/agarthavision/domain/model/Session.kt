package com.agarthavision.domain.model

/**
 * Domain model for one capture session. Per ADR-005 a session equals one fecal
 * smear; [label] is the medtech-entered smear name set in the SessionPicker.
 */
data class Session(
    val id: String,
    val userId: String?,
    val deviceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val notes: String?,
    val label: String?,
)
