package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.DetectionVerdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeKpiQueriesTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao
    private lateinit var sampleDao: SampleDao
    private lateinit var detectionDao: DetectionDao

    private val testUserId = "user-123"
    private val otherUserId = "user-456"
    private val patientId = "patient-1"

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
        sampleDao = db.sampleDao()
        detectionDao = db.detectionDao()

        patientDao.upsertPatient(
            PatientEntity(
                patientId = patientId,
                lastname = "Dela Cruz",
                firstname = "Juan",
                middleName = "Santos",
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = testUserId,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertSession(
        id: String,
        userId: String = testUserId,
        startedAt: Long = 1_000L,
    ) {
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = id,
                patientId = patientId,
                userId = userId,
                deviceId = "dev-1",
                startedAt = startedAt,
                label = "Smear-$id",
                supabaseStatus = "pending",
            ),
        )
    }

    @Suppress("LongParameterList")
    private suspend fun insertSample(
        id: String,
        sessionId: String,
        userId: String = testUserId,
        status: String = "verified",
        timestamp: Long = 1_000L,
        verifiedAt: Long = timestamp,
        deletedAt: Long? = null,
    ) {
        sampleDao.upsertSample(
            SampleEntity(
                sampleId = id,
                sessionId = sessionId,
                userId = userId,
                deviceId = "dev-1",
                timestamp = timestamp,
                verifiedAt = verifiedAt,
                imagePath = "/path/$id.jpg",
                status = status,
                deletedAt = deletedAt,
            ),
        )
    }

    private suspend fun insertDetection(
        id: String,
        sampleId: String,
        verdict: String,
    ) {
        detectionDao.insertDetections(
            listOf(
                DetectionEntity(
                    detectionId = id,
                    sampleId = sampleId,
                    classLabel = "Ascaris lumbricoides",
                    confidence = 0.95f,
                    bboxX = 0.1f,
                    bboxY = 0.1f,
                    bboxW = 0.2f,
                    bboxH = 0.2f,
                    verdict = verdict,
                ),
            ),
        )
    }

    // ── SessionDao.observeSessionOutcomesBetween ────────────────────────────

    @Test
    fun `session outcomes query distinguishes positive from negative and unexamined`() = runTest {
        insertSession("sess-pos", startedAt = 1_000L)
        insertSample("s-pos", sessionId = "sess-pos", status = "verified")
        insertDetection("d-pos", sampleId = "s-pos", verdict = DetectionVerdict.CONFIRMED.value)

        insertSession("sess-fp-only", startedAt = 1_000L)
        insertSample("s-fp", sessionId = "sess-fp-only", status = "verified")
        insertDetection("d-fp", sampleId = "s-fp", verdict = DetectionVerdict.FALSE_POSITIVE.value)

        insertSession("sess-flagged-only", startedAt = 1_000L)
        insertSample("s-flagged", sessionId = "sess-flagged-only", status = "flagged")

        insertSession("sess-empty", startedAt = 1_000L)

        val outcomes = sessionDao.observeSessionOutcomesBetween(testUserId, 500L, 1_500L).first()
        val byId = outcomes.associateBy { it.sessionId }

        assertTrue(byId["sess-pos"]!!.examined)
        assertTrue(byId["sess-pos"]!!.positive)

        assertTrue(byId["sess-fp-only"]!!.examined)
        assertFalse(byId["sess-fp-only"]!!.positive)

        assertFalse(byId["sess-flagged-only"]!!.examined)
        assertFalse(byId["sess-flagged-only"]!!.positive)

        assertFalse(byId["sess-empty"]!!.examined)
        assertFalse(byId["sess-empty"]!!.positive)
    }

    @Test
    fun `session outcomes query ignores tombstoned samples`() = runTest {
        insertSession("sess-tombstone", startedAt = 1_000L)
        insertSample("s-tomb", sessionId = "sess-tombstone", status = "verified", deletedAt = 1_200L)
        insertDetection("d-tomb", sampleId = "s-tomb", verdict = DetectionVerdict.CONFIRMED.value)

        val outcomes = sessionDao.observeSessionOutcomesBetween(testUserId, 500L, 1_500L).first()
        val row = outcomes.single()

        assertFalse(row.examined)
        assertFalse(row.positive)
    }

    @Test
    fun `session outcomes query respects window boundary and user isolation`() = runTest {
        insertSession("sess-before", startedAt = 999L)
        insertSession("sess-at-start", startedAt = 1_000L)
        insertSession("sess-at-end", startedAt = 2_000L)
        insertSession("sess-other-user", userId = otherUserId, startedAt = 1_500L)

        val outcomes = sessionDao.observeSessionOutcomesBetween(testUserId, 1_000L, 2_000L).first()
        assertEquals(listOf("sess-at-start"), outcomes.map { it.sessionId })
    }

    // ── SampleDao.observeSampleTimesBetween ─────────────────────────────────

    @Test
    fun `sample times query includes captured or verified within window and excludes tombstones`() = runTest {
        insertSession("sess-1")

        // Captured in window, verified later
        insertSample("s-captured", sessionId = "sess-1", timestamp = 1_200L, verifiedAt = 5_000L)
        // Captured earlier, verified in window
        insertSample("s-verified", sessionId = "sess-1", timestamp = 500L, verifiedAt = 1_400L)
        // Outside window entirely
        insertSample("s-outside", sessionId = "sess-1", timestamp = 3_000L, verifiedAt = 3_000L)
        // Inside window but tombstoned
        insertSample("s-deleted", sessionId = "sess-1", timestamp = 1_200L, deletedAt = 1_300L)
        // Other user
        insertSample("s-other", sessionId = "sess-1", userId = otherUserId, timestamp = 1_200L)

        val times = sampleDao.observeSampleTimesBetween(testUserId, 1_000L, 2_000L).first()
        val sampleIds = times.map { it.sampleId }.toSet()

        assertEquals(setOf("s-captured", "s-verified"), sampleIds)
    }

    // ── DetectionDao.observeRulingsBetween ──────────────────────────────────

    @Test
    fun `rulings query returns prediction rulings with detectionsInSample count`() = runTest {
        insertSession("sess-1")
        insertSample("s-1", sessionId = "sess-1", verifiedAt = 1_200L, status = "verified")
        insertDetection("d-1", sampleId = "s-1", verdict = DetectionVerdict.CONFIRMED.value)
        insertDetection("d-2", sampleId = "s-1", verdict = DetectionVerdict.WRONG_CLASS.value)

        val rulings = detectionDao.observeRulingsBetween(testUserId, 1_000L, 2_000L).first()
        assertEquals(2, rulings.size)
        assertEquals(1_200L, rulings[0].verifiedAt)
        assertEquals(2, rulings[0].detectionsInSample)
        assertEquals(2, rulings[1].detectionsInSample)
    }

    @Test
    fun `rulings query excludes flagged samples tombstones and other users`() = runTest {
        insertSession("sess-1")
        insertSample("s-flagged", sessionId = "sess-1", verifiedAt = 1_200L, status = "flagged")
        insertDetection("d-f", sampleId = "s-flagged", verdict = DetectionVerdict.CONFIRMED.value)

        insertSample("s-del", sessionId = "sess-1", verifiedAt = 1_200L, status = "verified", deletedAt = 1_300L)
        insertDetection("d-del", sampleId = "s-del", verdict = DetectionVerdict.CONFIRMED.value)

        insertSample("s-other", sessionId = "sess-1", userId = otherUserId, verifiedAt = 1_200L)
        insertDetection("d-other", sampleId = "s-other", verdict = DetectionVerdict.CONFIRMED.value)

        val rulings = detectionDao.observeRulingsBetween(testUserId, 1_000L, 2_000L).first()
        assertTrue(rulings.isEmpty())
    }
}
