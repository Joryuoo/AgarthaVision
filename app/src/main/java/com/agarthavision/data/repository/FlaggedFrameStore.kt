package com.agarthavision.data.repository

import com.agarthavision.domain.model.FlaggedFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store for frames flagged by inference during a recording session.
 *
 * This provides the source of truth for the [CaptureScreen] detection toasts and the
 * [VerificationSheet] queue. See docs/03_MOBILE_APP_PLAN.md §1.4.
 */
@Singleton
class FlaggedFrameStore @Inject constructor() {

    private val _state = MutableStateFlow<List<FlaggedFrame>>(emptyList())

    /**
     * Observable stream of flagged frames, sorted by [FlaggedFrame.capturedAt] descending.
     */
    val state: StateFlow<List<FlaggedFrame>> = _state.asStateFlow()

    /**
     * Adds a new flagged frame to the top of the stack.
     */
    fun add(frame: FlaggedFrame) {
        _state.update { listOf(frame) + it }
    }

    /**
     * Removes a frame from the store (usually after verification or rejection).
     */
    fun remove(frame: FlaggedFrame) {
        _state.update { current -> current.filter { it != frame } }
    }

    /**
     * Clears all frames from memory (e.g. on logout or session start).
     */
    fun clear() {
        _state.value = emptyList()
    }
}
