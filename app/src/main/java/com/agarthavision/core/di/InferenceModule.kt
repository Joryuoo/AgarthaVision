package com.agarthavision.core.di

import com.agarthavision.BuildConfig
import com.agarthavision.data.inference.RemoteInferenceEngine
import com.agarthavision.data.inference.ondevice.FramePreprocessor
import com.agarthavision.data.inference.ondevice.ModelStore
import com.agarthavision.data.inference.ondevice.OnDeviceInferenceEngine
import com.agarthavision.data.inference.ondevice.OnDeviceModels
import com.agarthavision.data.inference.ondevice.YoloOutputDecoder
import com.agarthavision.data.inference.queue.WorkManagerInferenceQueue
import com.agarthavision.data.remote.InferenceApi
import com.agarthavision.data.repository.InferenceQueueRepositoryImpl
import com.agarthavision.domain.inference.CloudCircuitBreaker
import com.agarthavision.domain.inference.InferenceQueue
import com.agarthavision.domain.inference.InferenceQueueProcessor
import com.agarthavision.domain.repository.InferenceQueueRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides the [InferenceApi] backed by Retrofit + OkHttp.
 *
 * The base URL and bearer key come from [BuildConfig], which is populated from
 * `local.properties` at build time. When the container is not live, requests fail at runtime
 * and the inference queue falls back to the on-device model. See ADR-003.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    /**
     * Short, because nobody waits on it any more. Every inference call comes from the background
     * queue, and an unreachable container is answered by the on-device model; until the circuit
     * breaker opens, each dead-server frame pays this timeout once before its fallback runs.
     */
    private const val CONNECT_TIMEOUT_SECONDS = 5L

    /**
     * Above the server's own per-request timeout (25 s in `inference/server.py`), so a busy
     * server answers `503` or `504` itself rather than the phone giving up first.
     */
    private const val READ_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    /**
     * No HTTP response cache is configured here: `/infer` is POST (uncacheable), and `/health`
     * is a connectivity probe that must always hit the network — a cached response could make
     * the app falsely report "online".
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.INFERENCE_API_KEY}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.HEADERS
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideInferenceRetrofit(client: OkHttpClient): Retrofit {
        val baseUrl = BuildConfig.INFERENCE_URL.ifBlank { "https://placeholder.invalid/" }
            .let { if (it.endsWith("/")) it else "$it/" }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideInferenceApi(retrofit: Retrofit): InferenceApi =
        retrofit.create(InferenceApi::class.java)

    /**
     * The on-device engine, running the precision chosen in [OnDeviceModels.SHIPPED]. The
     * inference queue's fallback when the cloud cannot be reached.
     */
    @Provides
    @Singleton
    fun provideOnDeviceInferenceEngine(
        modelStore: ModelStore,
        preprocessor: FramePreprocessor,
        decoder: YoloOutputDecoder,
    ): OnDeviceInferenceEngine =
        OnDeviceInferenceEngine(modelStore, preprocessor, decoder, OnDeviceModels.SHIPPED)

    /**
     * The inference queue's consumer: cloud first, this phone as the fallback.
     *
     * Built here rather than injected so the two engines are passed by concrete type. Both are
     * [com.agarthavision.domain.inference.InferenceEngine]s, and naming them here is clearer than
     * a pair of qualifiers. A singleton, because WorkManager builds a new worker for every pass:
     * the circuit breaker, the retry schedule and the lock that keeps passes from overlapping
     * must be the same ones each time.
     */
    @Provides
    @Singleton
    fun provideInferenceQueueProcessor(
        repository: InferenceQueueRepository,
        cloudEngine: RemoteInferenceEngine,
        deviceEngine: OnDeviceInferenceEngine,
    ): InferenceQueueProcessor = InferenceQueueProcessor(
        repository = repository,
        cloudEngine = cloudEngine,
        deviceEngine = deviceEngine,
        circuitBreaker = CloudCircuitBreaker(),
    )
}

/**
 * Binds the inference queue and its storage.
 *
 * There is no unqualified `InferenceEngine` binding any more. Nothing outside the queue runs
 * inference, and the queue is handed both engines by type in
 * [InferenceModule.provideInferenceQueueProcessor].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceBindingModule {
    @Binds
    abstract fun bindInferenceQueue(impl: WorkManagerInferenceQueue): InferenceQueue

    @Binds
    abstract fun bindInferenceQueueRepository(impl: InferenceQueueRepositoryImpl): InferenceQueueRepository
}
