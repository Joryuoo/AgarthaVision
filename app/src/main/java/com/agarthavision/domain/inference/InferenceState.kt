package com.agarthavision.domain.inference

/**
 * Where a captured frame is in getting its model output.
 *
 * Capture no longer waits on a model: it saves the sample as [QUEUED] and the background
 * inference queue moves it on from there. A frame goes [QUEUED] → [IN_INFERENCE] → [READY], or
 * ends at [MANUAL] when there will never be a model output for it.
 *
 * **Only [READY] carries a model's answer.** A pending frame also has zero predictions, and zero
 * predictions on a [READY] frame is a real clean field. Anything that reads "no boxes" as "no
 * eggs" has to ask this first — see [isPending].
 *
 * Stored in `samples.inference_state` as [value]. Local only: Supabase never sees it, because a
 * pending sample is never verified and so never synced.
 */
enum class InferenceState(val value: String) {
    /** Waiting its turn. Nothing has been asked of any model yet. */
    QUEUED("queued"),

    /** The one frame the queue is running right now, on the cloud or on this phone. */
    IN_INFERENCE("in_inference"),

    /** A model answered. The predictions on the row are its output, possibly empty. */
    READY("ready"),

    /**
     * No model output, and there never will be: the medtech cancelled inference, both engines
     * kept failing until the retry limit, or the frame was captured before the queue existed
     * with the container unreachable.
     */
    MANUAL("manual"),
    ;

    /** True while a model output may still arrive: the frame must not be read or verified yet. */
    val isPending: Boolean
        get() = this == QUEUED || this == IN_INFERENCE

    companion object {
        fun fromValue(value: String?): InferenceState? = entries.firstOrNull { it.value == value }
    }
}
