package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.psgc.readBundledBarangays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for [CoverageDao.observeTownCoverage] — the examined/positive rule
 * mirrors [DetectionDao.observeSessionFindingsBetween] exactly (see that DAO's owner-visibility
 * tests), but this DAO counts smears per town rather than species pairs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoverageDaoTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var coverageDao: CoverageDao
    private lateinit var sessionDao: SessionDao
    private lateinit var sampleDao: SampleDao
    private lateinit var detectionDao: DetectionDao
    private lateinit var patientDao: PatientDao
    private lateinit var psgcBarangayDao: PsgcBarangayDao

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coverageDao = db.coverageDao()
        sessionDao = db.sessionDao()
        sampleDao = db.sampleDao()
        detectionDao = db.detectionDao()
        patientDao = db.patientDao()
        psgcBarangayDao = db.psgcBarangayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedPatient(patientId: String, barangayCode: String) =
        patientDao.upsertPatient(
            PatientEntity(
                patientId = patientId,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = barangayCode,
                createdBy = "user-a",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

    private suspend fun seedSession(sessionId: String, patientId: String, userId: String?, startedAt: Long) =
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = sessionId,
                userId = userId,
                patientId = patientId,
                deviceId = "device-1",
                startedAt = startedAt,
            ),
        )

    private suspend fun seedSample(
        sampleId: String,
        sessionId: String,
        status: String = "synced",
        deletedAt: Long? = null,
    ) = sampleDao.upsertSample(
        SampleEntity(
            sampleId = sampleId,
            sessionId = sessionId,
            userId = "user-a",
            deviceId = "device-1",
            timestamp = 1_500L,
            verifiedAt = 2_000L,
            imagePath = "/tmp/$sampleId.jpg",
            storagePath = null,
            status = status,
            deletedAt = deletedAt,
        ),
    )

    private suspend fun seedDetection(
        detectionId: String,
        sampleId: String,
        verdict: String = "confirmed",
    ) = detectionDao.insertDetection(
        DetectionEntity(
            detectionId = detectionId,
            sampleId = sampleId,
            classLabel = "Ascaris",
            confidence = 0.9f,
            bboxX = 0.1f,
            bboxY = 0.2f,
            bboxW = 0.3f,
            bboxH = 0.4f,
            verdict = verdict,
            expertClass = null,
        ),
    )

    @Test
    fun `another user's session is excluded`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-b", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(0, rows.sumOf { it.smearCount })
    }

    @Test
    fun `session with only flagged samples is excluded`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1", status = "flagged")
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(0, rows.sumOf { it.smearCount })
    }

    @Test
    fun `session with only tombstoned samples is excluded`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1", deletedAt = 5_000L)
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(0, rows.sumOf { it.smearCount })
    }

    @Test
    fun `WRONG_CLASS and BOX_INCORRECT verdicts count as positive`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1", verdict = "wrong_class")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.sumOf { it.smearCount })
        assertEquals(1, rows.sumOf { it.positiveCount })
    }

    @Test
    fun `a session with only FALSE_POSITIVE detections is examined but not positive`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1", verdict = "false_positive")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.sumOf { it.smearCount })
        assertEquals(0, rows.sumOf { it.positiveCount })
    }

    @Test
    fun `two positive samples in one session still count as exactly one smear`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedSample("smp-2", "s1")
        seedDetection("d1", "smp-1", verdict = "confirmed")
        seedDetection("d2", "smp-2", verdict = "confirmed")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.sumOf { it.smearCount })
        assertEquals(1, rows.sumOf { it.positiveCount })
    }

    @Test
    fun `window start is inclusive, end exclusive`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1")

        val inclusive = coverageDao.observeTownCoverage("user-a", 1_000L, 10_000L).first()
        assertEquals(1, inclusive.sumOf { it.smearCount })

        val exclusive = coverageDao.observeTownCoverage("user-a", 0L, 1_000L).first()
        assertEquals(0, exclusive.sumOf { it.smearCount })
    }

    @Test
    fun `a patient with an unrecognized barangay code yields a null townCode`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.size)
        assertNull(rows.first().townCode)
    }

    @Test
    fun `a session with one flagged sample and one verified positive sample still counts once`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-flagged", "s1", status = "flagged")
        seedSample("smp-verified", "s1", status = "synced")
        seedDetection("d-flagged", "smp-flagged", verdict = "confirmed")
        seedDetection("d-verified", "smp-verified", verdict = "confirmed")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.sumOf { it.smearCount })
        assertEquals(1, rows.sumOf { it.positiveCount })
    }

    @Test
    fun `multiple sessions for the same patient and town aggregate into that town's smearCount`() = runTest {
        seedPatient(PATIENT_ID, UNRECOGNIZED_CODE)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSession("s2", PATIENT_ID, userId = "user-a", startedAt = 2_000L)
        seedSample("smp-1", "s1")
        seedSample("smp-2", "s2")
        seedDetection("d1", "smp-1", verdict = "confirmed")
        seedDetection("d2", "smp-2", verdict = "false_positive")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.size)
        assertEquals(2, rows.first().smearCount)
        assertEquals(1, rows.first().positiveCount)
    }

    @Test
    fun `a blank psgc_barangay_code yields a null townCode without crashing`() = runTest {
        seedPatient(PATIENT_ID, "")
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertEquals(1, rows.size)
        assertNull(rows.first().townCode)
    }

    @Test
    fun `a Manila barangay code resolves to Manila's real city_muni_code`() = runTest {
        val bundled = readBundledBarangays(ctx)
        val manilaBarangay = bundled.first { it.cityMuniName.contains("Manila", ignoreCase = true) }
        psgcBarangayDao.insertAll(listOf(manilaBarangay))

        seedPatient(PATIENT_ID, manilaBarangay.code)
        seedSession("s1", PATIENT_ID, userId = "user-a", startedAt = 1_000L)
        seedSample("smp-1", "s1")
        seedDetection("d1", "smp-1")

        val rows = coverageDao.observeTownCoverage("user-a", 0L, 10_000L).first()
        assertTrue(rows.any { it.townCode == manilaBarangay.cityMuniCode })
    }

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val UNRECOGNIZED_CODE = "9999999999"
    }
}
