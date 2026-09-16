package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
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
 * In-memory Room tests for the tolerant `getConfirmedEggCountsForSession(:sessionId, :userId)` query.
 *
 * Rule: null caller → count detections on all visible samples; "user-a" → own + unowned only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DetectionDaoOwnerVisibilityTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var detectionDao: DetectionDao
    private lateinit var sessionDao: SessionDao
    private lateinit var sampleDao: SampleDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        detectionDao = db.detectionDao()
        sessionDao = db.sessionDao()
        sampleDao = db.sampleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Seeds one session containing three samples (owner a, owner b, unowned), each with one
     * confirmed detection. Returns the session id used.
     */
    private suspend fun seedData(): String {
        val sessionId = "session-1"
        sessionDao.insertSession(
            SessionEntity(
                sessionId = sessionId,
                userId = "user-a",
                deviceId = "device-1",
                startedAt = 1_000L,
                endedAt = null,
                notes = null,
            ),
        )

        // sample owned by user-a
        sampleDao.insertSample(confirmedSample(id = "smp-a",       sessionId = sessionId, userId = "user-a"))
        // sample owned by user-b
        sampleDao.insertSample(confirmedSample(id = "smp-b",       sessionId = sessionId, userId = "user-b"))
        // unowned sample
        sampleDao.insertSample(confirmedSample(id = "smp-unowned", sessionId = sessionId, userId = null))

        detectionDao.insertDetection(confirmedDetection(id = "det-a",       sampleId = "smp-a"))
        detectionDao.insertDetection(confirmedDetection(id = "det-b",       sampleId = "smp-b"))
        detectionDao.insertDetection(confirmedDetection(id = "det-unowned", sampleId = "smp-unowned"))

        return sessionId
    }

    // ─────────────────── null caller counts only unowned samples ─────────────

    @Test
    fun `getConfirmedEggCountsForSession with null userId counts only unowned samples`() =
        runTest {
            val sessionId = seedData()

            val rows = detectionDao.getConfirmedEggCountsForSession(sessionId, null)

            val total = rows.sumOf { it.eggCount }
            assertEquals(
                "a signed-out caller must not aggregate another medtech's detections",
                1,
                total,
            )
        }

    // ─────────────────── concrete caller sees own + unowned ──────────────────

    @Test
    fun `getConfirmedEggCountsForSession with user-a counts user-a and unowned detections only`() =
        runTest {
            val sessionId = seedData()

            val rows = detectionDao.getConfirmedEggCountsForSession(sessionId, "user-a")

            val total = rows.sumOf { it.eggCount }
            assertEquals(
                "user-a should count 2 detections (own + unowned), not 3",
                2,
                total,
            )
        }

    // ─────────────────── empty table edge case ────────────────────────────────

    @Test
    fun `getConfirmedEggCountsForSession returns empty list when no detections exist`() = runTest {
        val rows = detectionDao.getConfirmedEggCountsForSession("no-session", null)
        assertEquals(0, rows.size)
    }
}

// ─────────────────────────────── helpers ─────────────────────────────────────

private fun confirmedSample(id: String, sessionId: String, userId: String?) = SampleEntity(
    sampleId = id,
    sessionId = sessionId,
    userId = userId,
    deviceId = "device-1",
    timestamp = 1_500L,
    verifiedAt = 2_000L,
    imagePath = "/tmp/$id.jpg",
    storagePath = null,
    gpsLatitude = 10.0,
    gpsLongitude = 20.0,
    gpsAccuracy = 5f,
    status = "synced",
)

private fun confirmedDetection(id: String, sampleId: String) = DetectionEntity(
    detectionId = id,
    sampleId = sampleId,
    classLabel = "Ascaris",
    confidence = 0.9f,
    bboxX = 0.1f,
    bboxY = 0.2f,
    bboxW = 0.3f,
    bboxH = 0.4f,
    verdict = "confirmed",
    expertClass = null,
    verifiedByUser = true,
)
