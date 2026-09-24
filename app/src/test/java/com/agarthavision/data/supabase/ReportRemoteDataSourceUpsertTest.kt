package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.util.MainDispatcherRule
import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pins the request [ReportRemoteDataSource.upsertReport] puts on the wire (14zcqnthrx9).
 *
 * `SyncReportUseCaseTest` fakes the data source, so it proves the use case recovers once the
 * write succeeds, but not that the write is insert-if-absent. This drives the real Supabase
 * client against a local HTTP server and checks what PostgREST would receive: a POST (an
 * insert, which `reports_insert_own` allows) carrying `on_conflict=id` and
 * `Prefer: resolution=ignore-duplicates`, so an id the server already holds is a no-op rather
 * than a primary-key conflict. A PATCH, or `merge-duplicates`, would need an UPDATE policy the
 * table does not have.
 */
class ReportRemoteDataSourceUpsertTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class Recorded(val method: String, val uri: String, val prefer: String, val body: String)

    private val requests: MutableList<Recorded> = Collections.synchronizedList(mutableListOf())
    private lateinit var server: HttpServer
    private lateinit var client: SupabaseClient

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += Recorded(
                    method = exchange.requestMethod,
                    uri = exchange.requestURI.toString(),
                    prefer = exchange.requestHeaders["Prefer"].orEmpty().joinToString(","),
                    body = exchange.requestBody.readBytes().decodeToString(),
                )
                exchange.sendResponseHeaders(HTTP_CREATED, NO_BODY)
                exchange.close()
            }
            start()
        }
        client = createSupabaseClient(
            supabaseUrl = "http://127.0.0.1:${server.address.port}",
            supabaseKey = "test-anon-key",
        ) {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
            }
            install(Postgrest)
        }
        runBlocking {
            client.auth.importSession(
                UserSession(
                    accessToken = "test-access-token",
                    refreshToken = "test-refresh-token",
                    expiresIn = SESSION_LIFETIME_SECONDS,
                    tokenType = "bearer",
                    user = UserInfo(aud = "authenticated", id = "user-1"),
                ),
                autoRefresh = false,
            )
        }
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `the report row is written insert-if-absent on its id`() = runBlocking {
        ReportRemoteDataSource(client, Gson()).upsertReport(entity("report-1"))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.uri, request.uri.startsWith("/rest/v1/reports?"))
        assertTrue(request.uri, "on_conflict=id" in request.uri)
        assertTrue(request.prefer, "resolution=ignore-duplicates" in request.prefer)
        assertTrue(request.body, "\"id\":\"report-1\"" in request.body)
        assertTrue(request.body, "\"user_id\":\"user-1\"" in request.body)
    }

    @Test
    fun `a retry sends the same insert-if-absent write, never an update`() = runBlocking {
        val remote = ReportRemoteDataSource(client, Gson())

        remote.upsertReport(entity("report-2"))
        remote.upsertReport(entity("report-2"))

        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("POST", request.method)
            assertTrue(request.prefer, "resolution=ignore-duplicates" in request.prefer)
            assertTrue(request.prefer, "merge-duplicates" !in request.prefer)
        }
    }

    private fun entity(id: String) = ReportEntity(
        reportId = id,
        sessionId = "session-1",
        userId = "user-1",
        reportType = "session",
        generatedAt = GENERATED_AT,
        totalSamples = 1,
        totalEggsConfirmed = 0,
        positiveSpeciesJson = "[]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        supabaseStatus = ReportSyncStatus.PENDING.value,
        createdAt = GENERATED_AT,
    )

    private companion object {
        const val HTTP_CREATED = 201
        const val NO_BODY = -1L
        const val SESSION_LIFETIME_SECONDS = 3600L
        const val GENERATED_AT = 1_790_000_000_000L
    }
}
