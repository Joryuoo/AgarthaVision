package com.agarthavision.domain.model

/**
 * Aggregate counts for the Sessions screen header. Counts apply the same filter
 * predicate as the paginated list query so the header and the list can never
 * disagree on the universe of sessions counted.
 */
data class SessionsCounts(
    val totalCount: Int = 0,
    /**
     * Frames still awaiting review across the counted sessions.
     *
     * Was `activeCount`, the number of sessions with no `ended_at`. Sessions do not end any
     * more (86d4ab4vm), so that counted all of them and told the medtech nothing.
     */
    val unverifiedCount: Int = 0,
)
