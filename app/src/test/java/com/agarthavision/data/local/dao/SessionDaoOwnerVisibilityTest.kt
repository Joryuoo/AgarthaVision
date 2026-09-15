package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for the tolerant `observeAllSessions(:userId)` query.
 *
 * Rule: null caller (signed out) → see only the unowned row; "user-a" caller → see own + unowned only.
 * A signed-out shared phone must never list another medtech's sessions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionDaoOwnerVisibilityTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedThreeSessions() {
        dao.insertSession(sessionEntity(id = "s-a",        userId = "user-a"))
        dao.insertSession(sessionEntity(id = "s-b",        userId = "user-b"))
        dao.insertSession(sessionEntity(id = "s-unowned",  userId = null))
    }

    // ─────────────────── null caller sees only unowned rows ──────────────────

    @Test
    fun `observeAllSessions with null userId returns only the unowned session`() = runTest {
        seedThreeSessions()

        val result = dao.observeAllSessions(null).first()

        assertEquals(listOf<String?>(null), result.map { it.userId })
    }

    // ─────────────────── concrete caller sees own + unowned only ─────────────

    @Test
    fun `observeAllSessions with user-a returns user-a and unowned sessions`() = runTest {
        seedThreeSessions()

        val result = dao.observeAllSessions("user-a").first()

        val ids = result.map { it.sessionId }.toSet()
        assertEquals(
            "user-a should see s-a and s-unowned, not s-b",
            setOf("s-a", "s-unowned"),
            ids,
        )
    }

    // ─────────────────── concrete caller does NOT see another user ────────────

    @Test
    fun `observeAllSessions with user-b does not include user-a sessions`() = runTest {
        seedThreeSessions()

        val result = dao.observeAllSessions("user-b").first()

        assertTrue(result.none { it.sessionId == "s-a" })
    }

    // ─────────────────── empty table edge case ────────────────────────────────

    @Test
    fun `observeAllSessions returns empty list when table is empty`() = runTest {
        val result = dao.observeAllSessions("user-a").first()
        assertTrue(result.isEmpty())
    }
}

private fun sessionEntity(id: String, userId: String?) = SessionEntity(
    sessionId = id,
    userId = userId,
    deviceId = "device-1",
    startedAt = 1_000L,
    endedAt = null,
    notes = null,
    label = null,
)
