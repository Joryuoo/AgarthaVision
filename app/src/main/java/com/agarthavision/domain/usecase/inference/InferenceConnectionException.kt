package com.agarthavision.domain.usecase.inference

/**
 * Thrown when the inference container is unreachable.
 *
 * Raised by [NetworkErrorMapper] from a Retrofit [java.io.IOException] or an
 * [retrofit2.HttpException] with status ≥ 500. 4xx errors are not wrapped here —
 * they indicate an API contract issue, not a connectivity failure.
 *
 * Supabase connectivity failures are a separate concern and do NOT produce this
 * exception. See docs/03_MOBILE_APP_PLAN.md §1.9.
 */
class InferenceConnectionException(cause: Throwable? = null) :
    Exception("Inference container unreachable", cause)
