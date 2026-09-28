package com.agarthavision.data.inference.queue

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agarthavision.domain.inference.InferenceQueueProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * One pass of the inference queue: every frame that is due, oldest first. See
 * [WorkManagerInferenceQueue] for how passes are chained.
 *
 * If WorkManager stops a pass part-way (its ten-minute limit, or the system reclaiming the
 * process), the frame in flight is left in inference. The next pass starts by putting it back in
 * the queue, and WorkManager reschedules the stopped pass itself, so nothing is lost.
 */
@HiltWorker
class InferenceQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: InferenceQueueProcessor,
    private val queue: WorkManagerInferenceQueue,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val failure = runCatching { processor.drain() }.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure != null) {
            // Only reached when the queue itself could not be read; a single frame's failure is
            // handled inside the pass. Backs off and tries again.
            Log.e(TAG, "Inference queue pass failed", failure)
            return Result.retry()
        }
        processor.nextRetryIn()?.let(queue::scheduleRetry)
        return Result.success()
    }

    private companion object {
        const val TAG = "InferenceQueue"
    }
}
