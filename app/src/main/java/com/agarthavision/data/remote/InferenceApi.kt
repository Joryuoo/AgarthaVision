package com.agarthavision.data.remote

import com.agarthavision.data.remote.dto.InferenceResponseDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface to the self-hosted inference container.
 *
 * The container exposes `POST /infer` (raw `image/jpeg` body) and returns
 * detections in [InferenceResponseDto] shape. Bearer-token auth is injected
 * by the OkHttp interceptor wired in [com.agarthavision.core.di.InferenceModule].
 *
 * See docs/04_CLOUD_BACKEND_PLAN.md §5 and ADR-003.
 */
interface InferenceApi {
    @GET("health")
    suspend fun health(): Response<Unit>

    @POST("infer")
    suspend fun infer(@Body image: RequestBody): Response<InferenceResponseDto>
}
