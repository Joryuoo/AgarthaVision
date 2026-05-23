package com.agarthavision.domain.model

import java.util.UUID

/**
 * Domain model representing a captured microscopy sample and its associated metadata.
 * See docs/03_MOBILE_APP_PLAN.md §1.3.
 */
data class Sample(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val sessionId: String,
    val filePath: String,
    val latitude: Double?,
    val longitude: Double?,
    val status: SampleStatus = SampleStatus.CAPTURED,
    // Add other fields as needed for §1.3
)
