package com.agarthavision.domain.usecase.inference

import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Retrofit transport errors to [InferenceConnectionException].
 *
 * Rules:
 * - [IOException] → [InferenceConnectionException] (network unreachable, timeout, etc.)
 * - [HttpException] with status ≥ 500 → [InferenceConnectionException] (server-side failure)
 * - [HttpException] with status < 500 → re-thrown as-is (API contract error, not connectivity)
 * - Any other [Throwable] → re-thrown as-is
 *
 * Usage in [InferFrameUseCase]:
 * ```kotlin
 * val result = mapper.execute { api.infer(body) }
 * ```
 *
 * See docs/03_MOBILE_APP_PLAN.md §1.9.
 */
@Singleton
class NetworkErrorMapper @Inject constructor() {

    suspend fun <T> execute(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            throw InferenceConnectionException(e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw InferenceConnectionException(e) else throw e
        }
    }
}
