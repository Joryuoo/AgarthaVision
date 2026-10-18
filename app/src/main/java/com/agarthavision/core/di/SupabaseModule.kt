package com.agarthavision.core.di

import com.agarthavision.BuildConfig
import com.agarthavision.domain.model.HumanCheckConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Provides the app-wide [SupabaseClient] with Auth, Postgrest, and Storage plugins
 * installed. URL + anon key are baked into [BuildConfig] at build time from
 * `local.properties`. See CONTEXT.md and schema.ts
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            requestTimeout = HTTP_TIMEOUT
            install(Auth)
            install(Postgrest)
            install(Storage)
        }

    /** The sign-in human check's public site key and hostname, from `local.properties`. */
    @Provides
    fun provideHumanCheckConfig(): HumanCheckConfig =
        HumanCheckConfig(siteKey = BuildConfig.TURNSTILE_SITE_KEY, hostname = BuildConfig.TURNSTILE_HOSTNAME)

    private val HTTP_TIMEOUT = 30.seconds
}
