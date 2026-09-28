package com.agarthavision.data.inference.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.agarthavision.domain.inference.InferenceQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Runs the inference queue through WorkManager, like sync (`WorkManagerSyncScheduler`).
 *
 * **Why WorkManager.** The queue used to run on a coroutine that lived as long as the process,
 * so it stopped the moment the app was swiped away or killed, and waiting frames sat until the
 * next launch. A WorkManager request outlives the process: Android runs it again when it next
 * allows the app background work, and after a reboot. While the app is open a request with no
 * constraints starts at once, so a capture is picked up as quickly as before.
 *
 * **It will not always run in the background on this fleet.** MIUI withholds background work
 * from third-party apps until "Autostart" is turned on, the same limit sync lives with. Nothing
 * is lost when it does not run: the queue is the sample rows in Room, on the phone, and the next
 * capture or app start carries on from them.
 *
 * **One consumer.** Every capture appends an [InferenceQueueWorker] to one unique chain, so
 * passes run one after another, never side by side. APPEND_OR_REPLACE rather than KEEP: with
 * KEEP, a capture landing just as a pass finished its last read would be dropped until the next
 * capture. Each pass re-reads the whole queue, so the extra links a burst of captures leaves are
 * cheap passes that find nothing to do. A failed link starts a fresh chain instead of failing
 * every link after it.
 *
 * **Retries are a separate unique request**, [InferenceRetryWorker], delayed until the earliest
 * failed frame is due. Delaying a link in the main chain would hold every later capture behind
 * it. The retry worker does no inference itself: it appends a pass to the main chain.
 *
 * No network constraint, because the on-device model needs none. Not expedited, for the reason
 * given in `WorkManagerSyncScheduler`: below API 31 that would need a foreground service.
 *
 * Touches WorkManager only when called, never while Hilt is building it; see
 * `WorkManagerSyncScheduler.isSyncing` for what an early `getInstance` does at app start.
 */
@Singleton
class WorkManagerInferenceQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) : InferenceQueue {

    private val workManager get() = WorkManager.getInstance(context)

    override fun start() = enqueuePass()

    override fun notifyQueued() = enqueuePass()

    /**
     * Wakes the queue again after [delay], when the earliest failed frame is due. Replaces any
     * wake-up already scheduled, since each pass computes the earliest due time afresh. A delay
     * already past runs at once.
     */
    fun scheduleRetry(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<InferenceRetryWorker>()
            .setInitialDelay(delay.inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(RETRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueuePass() {
        val request = OneTimeWorkRequestBuilder<InferenceQueueWorker>()
            // A pass that could not read the queue at all, on a database error say.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(QUEUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val QUEUE_WORK_NAME = "agartha-inference-queue"
        const val RETRY_WORK_NAME = "agartha-inference-retry"

        /** Matches the processor's first retry delay. WorkManager's floor is 10 seconds. */
        const val BACKOFF_SECONDS = 30L
    }
}
