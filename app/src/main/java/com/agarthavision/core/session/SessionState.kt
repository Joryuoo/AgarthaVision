package com.agarthavision.core.session

import com.agarthavision.data.local.entity.SessionEntity
import java.time.Instant

/**
 * The app-level capture session state. Per ADR-005, an active session = one
 * fecal smear. There is no inference-running sub-state any more: capture is
 * medtech-triggered (Track 2.13), so an active session simply is or is not
 * open. See CONTEXT.md.
 */
sealed interface SessionState {
    data object Idle : SessionState
    data class Active(
        val session: SessionEntity,
        val startedAt: Instant,
    ) : SessionState
}
