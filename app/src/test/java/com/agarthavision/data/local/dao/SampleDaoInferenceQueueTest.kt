package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.mapper.effectiveInferenceState
import com.agarthavision.data.repository.InferenceQueueRepositoryImpl
import com.agarthavision.domain.inference.InferenceEngineId
import com.agarthavision.domain.inference.InferenceResult
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.domain.inference.Prediction
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the inference queue's SQL (14zcqntj6ny) against a real Room database.
 *
 * The queue's safety rests on each transition being one conditional UPDATE, so these tests
 * drive the races as sequences: a cancel lands between the claim and the result, a delete lands
 * mid-inference, a verified sample is offered a result. `InferenceQueueProcessorTest` runs the
 * processor against an in-memory fake with the same rules; this is what keeps the fake honest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SampleDaoInferenceQueueTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sampleDao: SampleDao
    private lateinit var repository: InferenceQueueRepositoryImpl

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sampleDao = db.sampleDao()
        repository = InferenceQueueRepositoryImpl(sampleDao, Gson())
    }

    @After
    fun tearDown() = db.close()

    // ── the queue ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the queue lists queued frames only, oldest capture first`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("late", timestamp = 300))
        sampleDao.upsertSample(sample("early", timestamp = 100))
        sampleDao.upsertSample(sample("ready", timestamp = 50, inferenceState = "ready"))
        sampleDao.upsertSample(sample("running", timestamp = 60, inferenceState = "in_inference"))
        sampleDao.upsertSample(sample("manual", timestamp = 70, inferenceState = "manual", isManual = true))
        sampleDao.upsertSample(sample("verified", timestamp = 80, status = "verified"))

        assertEquals(listOf("early", "late"), repository.queuedSampleIds())
    }

    @Test
    fun `a frame can be claimed once`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))

        assertTrue(repository.claim("a"))
        assertFalse(repository.claim("a"))
        assertEquals(InferenceState.IN_INFERENCE, stateOf("a"))
    }

    @Test
    fun `a result lands on an in-inference frame and marks it ready`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")

        assertTrue(repository.complete("a", result(modelVersion = "yolo26n-effv2b0-v1-tflite-fp32")))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(InferenceState.READY, row.effectiveInferenceState())
        assertEquals("yolo26n-effv2b0-v1-tflite-fp32", row.inferenceModelVersion)
        assertEquals(640, row.imageWidth)
        assertEquals(480, row.imageHeight)
        assertTrue(row.predictionsJson!!.contains("Ascaris lumbricoides"))
        assertFalse(row.isManual)
    }

    @Test
    fun `a clean field is ready with no predictions, not pending`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")

        repository.complete("a", result(predictions = emptyList()))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(InferenceState.READY, row.effectiveInferenceState())
        assertNull(row.predictionsJson)
    }

    // ── cancel versus result ─────────────────────────────────────────────────────────────────

    @Test
    fun `a result arriving after a cancel is discarded`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")

        assertTrue(repository.cancel("a"))
        assertFalse(repository.complete("a", result()))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(InferenceState.MANUAL, row.effectiveInferenceState())
        assertTrue(row.isManual)
        assertEquals("manual", row.inferenceModelVersion)
        assertNull("the cancelled sample never gets a model output", row.predictionsJson)
        assertNull(row.imageWidth)
    }

    @Test
    fun `a queued frame can be cancelled before its turn, and is then never claimed`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))

        assertTrue(repository.cancel("a"))

        assertFalse(repository.claim("a"))
        assertTrue(repository.queuedSampleIds().isEmpty())
    }

    @Test
    fun `a cancel after the result landed changes nothing`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")
        repository.complete("a", result())

        assertFalse(repository.cancel("a"))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(InferenceState.READY, row.effectiveInferenceState())
        assertFalse(row.isManual)
    }

    @Test
    fun `a result for a frame deleted mid-inference writes nothing`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")

        sampleDao.deleteSample("a")

        assertFalse(repository.complete("a", result()))
        assertNull(sampleDao.getSampleByIdIncludingDeleted("a"))
    }

    @Test
    fun `a verified sample is never given a model output`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a", status = "verified", inferenceState = "in_inference"))

        assertFalse(repository.complete("a", result()))
        assertFalse(repository.cancel("a"))
    }

    // ── both engines failing ─────────────────────────────────────────────────────────────────

    @Test
    fun `a failure below the limit puts the frame back in the queue`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")

        assertEquals(InferenceState.QUEUED, repository.recordFailure("a", maxAttempts = 5))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(1, row.inferenceAttempts)
        assertFalse(row.isManual)
        assertEquals(listOf("a"), repository.queuedSampleIds())
    }

    @Test
    fun `the failure that reaches the limit makes the frame manual`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a", attempts = 4))
        repository.claim("a")

        assertEquals(InferenceState.MANUAL, repository.recordFailure("a", maxAttempts = 5))

        val row = sampleDao.getSampleById("a")!!
        assertEquals(5, row.inferenceAttempts)
        assertTrue(row.isManual)
        assertEquals("manual", row.inferenceModelVersion)
        assertTrue(repository.queuedSampleIds().isEmpty())
    }

    @Test
    fun `a failure on a frame cancelled mid-inference writes nothing`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("a"))
        repository.claim("a")
        repository.cancel("a")

        assertNull(repository.recordFailure("a", maxAttempts = 5))
        assertEquals(0, sampleDao.getSampleById("a")!!.inferenceAttempts)
    }

    // ── restart recovery ─────────────────────────────────────────────────────────────────────

    @Test
    fun `interrupted frames go back to the queue and nothing else moves`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample("running", inferenceState = "in_inference"))
        sampleDao.upsertSample(sample("ready", inferenceState = "ready"))
        sampleDao.upsertSample(sample("manual", inferenceState = "manual", isManual = true))

        repository.requeueInterrupted()

        assertEquals(InferenceState.QUEUED, stateOf("running"))
        assertEquals(InferenceState.READY, stateOf("ready"))
        assertEquals(InferenceState.MANUAL, stateOf("manual"))
    }

    // ── reading the state ────────────────────────────────────────────────────────────────────

    @Test
    fun `is_manual wins over the column, so an old or pulled manual row never reads as ready`() = runTest {
        seedSession()
        // What a row written before version 23, or pulled from Supabase, looks like.
        sampleDao.upsertSample(sample("legacy", inferenceState = "ready", isManual = true))

        assertEquals(InferenceState.MANUAL, stateOf("legacy"))
        assertEquals("manual", sampleDao.getEffectiveInferenceState("legacy"))
        assertTrue(repository.queuedSampleIds().isEmpty())
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    private suspend fun stateOf(sampleId: String): InferenceState =
        sampleDao.getSampleById(sampleId)!!.effectiveInferenceState()

    private suspend fun seedSession() {
        db.patientDao().upsertPatient(
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
        db.sessionDao().upsertSession(
            SessionEntity(
                sessionId = SESSION_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 1_000L,
                label = "Smear A",
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun sample(
        id: String,
        timestamp: Long = 1_500L,
        status: String = "flagged",
        inferenceState: String = "queued",
        isManual: Boolean = false,
        attempts: Int = 0,
    ) = SampleEntity(
        sampleId = id,
        sessionId = SESSION_ID,
        userId = USER_ID,
        deviceId = "device-1",
        timestamp = timestamp,
        imagePath = "/tmp/$id.jpg",
        status = status,
        isManual = isManual,
        inferenceState = inferenceState,
        inferenceAttempts = attempts,
    )

    private fun result(
        predictions: List<Prediction> = listOf(
            Prediction(
                classLabel = "Ascaris lumbricoides",
                confidence = 0.9f,
                x = 100f,
                y = 120f,
                width = 30f,
                height = 40f,
            ),
        ),
        modelVersion: String = "yolo26n-effv2b0-v1-cloud-fp32",
    ) = InferenceResult(
        predictions = predictions,
        imageWidth = 640,
        imageHeight = 480,
        modelVersion = modelVersion,
        engine = InferenceEngineId.REMOTE,
    )

    private companion object {
        const val USER_ID = "user-1"
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
    }
}
