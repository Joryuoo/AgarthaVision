package com.agarthavision.domain.inference

/**
 * Asks the background inference queue to run, without caring how it runs.
 *
 * Pure Kotlin (C2), like `SyncScheduler`: capture only needs to say "a frame is waiting", and
 * the implementation in `data/inference/queue/` decides the threading.
 *
 * Both calls are cheap, idempotent and never block. The queue itself is the rows in Room
 * (`samples.inference_state`), so nothing is lost if a call is missed. A missed call only
 * delays the frame until the next capture or app start.
 */
interface InferenceQueue {

    /**
     * Starts the queue's consumer if it is not already running. Called at app start, so frames
     * left queued by a killed process resume without waiting for a new capture.
     */
    fun start()

    /** Tells the consumer a frame was just queued. Starts it too, if it had not started. */
    fun notifyQueued()
}
