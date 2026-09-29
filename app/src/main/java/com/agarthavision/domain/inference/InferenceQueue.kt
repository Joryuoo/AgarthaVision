package com.agarthavision.domain.inference

/**
 * Asks the background inference queue to run, without caring how it runs.
 *
 * Pure Kotlin (C2), like `SyncScheduler`: capture only needs to say "a frame is waiting", and
 * the implementation in `data/inference/queue/` decides how and when it runs.
 *
 * Both calls are cheap and never block, and calling either twice is harmless: a pass that
 * finds nothing due does nothing. The queue itself is the rows in Room
 * (`samples.inference_state`), so nothing is lost if a call is missed. A missed call only
 * delays the frame until the next capture or app start.
 */
interface InferenceQueue {

    /**
     * Asks for a pass over whatever is waiting. Called at app start, so frames left queued by a
     * killed process resume without waiting for a new capture.
     */
    fun start()

    /** Tells the queue a frame was just queued, so it runs a pass for it. */
    fun notifyQueued()
}
