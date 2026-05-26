package com.agarthavision.core.di

import com.agarthavision.util.MainDispatcherRule
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Catches transitive-dependency and serializer-wiring failures in the Supabase SDK
 * at JVM-test time, without needing to install the APK on a device.
 *
 * If kotlinx-datetime, ktor, or kotlinx-serialization is missing from the runtime
 * classpath, [createSupabaseClient] will throw `NoClassDefFoundError` during plugin
 * initialization and this test fails — the same root cause as the on-device
 * `Failed resolution of: kotlinx/datetime/serializers/InstantIso8601Serializer` crash.
 *
 * Uses [MemorySessionManager] instead of the default `SettingsSessionManager` because
 * the latter requires Android's SharedPreferences which isn't available on JVM.
 */
class SupabaseClientSmokeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `SupabaseClient with Auth plugin can be constructed`() {
        Class.forName("kotlinx.datetime.serializers.InstantIso8601Serializer")
        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-anon-key",
        ) {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        assertNotNull(client)
    }
}
