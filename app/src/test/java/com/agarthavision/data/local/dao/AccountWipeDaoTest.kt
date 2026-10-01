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
 * Pins what [AccountWipeDao] removes when the server signs a phone out (14zcqntjph8), against a
 * real in-memory Room so the foreign keys are enforced as on the phone.
 *
 * The rule: everything synced goes, whoever owns it; everything unsynced stays, the signed-out
 * account's included, with the parents it hangs from. The server refuses a login both for a
 * removed account and for a password changed elsewhere, and the second must not cost field work.
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
    fun `the signed-out account's synced rows all go`() = runTest {
        seedPatient("p-1", createdBy = SIGNED_OUT, status = SYNCED)
        db.patientDao().linkPatientToUser(PatientUserEntity("p-1", SIGNED_OUT, linkedAt = 1L))
        seedSession("s-1", userId = SIGNED_OUT, patientId = "p-1", status = SYNCED)
        seedSample("smp-1", sessionId = "s-1", userId = SIGNED_OUT, status = SampleStatus.SYNCED)
        seedReport("r-1", sessionId = "s-1", userId = SIGNED_OUT, status = SYNCED)

        wipe()

        assertNull(db.patientDao().getPatientById("p-1"))
        assertNull(db.sessionDao().getSessionById("s-1"))
        assertNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-1"))
        assertNull(db.reportDao().getReportById("r-1"))
        // The link cascades with its patient.
        assertTrue(db.patientDao().getLinksForUser(SIGNED_OUT).isEmpty())
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
    fun `the signed-out account's own unsynced work and drafts stay, with their parents`() = runTest {
        // A medtech whose password was changed on the web: this work uploads after they sign
        // back in with the new one.
        seedPatient("p-3", createdBy = SIGNED_OUT, status = SYNCED)
        seedSession("s-3", userId = SIGNED_OUT, patientId = "p-3", status = SYNCED)
        seedSample("smp-synced", sessionId = "s-3", userId = SIGNED_OUT, status = SampleStatus.SYNCED)
        seedSample("smp-verified", sessionId = "s-3", userId = SIGNED_OUT, status = SampleStatus.VERIFIED)
        seedSample("smp-draft", sessionId = "s-3", userId = SIGNED_OUT, status = SampleStatus.FLAGGED)
        seedPatient("p-4", createdBy = SIGNED_OUT, status = PENDING)
        seedReport("r-3", sessionId = "s-3", userId = SIGNED_OUT, status = PENDING)

        wipe()

        assertNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-synced"))
        assertNotNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-verified"))
        assertNotNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-draft"))
        assertNotNull(db.reportDao().getReportById("r-3"))
        assertNotNull(db.sessionDao().getSessionById("s-3"))
        assertNotNull(db.patientDao().getPatientById("p-3"))
        assertNotNull(db.patientDao().getPatientById("p-4"))
    }

    @Test
    fun `another account's unsynced work and its parents stay`() = runTest {
        seedPatient("p-5", createdBy = COLLEAGUE, status = SYNCED)
        seedSession("s-5", userId = COLLEAGUE, patientId = "p-5", status = SYNCED)
        seedSample("smp-5", sessionId = "s-5", userId = COLLEAGUE, status = SampleStatus.VERIFIED)

        wipe()

        assertNotNull(db.sampleDao().getSampleByIdIncludingDeleted("smp-5"))
        assertNotNull(db.sessionDao().getSessionById("s-5"))
        assertNotNull(db.patientDao().getPatientById("p-5"))
    }

    @Test
    fun `it reports the files of the rows it removes, and only those`() = runTest {
        seedPatient("p-6", createdBy = SIGNED_OUT, status = SYNCED)
        seedSession("s-6", userId = SIGNED_OUT, patientId = "p-6", status = SYNCED)
        seedSample("smp-6", sessionId = "s-6", userId = SIGNED_OUT, status = SampleStatus.SYNCED)
        seedSample("smp-7", sessionId = "s-6", userId = SIGNED_OUT, status = SampleStatus.VERIFIED)
        seedReport("r-6", sessionId = "s-6", userId = SIGNED_OUT, status = SYNCED)
        seedReport("r-7", sessionId = "s-6", userId = SIGNED_OUT, status = PENDING)

        assertEquals(listOf("/files/smp-6.jpg"), wipeDao.getSyncedSampleImagePaths())
        assertEquals(
            listOf(ReportFilePaths(csvFilePath = "/docs/r-6.csv", pdfFilePath = "content://docs/r-6")),
            wipeDao.getSyncedReportFiles(),
        )
    }

    private suspend fun wipe() {
        wipeDao.deleteSyncedReports()
        wipeDao.deleteSyncedSamples()
        wipeDao.deleteSyncedSessions()
        wipeDao.deleteSyncedPatients()
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
        const val SIGNED_OUT = "user-signed-out"
        const val COLLEAGUE = "user-colleague"
        const val SYNCED = "synced"
        const val PENDING = "pending"
    }
}
