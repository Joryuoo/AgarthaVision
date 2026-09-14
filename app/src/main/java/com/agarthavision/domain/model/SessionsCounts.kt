package com.agarthavision.domain.model

/**
 * Aggregate counts for the Sessions screen header. Counts apply the same filter
 * predicate as the paginated list query so the header and the list can never
 * disagree on the universe of sessions counted.
 */
data class SessionsCounts(
    val totalCount: Int = 0,
    val activeCount: Int = 0,
)
