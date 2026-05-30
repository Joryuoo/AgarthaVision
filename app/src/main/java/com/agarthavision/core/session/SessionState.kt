package com.agarthavision.core.session

import com.agarthavision.data.local.entity.SessionEntity
import java.time.Instant

/**
 * The app-level capture session state. Per ADR-005, an active session = one
 * fecal smear. [isInferenceRunning] toggles when Capture foregrounds/backgrounds
 * or when a sheet/picker/reports overlay opens — paused state keeps the smear
 * alive without burning the inference server. See docs/03_MOBILE_APP_PLAN.md §1.1.
 */
sealed interface SessionState {
    data object Idle : SessionState
    data class Active(
        val session: SessionEntity,
        val startedAt: Instant,
        val isInferenceRunning: Boolean = true,
    ) : SessionState
}
