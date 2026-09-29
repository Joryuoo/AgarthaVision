package com.agarthavision.data.inference.queue

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agarthavision.domain.inference.InferenceQueue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires when the earliest failed frame is due to be tried again, and appends a pass to the
 * queue's chain. It runs no inference itself, so the chain stays the queue's only consumer. See
 * [WorkManagerInferenceQueue].
 */
@HiltWorker
class InferenceRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val queue: InferenceQueue,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        queue.notifyQueued()
        return Result.success()
    }
}
