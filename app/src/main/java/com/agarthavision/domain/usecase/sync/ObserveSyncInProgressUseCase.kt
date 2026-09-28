package com.agarthavision.domain.usecase.sync

import com.agarthavision.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Streams whether a sync pass is running right now, so a screen can hold back a zero-count
 * display until it knows the count isn't simply "not synced yet". Per `SyncScheduler.isSyncing`,
 * this stays a **cold** flow — no `stateIn`/`shareIn` wrapper here.
 */
class ObserveSyncInProgressUseCase @Inject constructor(
    private val syncScheduler: SyncScheduler,
) {
    operator fun invoke(): Flow<Boolean> =
        syncScheduler.isSyncing.distinctUntilChanged().catch { emit(false) }
}
