package com.agarthavision.core.di

import com.agarthavision.BuildConfig
import com.agarthavision.data.remote.InferenceApi
import com.google.gson.Gson
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
 * `local.properties` at build time. Until DMKuZu's container is live, requests
 * will fail at runtime — which is fine, because `FrameSampler` and
 * `InferFrameUseCase` are built around the response *shape*, not against a live
 * server. See ADR-003.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

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
}
