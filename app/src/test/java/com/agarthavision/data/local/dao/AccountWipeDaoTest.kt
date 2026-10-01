package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SampleStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins what [AccountWipeDao] removes when the server refuses an account (14zcqntjph8), against a
 * real in-memory Room so the foreign keys are enforced as on the phone.
 *
 * The two rules: everything the refused account holds, and everything synced, goes; another
 * account's unsynced work, and the parents it hangs from, stays (C8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountWipeDaoTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var wipeDao: AccountWipeDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wipeDao = db.accountWipeDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `the refused account's synced rows and drafts all go`() = runTest {
        seedPatient("p-1", createdBy = LEAVER, status = SYNCED)
        db.patientDao().linkPatientToUser(PatientUserEntity("p-1", LEAVER, linkedAt = 1L))
        seedSession("s-1", userId = LEAVER, patientId = "p-1", status = SYNCED)
        seedSample("smp-synced", sessionId = "s-1", userId = LEAVER, status = SampleStatus.SYNCED)
        seedSample("smp-draft", sessionId = "s-1", userId = LEAVER, status = SampleStatus.FLAGGED)
        seedReport("r-1", sessionId = "s-1", userId = LEAVER, status = SYNCED)

        wipe()

        assertNull(db.patientDao().getPatientById("p-1"))
        assertNull(db.sessionDao().getSessionById("s-1"))
        assertNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-synced"))
        assertNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-draft"))
        assertNull(db.reportDao().getReportById("r-1"))
        assertTrue(db.patientDao().getLinksForUser(LEAVER).isEmpty())
    }

    @Test
    fun `another account's synced leftovers go too, because the server holds them`() = runTest {
        seedPatient("p-2", createdBy = COLLEAGUE, status = SYNCED)
        seedSession("s-2", userId = COLLEAGUE, patientId = "p-2", status = SYNCED)
        seedSample("smp-2", sessionId = "s-2", userId = COLLEAGUE, status = SampleStatus.SYNCED)

        wipe()

        assertNull(db.patientDao().getPatientById("p-2"))
        assertNull(db.sessionDao().getSessionById("s-2"))
        assertNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-2"))
    }

    @Test
    fun `another account's unsynced work and its parents stay`() = runTest {
        // A colleague worked offline on this phone and never signed out. That work has not
        // reached Supabase and is not the refused account's to lose (C8).
        seedPatient("p-3", createdBy = COLLEAGUE, status = SYNCED)
        seedSession("s-3", userId = COLLEAGUE, patientId = "p-3", status = SYNCED)
        seedSample("smp-3", sessionId = "s-3", userId = COLLEAGUE, status = SampleStatus.VERIFIED)
        seedPatient("p-4", createdBy = COLLEAGUE, status = PENDING)

        wipe()

        assertNotNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-3"))
        assertNotNull(db.sessionDao().getSessionById("s-3"))
        assertNotNull(db.patientDao().getPatientById("p-3"))
        assertNotNull(db.patientDao().getPatientById("p-4"))
    }

    @Test
    fun `it reports the files it orphaned so the caller can delete them`() = runTest {
        seedPatient("p-5", createdBy = LEAVER, status = SYNCED)
        seedSession("s-5", userId = LEAVER, patientId = "p-5", status = SYNCED)
        seedSample("smp-5", sessionId = "s-5", userId = LEAVER, status = SampleStatus.SYNCED)
        seedReport("r-5", sessionId = "s-5", userId = LEAVER, status = SYNCED)

        assertEquals(listOf("/files/smp-5.jpg"), wipeDao.getWipedSampleImagePaths(LEAVER))
        assertEquals(
            listOf(ReportFilePaths(csvFilePath = "/docs/r-5.csv", pdfFilePath = "content://docs/r-5")),
            wipeDao.getWipedReportFiles(LEAVER),
        )
    }

    private suspend fun wipe() {
        wipeDao.deleteReports(LEAVER)
        wipeDao.deleteSamples(LEAVER)
        wipeDao.deleteSessions(LEAVER)
        wipeDao.deletePatientLinks(LEAVER)
        wipeDao.deletePatients(LEAVER)
    }

    private suspend fun seedPatient(id: String, createdBy: String, status: String) {
        db.patientDao().upsertPatient(
            PatientEntity(
                patientId = id,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = createdBy,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                supabaseStatus = status,
            ),
        )
    }

    private suspend fun seedSession(id: String, userId: String, patientId: String, status: String) {
        db.sessionDao().upsertSession(
            SessionEntity(
                sessionId = id,
                userId = userId,
                patientId = patientId,
                deviceId = "device-1",
                startedAt = 1_000L,
                label = id,
                supabaseStatus = status,
            ),
        )
    }

    private suspend fun seedSample(id: String, sessionId: String, userId: String, status: SampleStatus) {
        db.sampleDao().upsertSample(
            SampleEntity(
                sampleId = id,
                sessionId = sessionId,
                userId = userId,
                deviceId = "device-1",
                timestamp = 1_500L,
                imagePath = "/files/$id.jpg",
                status = status.value,
            ),
        )
    }

    private suspend fun seedReport(id: String, sessionId: String, userId: String, status: String) {
        db.reportDao().insertReport(
            ReportEntity(
                reportId = id,
                sessionId = sessionId,
                userId = userId,
                generatedAt = 2_000L,
                totalSamples = 1,
                totalEggsConfirmed = 0,
                positiveSpeciesJson = "[]",
                lpfPerSpeciesJson = "{}",
                csvFilePath = "/docs/$id.csv",
                pdfFilePath = "content://docs/$id",
                supabaseStatus = status,
                createdAt = 2_000L,
            ),
        )
    }

    private companion object {
        const val LEAVER = "user-leaver"
        const val COLLEAGUE = "user-colleague"
        const val SYNCED = "synced"
        const val PENDING = "pending"
    }
}
