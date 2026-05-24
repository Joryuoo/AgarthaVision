package com.agarthavision.core.session

import com.agarthavision.data.local.entity.SessionEntity
import java.time.Instant

/**
 * The app-level capture session state. See docs/03_MOBILE_APP_PLAN.md §1.1.
 */
sealed interface SessionState {
    data object Idle : SessionState
    data class Recording(
        val session: SessionEntity,
        val startedAt: Instant,
    ) : SessionState
}
