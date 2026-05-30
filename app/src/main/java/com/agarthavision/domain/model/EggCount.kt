package com.agarthavision.domain.model

/**
 * Per-species egg count for one session.
 */
data class EggCount(
    val species: String,
    val count: Int,
)
