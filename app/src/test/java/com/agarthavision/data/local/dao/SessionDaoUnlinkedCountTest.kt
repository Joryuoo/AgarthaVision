package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for [SessionDao.observeUnlinkedCount].
 *
 * Rule: COUNT(*) WHERE user_id IS NULL AND claim_exempt = 0.
 *  - owned sessions are excluded
 *  - claim-exempt (user_id IS NULL, claimExempt = true) sessions are excluded
 *  - unowned non-exempt sessions are counted
 *  - empty table → 0
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionDaoUnlinkedCountTest {

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

    // ── empty table ──────────────────────────────────────────────────────────

    @Test
    fun `observeUnlinkedCount returns 0 for empty table`() = runTest {
        val count = dao.observeUnlinkedCount().first()
        assertEquals(0, count)
    }

    // ── mixed seed ───────────────────────────────────────────────────────────

    @Test
    fun `observeUnlinkedCount counts only unowned non-exempt sessions`() = runTest {
        // owned — must NOT count
        dao.insertSession(unlinkedCountSessionEntity(id = "s-owned", userId = "user-a", claimExempt = false))
        // unowned, non-exempt — MUST count
        dao.insertSession(unlinkedCountSessionEntity(id = "s-unowned", userId = null, claimExempt = false))
        // unowned, exempt — must NOT count
        dao.insertSession(unlinkedCountSessionEntity(id = "s-exempt", userId = null, claimExempt = true))

        val count = dao.observeUnlinkedCount().first()

        assertEquals("only the unowned non-exempt session should be counted", 1, count)
    }

    // ── only owned rows ──────────────────────────────────────────────────────

    @Test
    fun `observeUnlinkedCount returns 0 when all sessions are owned`() = runTest {
        dao.insertSession(unlinkedCountSessionEntity(id = "s1", userId = "user-a"))
        dao.insertSession(unlinkedCountSessionEntity(id = "s2", userId = "user-b"))

        val count = dao.observeUnlinkedCount().first()
        assertEquals(0, count)
    }

    // ── only exempt rows ─────────────────────────────────────────────────────

    @Test
    fun `observeUnlinkedCount returns 0 when all unowned sessions are claim-exempt`() = runTest {
        dao.insertSession(unlinkedCountSessionEntity(id = "e1", userId = null, claimExempt = true))
        dao.insertSession(unlinkedCountSessionEntity(id = "e2", userId = null, claimExempt = true))

        val count = dao.observeUnlinkedCount().first()
        assertEquals(0, count)
    }

    // ── multiple unlinked ────────────────────────────────────────────────────

    @Test
    fun `observeUnlinkedCount returns correct count for multiple unlinked sessions`() = runTest {
        dao.insertSession(unlinkedCountSessionEntity(id = "u1", userId = null, claimExempt = false))
        dao.insertSession(unlinkedCountSessionEntity(id = "u2", userId = null, claimExempt = false))
        dao.insertSession(unlinkedCountSessionEntity(id = "u3", userId = null, claimExempt = false))
        // exempt one should be ignored
        dao.insertSession(unlinkedCountSessionEntity(id = "u4", userId = null, claimExempt = true))

        val count = dao.observeUnlinkedCount().first()
        assertEquals(3, count)
    }
}

private fun unlinkedCountSessionEntity(
    id: String,
    userId: String?,
    claimExempt: Boolean = false,
) = SessionEntity(
    sessionId = id,
    userId = userId,
    deviceId = "device-1",
    startedAt = 1_000L,
    endedAt = null,
    notes = null,
    label = null,
    claimExempt = claimExempt,
)
