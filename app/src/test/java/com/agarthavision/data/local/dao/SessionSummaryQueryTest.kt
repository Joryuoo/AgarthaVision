package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.SessionListFilter
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
class SessionSummaryQueryTest {

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

    private suspend fun insertSession(id: String, userId: String = testUserId, startedAt: Long = 1_000L) {
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

    private suspend fun insertSample(
        id: String,
        sessionId: String,
        status: String = "verified",
        timestamp: Long = 1_000L,
        deletedAt: Long? = null,
    ) {
        sampleDao.upsertSample(
            SampleEntity(
                sampleId = id,
                sessionId = sessionId,
                userId = testUserId,
                deviceId = "dev-1",
                timestamp = timestamp,
                verifiedAt = timestamp,
                imagePath = "/path/$id.jpg",
                status = status,
                deletedAt = deletedAt,
            ),
        )
    }

    private suspend fun insertDetection(
        id: String,
        sampleId: String,
        verdict: String = DetectionVerdict.CONFIRMED.value,
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

    @Test
    fun `user isolation - returns only sessions belonging to user`() = runTest {
        insertSession("s-mine", userId = testUserId)
        insertSession("s-other", userId = otherUserId)

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.ALL.sql,
            fromMillis = null,
            toMillis = null,
            limit = 10,
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("s-mine", summaries.first().session.sessionId)
    }

    @Test
    fun `filter ALL - returns all sessions in time window`() = runTest {
        insertSession("s-in", startedAt = 5_000L)
        insertSession("s-out-early", startedAt = 1_000L)
        insertSession("s-out-late", startedAt = 10_000L)

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.ALL.sql,
            fromMillis = 4_000L,
            toMillis = 9_000L,
            limit = 10,
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("s-in", summaries.first().session.sessionId)
    }

    @Test
    fun `filter NO_FRAMES - returns sessions without live samples`() = runTest {
        insertSession("s-empty")
        insertSession("s-with-samples")
        insertSample("samp-1", "s-with-samples", status = "verified")

        insertSession("s-tombstoned")
        insertSample("samp-del", "s-tombstoned", status = "verified", deletedAt = 2_000L)

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.NO_FRAMES.sql,
            fromMillis = null,
            toMillis = null,
            limit = 10,
        ).first()

        val ids = summaries.map { it.session.sessionId }.toSet()
        assertEquals(setOf("s-empty", "s-tombstoned"), ids)
    }

    @Test
    fun `filter TO_REVIEW - returns sessions with flagged live samples in window`() = runTest {
        insertSession("s-flagged", startedAt = 1_000L)
        insertSample("samp-f1", "s-flagged", status = "flagged", timestamp = 5_000L)

        insertSession("s-verified", startedAt = 1_000L)
        insertSample("samp-v1", "s-verified", status = "verified", timestamp = 5_000L)

        insertSession("s-flagged-out-of-window", startedAt = 1_000L)
        insertSample("samp-f2", "s-flagged-out-of-window", status = "flagged", timestamp = 12_000L)

        insertSession("s-tombstoned-flagged", startedAt = 1_000L)
        insertSample("samp-f3", "s-tombstoned-flagged", status = "flagged", timestamp = 5_000L, deletedAt = 6_000L)

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.TO_REVIEW.sql,
            fromMillis = 4_000L,
            toMillis = 10_000L,
            limit = 10,
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("s-flagged", summaries.first().session.sessionId)
        assertEquals(1, summaries.first().unverifiedSamples)
    }

    @Test
    fun `filter EXAMINED - returns sessions with non-flagged live samples`() = runTest {
        insertSession("s-only-flagged")
        insertSample("samp-f", "s-only-flagged", status = "flagged")

        insertSession("s-examined")
        insertSample("samp-v", "s-examined", status = "verified")

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.EXAMINED.sql,
            fromMillis = null,
            toMillis = null,
            limit = 10,
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("s-examined", summaries.first().session.sessionId)
    }

    @Test
    fun `filter POSITIVE - handles false positive only vs confirmed detections`() = runTest {
        insertSession("s-fp-only")
        insertSample("samp-fp", "s-fp-only", status = "verified")
        insertDetection("det-fp", "samp-fp", verdict = DetectionVerdict.FALSE_POSITIVE.value)

        insertSession("s-confirmed")
        insertSample("samp-conf", "s-confirmed", status = "verified")
        insertDetection("det-conf", "samp-conf", verdict = DetectionVerdict.CONFIRMED.value)

        val summaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.POSITIVE.sql,
            fromMillis = null,
            toMillis = null,
            limit = 10,
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("s-confirmed", summaries.first().session.sessionId)
        assertTrue(summaries.first().isPositive)

        // Check s-fp-only under ALL filter has isPositive = false
        val allSummaries = sessionDao.observeSessionSummaries(
            userId = testUserId,
            filter = SessionListFilter.ALL.sql,
            fromMillis = null,
            toMillis = null,
            limit = 10,
        ).first()
        val fpSummary = allSummaries.first { it.session.sessionId == "s-fp-only" }
        assertFalse(fpSummary.isPositive)
    }

    @Test
    fun `observeSessionSummaryCount matches summary query results`() = runTest {
        insertSession("s-1")
        insertSample("samp-1", "s-1", status = "flagged")
        insertSession("s-2")
        insertSample("samp-2", "s-2", status = "verified")

        val count = sessionDao.observeSessionSummaryCount(
            userId = testUserId,
            filter = SessionListFilter.TO_REVIEW.sql,
            fromMillis = null,
            toMillis = null,
        ).first()

        assertEquals(1, count)
    }

    @Test
    fun `observeEmptySessionCount - respects excludeSessionId and tombstoned samples`() = runTest {
        insertSession("s-empty-1")
        insertSession("s-empty-2")
        insertSession("s-has-sample")
        insertSample("s-samp", "s-has-sample", status = "verified")

        // Tombstoned sample means session is empty
        insertSession("s-tombstoned")
        insertSample("s-del", "s-tombstoned", status = "verified", deletedAt = 2_000L)

        // 3 empty sessions: s-empty-1, s-empty-2, s-tombstoned
        val countTotal = sessionDao.observeEmptySessionCount(testUserId, excludeSessionId = null).first()
        assertEquals(3, countTotal)

        // Excluding s-empty-1 yields 2
        val countExcluded = sessionDao.observeEmptySessionCount(testUserId, excludeSessionId = "s-empty-1").first()
        assertEquals(2, countExcluded)
    }

    @Test
    fun `observeSessionsPage counts WRONG_CLASS and BOX_INCORRECT detections as valid eggs`() = runTest {
        insertSession("s-corrected")
        insertSample("samp-c1", "s-corrected", status = "verified")
        insertDetection("det-wrong-class", "samp-c1", verdict = DetectionVerdict.WRONG_CLASS.value)
        insertDetection("det-box-incorrect", "samp-c1", verdict = DetectionVerdict.BOX_INCORRECT.value)
        insertDetection("det-fp", "samp-c1", verdict = DetectionVerdict.FALSE_POSITIVE.value)

        val sessions = sessionDao.observeSessionsPage(
            userId = testUserId,
            patientId = patientId,
            activeSessionId = null,
            sinceMillis = 0L,
            startMillis = null,
            endMillis = null,
            query = "",
            limit = 10,
        ).first()

        assertEquals(1, sessions.size)
        // 2 non-false-positive eggs (WRONG_CLASS + BOX_INCORRECT)
        assertEquals(2, sessions.first().totalEggs)
    }
}
