package com.agarthavision.data.inference.queue

import android.util.Log
import com.agarthavision.domain.inference.InferenceQueue
import com.agarthavision.domain.inference.InferenceQueueProcessor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the [InferenceQueueProcessor] on one app-scoped coroutine for the life of the process.
 *
 * **In-process, not WorkManager, deliberately.** The queue has to have exactly one consumer and
 * has to react to a capture within milliseconds. A coroutine that lives as long as the process
 * gives both for free. WorkManager would add scheduling latency on every capture, and would need
 * a chain of unique work to avoid dropping a capture that lands while the previous run is
 * finishing. It would also buy little on this fleet: MIUI withholds background work from
 * third-party apps by default (see `WorkManagerSyncScheduler`), and a model output is only
 * useful once the medtech opens the app to verify.
 *
 * **Durability comes from Room, not from this class.** A queued frame is a sample row with
 * `inference_state = 'queued'`. If the process dies, the rows stay put. The next app start
 * calls [start], the first pass puts any interrupted frame back in the queue, and the queue
 * carries on in capture order. The same holds after a reboot.
 *
 * A `@Singleton` because it owns the one consumer. See C5.
 */
@Singleton
class InProcessInferenceQueue @Inject constructor(
    private val processor: InferenceQueueProcessor,
) : InferenceQueue {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            // The processor catches everything but cancellation, so this is only reached by an
            // Error. Logged so a dead queue shows up in logcat instead of as frames that wait
            // forever.
            Log.e(TAG, "Inference queue stopped", throwable)
        },
    )

    /**
     * Conflated: any number of captures during a pass leave one pending wake-up, which is all
     * the consumer needs, since each pass re-reads the whole queue.
     */
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    private val started = AtomicBoolean(false)

    override fun start() {
        if (started.compareAndSet(false, true)) {
            scope.launch { processor.run(wakeUps) }
        }
    }

    override fun notifyQueued() {
        start()
        wakeUps.trySend(Unit)
    }

    private companion object {
        const val TAG = "InferenceQueue"
    }
}
