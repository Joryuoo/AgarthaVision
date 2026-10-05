package com.agarthavision.domain.usecase.auth

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.SampleStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [DiscardUnsyncedDataUseCase] against a real in-memory [AgarthaDatabase], covering the
 * FK-safe deletion order now that a report can be patient-scoped (`patient_id` set,
 * `session_id` null) as well as session-scoped, per 14zcqntj2uz / `0015_patient_reports.sql`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscardUnsyncedDataUseCaseTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var useCase: DiscardUnsyncedDataUseCase
    private val sampleImageStore: SampleImageStore = mock()

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        useCase = DiscardUnsyncedDataUseCase(
            database = db,
            patientDao = db.patientDao(),
            sessionDao = db.sessionDao(),
            sampleDao = db.sampleDao(),
            reportDao = db.reportDao(),
            sampleImageStore = sampleImageStore,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `discards a pending patient report along with its patient and session without an FK violation`() = runTest {
        seedPatient("p1")
        seedSession("s1", "p1")
        seedSample("smp-1", "s1")
        db.reportDao().insertReport(patientReport("r1", "p1", ReportSyncStatus.PENDING.value))

        val result = useCase(USER_ID)

        assertEquals(1, result.patients)
        assertEquals(1, result.sessions)
        assertEquals(1, result.samples)
        assertEquals(1, result.reports)
        assertNull(db.reportDao().getReportById("r1"))
        assertNull(db.sessionDao().getSessionById("s1"))
    }

    @Test
    fun `a pending patient report is removed while a patient with a still-synced session is kept`() = runTest {
        seedPatient("p2")
        seedSession("s2", "p2", supabaseStatus = "synced")
        db.reportDao().insertReport(patientReport("r2", "p2", ReportSyncStatus.PENDING.value))

        val result = useCase(USER_ID)

        // The patient still owns a synced session, so it is left alone even though it is
        // itself in PENDING state - deleting it would strand that session's record.
        assertEquals(0, result.patients)
        assertEquals(1, result.reports)
        assertNull(db.reportDao().getReportById("r2"))
        assertNotNull(db.sessionDao().getSessionById("s2"))
    }

    /**
     * [DiscardUnsyncedDataUseCase] decides whether a patient is still "in use" via both
     * `sessionDao.countSessionsForPatient` and `reportDao.countReportsForPatient`. A patient
     * report's only FK is to `patient_id` directly (`0015_patient_reports.sql`), so a patient
     * with zero sessions but an already-SYNCED patient report must still be treated as in use -
     * otherwise `ReportEntity`'s `patient_id` foreign key (`onDelete = CASCADE`) would silently
     * delete that already-synced report along with the patient.
     */
    @Test
    fun `deleting an unsynced patient with no sessions leaves its synced patient report and the patient intact`() =
        runTest {
            seedPatient("p3")
            // No sessions for p3 at all - the scenario a session-only check cannot see.
            db.reportDao().insertReport(patientReport("r3", "p3", ReportSyncStatus.SYNCED.value))

            val result = useCase(USER_ID)

            // A SYNCED report is never something a client-side discard should remove, and the
            // patient it references must survive with it.
            assertEquals(0, result.patients)
            assertNotNull(
                "a SYNCED patient report must not be cascade-deleted when its unsynced, " +
                    "session-less patient is discarded",
                db.reportDao().getReportById("r3"),
            )
            assertNotNull(db.patientDao().getPatientById("p3"))
        }

    private suspend fun seedPatient(id: String) {
        db.patientDao().upsertPatient(
            PatientEntity(
                patientId = id,
                lastname = "Cruz",
                firstname = "Gerald",
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = USER_ID,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                supabaseStatus = "pending",
            ),
        )
        // getPatientsPendingSync resolves visibility through patient_users, not createdBy -
        // without this link the patient is invisible to the pending-sync query and never
        // reaches the discard's deletion path at all.
        db.patientDao().linkPatientToUser(PatientUserEntity(patientId = id, userId = USER_ID, linkedAt = 1_000L))
    }

    private suspend fun seedSession(id: String, patientId: String, supabaseStatus: String = "pending") {
        db.sessionDao().upsertSession(
            SessionEntity(
                sessionId = id,
                userId = USER_ID,
                patientId = patientId,
                deviceId = "device-1",
                startedAt = 1_000L,
                supabaseStatus = supabaseStatus,
            ),
        )
    }

    private suspend fun seedSample(id: String, sessionId: String) {
        db.sampleDao().upsertSample(
            SampleEntity(
                sampleId = id,
                sessionId = sessionId,
                userId = USER_ID,
                deviceId = "device-1",
                timestamp = 1_000L,
                imagePath = "/tmp/$id.jpg",
                status = SampleStatus.VERIFIED.value,
            ),
        )
    }

    private fun patientReport(id: String, patientId: String, supabaseStatus: String) = ReportEntity(
        reportId = id,
        sessionId = null,
        patientId = patientId,
        sessionIdsJson = """["s-does-not-matter"]""",
        userId = USER_ID,
        reportType = "patient",
        generatedAt = 3_000L,
        totalSamples = 1,
        totalEggsConfirmed = 0,
        positiveSpeciesJson = "[]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        supabaseStatus = supabaseStatus,
        createdAt = 3_000L,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
