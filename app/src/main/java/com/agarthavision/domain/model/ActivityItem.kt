package com.agarthavision.domain.model

sealed interface ActivityItem {
    val occurredAt: Long

    data class FramesVerified(
        val sessionId: String,
        val sessionLabel: String?,
        val count: Int,
        override val occurredAt: Long,
    ) : ActivityItem

    data class FramesCaptured(
        val sessionId: String,
        val sessionLabel: String?,
        val count: Int,
        override val occurredAt: Long,
    ) : ActivityItem

    data class PatientAdded(
        val patientId: String,
        val maskedName: String,
        override val occurredAt: Long,
    ) : ActivityItem

    data class SessionStarted(
        val sessionId: String,
        val sessionLabel: String?,
        override val occurredAt: Long,
    ) : ActivityItem

    data class SyncFinished(
        val itemCount: Int,
        override val occurredAt: Long,
    ) : ActivityItem
}
