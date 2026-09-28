package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real-Room test for `SessionDao.countLabelCollisions` + upsert-with-disambiguation.
 *
 * Pins the actual behaviour that [com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase]
 * now depends on: two pulled sessions carrying the same `(patient_id, label)` are both persisted
 * with distinct labels when the collision is detected via `countLabelCollisions` before writing.
 *
 * This test exists to give genuine confidence in the pre-check path. The mock-based
 * `FetchRemoteDataUseCaseTest` correctly exercises the use-case control flow, but only this
 * test can prove that the REAL Room DAO behaves as assumed — in particular that `@Upsert` does
 * NOT throw `SQLiteConstraintException` for a new-row collision (it silently no-ops), so the
 * pre-check is the only reliable mechanism.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionLabelCollisionReconcileTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
    }

    @After
    fun tearDown() = db.close()

    // ── Pre-check accuracy ────────────────────────────────────────────────────

    @Test
    fun `countLabelCollisions returns 0 when no other session carries the label`() = runTest {
        seedPatient()
        sessionDao.upsertSession(session("sess-aaa", label = "SMEAR-1"))

        val count = sessionDao.countLabelCollisions(
            patientId = PATIENT_ID,
            label = "SMEAR-1",
            excludingSessionId = "sess-aaa", // exclude itself
        )

        assertEquals(0, count)
    }

    @Test
    fun `countLabelCollisions returns 1 when a different session already carries the label`() = runTest {
        seedPatient()
        sessionDao.upsertSession(session("sess-aaa", label = "SMEAR-1"))

        // sess-bbb is a brand-new session; it does NOT exist locally yet, so we exclude an
        // unknown id — the same scenario pullSessions faces for a new remote row.
        val count = sessionDao.countLabelCollisions(
            patientId = PATIENT_ID,
            label = "SMEAR-1",
            excludingSessionId = "sess-bbb",
        )

        assertEquals(1, count)
    }

    // ── Full reconciliation round-trip ────────────────────────────────────────

    /**
     * Mimics exactly what [com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
     * .upsertSessionReconcilingLabel] does: pre-check → disambiguate if needed → upsert.
     *
     * Asserts that BOTH sessions land in the database with distinct labels, with no exception
     * thrown — the outcome the old try/catch approach failed to guarantee for new rows.
     */
    @Test
    fun `pre-check reconciliation persists both colliding sessions with distinct labels`() = runTest {
        seedPatient()

        val collidingLabel = "SMEAR-1"
        val session1 = session("sess-aaaabbbb", label = collidingLabel)
        val session2 = session("sess-ccccdddd", label = collidingLabel)

        // Simulate upsertSessionReconcilingLabel for session1
        upsertReconcilingLabel(session1)
        // Simulate upsertSessionReconcilingLabel for session2 — at this point session1 is present
        upsertReconcilingLabel(session2)

        // Both rows must exist
        val row1 = sessionDao.getSessionById("sess-aaaabbbb")
        val row2 = sessionDao.getSessionById("sess-ccccdddd")
        assertNotNull("session1 must be persisted", row1)
        assertNotNull("session2 must be persisted", row2)

        // Labels must be distinct
        val label1 = checkNotNull(row1).label
        val label2 = checkNotNull(row2).label
        assertNotNull("session1 label must not be null", label1)
        assertNotNull("session2 label must not be null", label2)
        assertEquals(
            "session1 should keep its original label (no collision at insert time)",
            collidingLabel,
            label1,
        )
        assertEquals(
            "session2 must be disambiguated with a suffix from its own session id",
            "${collidingLabel}-${session2.sessionId.take(DISAMBIGUATION_SUFFIX_LENGTH)}".uppercase(),
            label2,
        )
    }

    /**
     * Proves that without the pre-check — i.e., calling `@Upsert` directly — the second
     * session is silently dropped. This test documents the exact failure the pre-check fixes,
     * and is the reason a try/catch on upsertSession is dead code for new-row collisions.
     */
    @Test
    fun `upsert alone silently drops second session on label collision (documents the Room bug)`() = runTest {
        seedPatient()

        val label = "SMEAR-1"
        sessionDao.upsertSession(session("sess-aaaabbbb", label = label))
        // No exception — @Upsert silently no-ops for a new PK with a colliding secondary key.
        sessionDao.upsertSession(session("sess-ccccdddd", label = label))

        val row2 = sessionDao.getSessionById("sess-ccccdddd")
        assertEquals(
            "without pre-check, the second session is silently dropped by @Upsert",
            null,
            row2,
        )
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    /**
     * Mirrors the pre-check logic in `FetchRemoteDataUseCase.upsertSessionReconcilingLabel`,
     * using the real DAO so this test exercises the same path end-to-end.
     */
    private suspend fun upsertReconcilingLabel(remote: SessionEntity) {
        val rawLabel = remote.label
        val toWrite = if (!rawLabel.isNullOrBlank() &&
            sessionDao.countLabelCollisions(remote.patientId, rawLabel, remote.sessionId) > 0
        ) {
            val disambiguated =
                "${rawLabel.trim()}-${remote.sessionId.take(DISAMBIGUATION_SUFFIX_LENGTH)}".uppercase()
            remote.copy(label = disambiguated)
        } else {
            remote
        }
        sessionDao.upsertSession(toWrite)
    }

    private suspend fun seedPatient() {
        patientDao.upsertPatient(
            PatientEntity(
                patientId = PATIENT_ID,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = USER_ID,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
    }

    private fun session(id: String, label: String? = null) = SessionEntity(
        sessionId = id,
        userId = USER_ID,
        patientId = PATIENT_ID,
        deviceId = "device-1",
        startedAt = 1_000L,
        label = label,
    )

    private companion object {
        const val PATIENT_ID = "patient-collision-test"
        const val USER_ID = "user-a"
        const val DISAMBIGUATION_SUFFIX_LENGTH = 4
    }
}
