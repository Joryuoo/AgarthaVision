package com.agarthavision.data.local.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins `SampleDao.upsertSample` and `SessionDao.upsertSession` against their cascades (86d4bx196).
 *
 * Both used to be `@Insert(onConflict = OnConflictStrategy.REPLACE)`. In SQLite `INSERT OR
 * REPLACE` is not an update: it deletes the conflicting row and inserts a new one, and that
 * delete fires every foreign-key cascade hanging off it. Re-writing a sample took its
 * `detections` and `sample_species_findings` with it; re-writing a session took its `reports`.
 * Nothing threw, nothing logged, and the parent row read back correctly updated — which is
 * precisely why this needs a test rather than a reviewer.
 *
 * Every test here passes under `@Upsert` and fails under `@Insert(REPLACE)`. If one ever passes
 * both ways, read [foreign keys are actually enforced in this database] first: a Room database
 * built without foreign-key enforcement makes all of these vacuous.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SampleDaoUpsertCascadeTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sampleDao: SampleDao
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao
    private lateinit var detectionDao: DetectionDao
    private lateinit var findingDao: SampleSpeciesFindingDao
    private lateinit var reportDao: ReportDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sampleDao = db.sampleDao()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
        detectionDao = db.detectionDao()
        findingDao = db.sampleSpeciesFindingDao()
        reportDao = db.reportDao()
    }

    @After
    fun tearDown() = db.close()

    // ── the guard that keeps the rest of this file meaningful ─────────────────

    @Test
    fun `foreign keys are actually enforced in this database`() = runTest {
        // Without this, every assertion below would pass on REPLACE too - no cascade can fire
        // if the keys are off, so the children would survive for the wrong reason and the
        // regression would be invisible. The ticket calls this out by name.
        val error = runCatching {
            detectionDao.insertDetection(detection("det-orphan", sampleId = "no-such-sample"))
        }.exceptionOrNull()

        assertTrue(
            "expected a foreign-key violation, got $error",
            error is SQLiteConstraintException,
        )
    }

    // ── samples ──────────────────────────────────────────────────────────────

    @Test
    fun `re-writing a sample keeps its detections`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(status = "verified"))
        detectionDao.insertDetections(
            listOf(detection("det-1"), detection("det-2")),
        )

        // What a pull does to a sample the device already holds.
        sampleDao.upsertSample(sample(status = "synced", storagePath = "user-a/smp-1.jpg"))

        // Fails on @Insert(REPLACE): the conflict is resolved by deleting the sample row, which
        // cascades detections away. That is the retraining corpus C8 exists to protect, and it
        // would go without a single error anywhere.
        assertEquals(
            listOf("det-1", "det-2"),
            detectionDao.getDetectionsForSample(SAMPLE_ID).map { it.detectionId }.sorted(),
        )
        // ...and the parent really was updated, so this is not passing by skipping the write.
        assertEquals("user-a/smp-1.jpg", sampleDao.getSampleById(SAMPLE_ID)?.storagePath)
    }

    @Test
    fun `re-writing a sample keeps its per-species findings`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(status = "verified"))
        findingDao.insertFindings(listOf(finding("fnd-1", "Ascaris lumbricoides", eggCount = 23)))

        sampleDao.upsertSample(sample(status = "synced"))

        // `sample_species_findings.sample_id` is the second CASCADE hanging off this row.
        val findings = findingDao.getFindingsForSample(SAMPLE_ID)
        assertEquals(1, findings.size)
        assertEquals(23, findings.single().eggCount)
    }

    // ── sessions ─────────────────────────────────────────────────────────────

    @Test
    fun `re-writing a session keeps its reports`() = runTest {
        seedSession()
        reportDao.insertReport(report("rpt-1"))

        // What pullSessions does to a session the device already holds, every pass.
        sessionDao.upsertSession(sessionEntity(label = "Smear A (renamed)"))

        // Fails on @Insert(REPLACE): `reports.session_id` is CASCADE, so the report row goes
        // while its CSV and PDF stay on disk - orphaned, and unreachable through any query.
        assertNotNull(reportDao.getReportById("rpt-1"))
        assertEquals("Smear A (renamed)", sessionDao.getSessionById(SESSION_ID)?.label)
    }

    @Test
    fun `a NO_ACTION key on samples does not protect the session's other children`() = runTest {
        // The trap worth pinning. `samples.session_id` is NO_ACTION, which refuses a delete that
        // would orphan samples - so it looks like it should have blocked the REPLACE outright
        // and made this whole ticket theoretical. It does not: REPLACE re-inserts the parent
        // under the same id inside the same statement, so the constraint is satisfied by the
        // time it is checked. Under REPLACE this test used to leave the sample alive and the
        // report deleted, which is the worst of both.
        seedSession()
        sampleDao.upsertSample(sample(status = "synced"))
        reportDao.insertReport(report("rpt-1"))

        sessionDao.upsertSession(sessionEntity(label = "Smear A (renamed)"))

        assertNotNull(sampleDao.getSampleById(SAMPLE_ID))
        assertNotNull(reportDao.getReportById("rpt-1"))
    }

    // ── a first write still inserts ──────────────────────────────────────────

    @Test
    fun `upsert still inserts a row that is not there yet`() = runTest {
        // @Upsert is INSERT-then-UPDATE, so the capture path - which only ever writes new rows -
        // has to behave exactly as it did.
        seedSession()

        sampleDao.upsertSample(sample(status = "flagged"))

        assertEquals("flagged", sampleDao.getSampleById(SAMPLE_ID)?.status)
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    private suspend fun seedSession() {
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
        sessionDao.upsertSession(sessionEntity())
    }

    private fun sessionEntity(label: String? = "Smear A") = SessionEntity(
        sessionId = SESSION_ID,
        userId = USER_ID,
        patientId = PATIENT_ID,
        deviceId = "device-1",
        startedAt = 1_000L,
        label = label,
    )

    private fun sample(status: String, storagePath: String? = null) = SampleEntity(
        sampleId = SAMPLE_ID,
        sessionId = SESSION_ID,
        userId = USER_ID,
        deviceId = "device-1",
        timestamp = 1_500L,
        verifiedAt = 2_000L,
        imagePath = "/tmp/smp-1.jpg",
        storagePath = storagePath,
        status = status,
    )

    private fun detection(id: String, sampleId: String = SAMPLE_ID) = DetectionEntity(
        detectionId = id,
        sampleId = sampleId,
        classLabel = "Ascaris lumbricoides",
        confidence = 0.9f,
        bboxX = 10f,
        bboxY = 20f,
        bboxW = 30f,
        bboxH = 40f,
    )

    private fun finding(id: String, species: String, eggCount: Int) =
        SampleSpeciesFindingEntity(
            findingId = id,
            sampleId = SAMPLE_ID,
            species = species,
            eggCount = eggCount,
        )

    private fun report(id: String) = ReportEntity(
        reportId = id,
        sessionId = SESSION_ID,
        userId = USER_ID,
        generatedAt = 3_000L,
        totalSamples = 1,
        totalEggsConfirmed = 2,
        positiveSpeciesJson = "[\"Ascaris lumbricoides\"]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = "/tmp/rpt-1.csv",
        pdfFilePath = "/tmp/rpt-1.pdf",
        createdAt = 3_000L,
    )

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
        const val SAMPLE_ID = "smp-1"
        const val USER_ID = "user-a"
    }
}
