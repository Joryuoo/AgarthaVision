package com.agarthavision.core.di

// TODO: Uncomment when InferenceApi is defined in data/remote/api/
//
// import com.agarthavision.BuildConfig
// import com.agarthavision.data.remote.api.InferenceApi
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.components.SingletonComponent
// import okhttp3.OkHttpClient
// import okhttp3.logging.HttpLoggingInterceptor
// import okhttp3.logging.HttpLoggingInterceptor.Level.BODY
// import retrofit2.Retrofit
// import retrofit2.converter.gson.GsonConverterFactory
// import java.util.concurrent.TimeUnit
// import javax.inject.Singleton
//
// @Module
// @InstallIn(SingletonComponent::class)
// object NetworkModule {
//     @Provides @Singleton
//     fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
//         .addInterceptor(HttpLoggingInterceptor().apply { level = BODY })
//         .connectTimeout(30, TimeUnit.SECONDS)
//         .readTimeout(60, TimeUnit.SECONDS)
//         .build()
//
//     @Provides @Singleton
//     fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
//         .baseUrl(BuildConfig.API_BASE_URL)
//         .client(client)
//         .addConverterFactory(GsonConverterFactory.create())
//         .build()
//
//     @Provides fun provideInferenceApi(retrofit: Retrofit): InferenceApi =
//         retrofit.create(InferenceApi::class.java)
// }
