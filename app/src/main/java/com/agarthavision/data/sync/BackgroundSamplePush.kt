package com.agarthavision.data.sync

import com.agarthavision.core.util.Logger
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes one just-saved sample to Supabase without anyone waiting on it.
 *
 * Verifying used to await this push before the sheet could close: about three seconds on good
 * Wi-Fi, measured on 2026-10-04, against well under one for the Room writes the save actually
 * depends on. Its result was already ignored — a failure only marks the row `sync_failed` — so
 * the medtech was waiting on something that changed nothing about the save.
 *
 * **Push first, then ask for a pass, in that order.** The same order the save used to run them
 * in. Asking first would start a [SyncWorker] pass pushing this very sample alongside the push
 * here. And the scheduler enqueues with `KEEP`, so a request made while a pass is already
 * running is dropped — that pass may have read its pending list before this sample was saved.
 * The direct push is what covers that gap, so it stays rather than being left to the worker.
 *
 * Process-lifetime, like [com.agarthavision.data.repository.FlaggedFrameStore]'s scope, so
 * closing the sheet no longer cancels it. A process killed mid-push leaves the row `verified`,
 * which the next foreground trigger picks up — no worse than a kill mid-save was before.
 */
@Singleton
class BackgroundSamplePush internal constructor(
    private val syncSampleUseCase: SyncSampleUseCase,
    private val syncScheduler: SyncScheduler,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        syncSampleUseCase: SyncSampleUseCase,
        syncScheduler: SyncScheduler,
    ) : this(syncSampleUseCase, syncScheduler, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    fun push(sampleId: String) {
        scope.launch {
            // Caught here: nothing is left to observe a failure, and an uncaught throw in this
            // scope would crash the app rather than surface anywhere. A row read can throw before
            // SyncSampleUseCase's own runCatching starts.
            runCatching { syncSampleUseCase(sampleId) }
                .onFailure { Logger.e(TAG, "[SyncFailed][Sample:$sampleId] Background push threw", it) }
            syncScheduler.requestSync()
        }
    }

    private companion object {
        const val TAG = "BackgroundSamplePush"
    }
}
