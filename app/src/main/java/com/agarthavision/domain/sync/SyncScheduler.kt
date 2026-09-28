package com.agarthavision.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Asks for a sync pass without caring who runs it.
 *
 * Pure Kotlin, no Android and no WorkManager (C2): the implementation
 * (`data/sync/WorkManagerSyncScheduler`) is what knows about constraints and backoff, so a
 * use case can ask for a sync without the domain layer taking a dependency on the scheduler
 * it happens to be built on.
 *
 * **Requesting a sync is cheap, idempotent and fire-and-forget.** The work is enqueued as
 * unique work that keeps whatever is already scheduled, so calling this after every local
 * write costs nothing when a pass is already pending. Callers do not await it and do not get
 * a result — a write must not fail because the network did.
 */
interface SyncScheduler {
    /**
     * Requests a push-then-pull pass, to run when the device has a network.
     *
     * Safe to call while offline, unauthenticated, or during another pass. Nothing here
     * blocks, and nothing here throws.
     */
    fun requestSync()

    /**
     * Whether a pass is running right now.
     *
     * Derived from the scheduler rather than tracked by a caller, which is what makes it
     * survive the screen that asked for the sync going away, and the process with it.
     */
    val isSyncing: Flow<Boolean>
}
