package com.agarthavision.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.FetchSummary
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.usecase.sync.SyncSummary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One sync pass — push everything pending, then pull what the server has.
 *
 * Push before pull, the same order every foreground trigger already uses: a pull that
 * overwrote a row still waiting to go up would lose the medtech's work.
 *
 * Both halves run even if the first reports a failure. [SyncPendingDataUseCase] already
 * tolerates individual row failures and only fails the pass on an unexpected error reading
 * the queues, so a push that could not finish is not a reason to leave the device without the
 * patients the server holds.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val push = syncPendingDataUseCase()
        val fetch = fetchRemoteDataUseCase()

        push.onFailure { Log.e(TAG, "Sync push failed", it) }
        fetch.onFailure { Log.e(TAG, "Sync fetch failed", it) }

        // Skipped means unauthenticated or offline. Retrying will not change either: there is
        // no credential to acquire here, and the network constraint already held before this
        // ran. Reporting success stops WorkManager backing off against a device that is
        // simply signed out, which would then delay the pass that matters after login.
        if (push.getOrNull() is SyncSummary.Skipped) return Result.success()

        // A pull that lost some entity types reports Result.success, because each type is
        // caught individually so one bad type cannot abort the rest. That tolerance is right,
        // but it made the backoff below unreachable: every pull could fail and the pass still
        // ended clean, so nothing ever tried again until some other trigger fired. Ask for the
        // retry explicitly instead.
        val fetchIncomplete = (fetch.getOrNull() as? FetchSummary.Ran)?.isComplete == false

        // Anything else that failed is worth another attempt with backoff — a dropped
        // connection mid-push, a server that was briefly unavailable.
        return if (push.isSuccess && fetch.isSuccess && !fetchIncomplete) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
