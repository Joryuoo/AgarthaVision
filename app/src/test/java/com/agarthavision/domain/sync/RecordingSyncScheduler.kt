package com.agarthavision.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test double that counts sync requests instead of enqueueing work.
 *
 * Shared rather than re-declared per test, because the thing worth asserting is the same
 * everywhere: a local write asks for a pass, and asks for exactly one.
 */
internal class RecordingSyncScheduler : SyncScheduler {
    var requests: Int = 0
        private set

    override fun requestSync() {
        requests += 1
    }

    override val isSyncing: Flow<Boolean> = MutableStateFlow(false)
}
