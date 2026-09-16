package com.agarthavision.domain.model

/**
 * Aggregated stats for the full filtered result set shown in the Records screen stat row.
 * Computed in SQL from a per-session subquery so join fan-out cannot inflate the sums.
 * Mirrors the bind parameters of the page query so filtering is always identical.
 */
data class RecordsTotals(
    val sessionCount: Int = 0,
    val totalSamples: Int = 0,
    val totalEpg: Int = 0,
)
