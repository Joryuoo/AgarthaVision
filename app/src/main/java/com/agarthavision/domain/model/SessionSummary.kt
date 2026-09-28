package com.agarthavision.domain.model

data class SessionSummary(
    val session: Session,
    val patient: Patient,
    val totalFrames: Int,
    val framesToReview: Int,
    val isPositive: Boolean,
    val lastActivityAt: Long,
)

enum class SessionListFilter(val sql: String) {
    ALL("all"),
    TO_REVIEW("to_review"),
    NO_FRAMES("no_frames"),
    EXAMINED("examined"),
    POSITIVE("positive"),
}
