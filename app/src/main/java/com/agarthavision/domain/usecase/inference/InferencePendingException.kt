package com.agarthavision.domain.usecase.inference

/**
 * Thrown when a sample is submitted while its model output may still arrive.
 *
 * A pending frame has no predictions, which is also what a clean field looks like. Verifying it
 * would record "no eggs" on a frame no model has read yet, and the result that lands afterwards
 * would be refused because the sample is no longer flagged. The medtech either waits for the
 * result or cancels inference and annotates by hand.
 */
class InferencePendingException(sampleId: String) :
    IllegalStateException("Sample $sampleId is still in inference. Wait for the result or cancel it.")
