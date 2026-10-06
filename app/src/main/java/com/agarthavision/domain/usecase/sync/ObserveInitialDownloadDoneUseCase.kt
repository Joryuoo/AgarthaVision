package com.agarthavision.domain.usecase.sync

import com.agarthavision.core.sync.InitialFetchStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Streams whether the first full download of a user's records has completed, so a screen can tell
 * "nothing here yet" apart from "still downloading". Cold flow, same contract as
 * [ObserveSyncInProgressUseCase]. On a read error it reports `true` (done) so a storage fault never
 * hides the normal empty state behind a download message that would never clear.
 */
class ObserveInitialDownloadDoneUseCase @Inject constructor(
    private val initialFetchStateStore: InitialFetchStateStore,
) {
    operator fun invoke(userId: String): Flow<Boolean> =
        initialFetchStateStore.observeCompleted(userId).distinctUntilChanged().catch { emit(true) }
}
