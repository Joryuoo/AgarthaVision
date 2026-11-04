package com.agarthavision.data.supabase

import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.util.MainDispatcherRule
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Drives [PatientRemoteDataSource.upsertPatient] through the real Supabase client against a
 * local HTTP server answering the way PostgREST does (14zcqntr43e).
 *
 * Every sync test mocks the data source whole, so the insert-then-update fallback had never
 * run under test - and it never fired on the device either. The 409 bodies below are what
 * PostgREST returns for each SQLSTATE; supabase-kt keeps the message and status from them
 * and drops the `code`, which is the trap the earlier version fell into.
 */
class PatientRemoteDataSourceUpsertTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class Recorded(val method: String, val uri: String, val prefer: String, val body: String)

    private data class Reply(val status: Int, val body: String?)

    private val requests: MutableList<Recorded> = Collections.synchronizedList(mutableListOf())
    private val replies: MutableMap<String, Reply> = Collections.synchronizedMap(mutableMapOf())
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
                val reply = replies[exchange.requestMethod] ?: Reply(HTTP_CREATED, null)
                if (reply.body == null) {
                    exchange.sendResponseHeaders(reply.status, NO_BODY)
                } else {
                    val bytes = reply.body.encodeToByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
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
    fun `a new patient is a single insert`() = runBlocking {
        remote().upsertPatient(entity())

        val request = requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.uri, request.uri.startsWith("/rest/v1/patients"))
        assertTrue(request.body, "\"id\":\"$PATIENT_ID\"" in request.body)
    }

    @Test
    fun `a patient the server already holds falls through to an update of that row`() = runBlocking {
        replies["POST"] = Reply(HTTP_CONFLICT, DUPLICATE_KEY_BODY)
        replies["PATCH"] = Reply(HTTP_OK, "[${patientJson()}]")

        remote().upsertPatient(entity())

        assertEquals(listOf("POST", "PATCH"), requests.map { it.method })
        val update = requests[1]
        assertTrue(update.uri, update.uri.startsWith("/rest/v1/patients?"))
        assertTrue(update.uri, "id=eq.$PATIENT_ID" in update.uri)
        assertTrue(update.prefer, "return=representation" in update.prefer)
        assertTrue(update.body, "\"lastname\":\"Dela Cruz\"" in update.body)
    }

    @Test
    fun `a conflict whose update matches no row rethrows the insert error`() = runBlocking {
        // A foreign-key violation is also a 409. The row is not there, so the update
        // touches nothing - that must not read as success.
        replies["POST"] = Reply(HTTP_CONFLICT, FOREIGN_KEY_BODY)
        replies["PATCH"] = Reply(HTTP_OK, "[]")

        val error = expectRestException { remote().upsertPatient(entity()) }

        assertEquals(listOf("POST", "PATCH"), requests.map { it.method })
        assertTrue(error.message, "foreign key constraint" in error.message.orEmpty())
    }

    @Test
    fun `an error that is not a conflict is rethrown without trying an update`() = runBlocking {
        replies["POST"] = Reply(HTTP_FORBIDDEN, RLS_BODY)

        val error = expectRestException { remote().upsertPatient(entity()) }

        assertEquals(listOf("POST"), requests.map { it.method })
        assertEquals(HTTP_FORBIDDEN, error.statusCode)
    }

    private fun remote() = PatientRemoteDataSource(dagger.Lazy { client })

    private suspend fun expectRestException(block: suspend () -> Unit): RestException {
        try {
            block()
        } catch (error: RestException) {
            return error
        }
        fail("Expected a RestException")
        error("unreachable")
    }

    private fun entity() = PatientEntity(
        patientId = PATIENT_ID,
        lastname = "Dela Cruz",
        firstname = "Juan",
        sex = "M",
        birthdate = BIRTHDATE,
        psgcBarangayCode = "0722217001",
        createdBy = "user-1",
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    private fun patientJson() =
        """{"id":"$PATIENT_ID","lastname":"Dela Cruz","firstname":"Juan","middle_name":null,""" +
            """"sex":"M","birthdate":"1990-01-01","psgc_barangay_code":"0722217001",""" +
            """"created_by":"user-1","created_at":"2026-10-29T00:00:00Z","updated_at":"2026-10-29T00:00:00Z"}"""

    private companion object {
        const val PATIENT_ID = "a8ed0b34-f641-41d9-8342-51da753081a9"
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
        const val NO_BODY = -1L
        const val SESSION_LIFETIME_SECONDS = 3600L
        const val BIRTHDATE = 631_152_000_000L
        const val CREATED_AT = 1_793_232_000_000L

        const val DUPLICATE_KEY_BODY =
            """{"code":"23505","details":"Key (id)=(a8ed0b34-f641-41d9-8342-51da753081a9) already exists.",""" +
                """"hint":null,"message":"duplicate key value violates unique constraint \"patients_pkey\""}"""
        const val FOREIGN_KEY_BODY =
            """{"code":"23503","details":"Key (created_by)=(user-1) is not present in table \"profiles\".",""" +
                """"hint":null,"message":"insert or update on table \"patients\" violates foreign key constraint""" +
                """ \"patients_created_by_fkey\""}"""
        const val RLS_BODY =
            """{"code":"42501","details":null,"hint":null,""" +
                """"message":"new row violates row-level security policy for table \"patients\""}"""
    }
}
