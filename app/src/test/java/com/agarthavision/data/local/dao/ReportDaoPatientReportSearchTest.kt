package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
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
 * [ReportDao.observeFilteredReports] / [ReportDao.observeFilteredReportsCount] against a
 * patient-scoped report (`patient_id` set, `session_id` null, per 14zcqntj2uz), whose
 * `LEFT JOIN patients p ON p.patient_id = COALESCE(r.patient_id, s.patient_id)` has no
 * session row to fall back on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportDaoPatientReportSearchTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var reportDao: ReportDao
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reportDao = db.reportDao()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a patient report is found by the patient's lastname via patient_id, with no session to join through`() =
        runTest {
            seedPatient("patient-1", lastname = "Cruz", firstname = "Gerald")
            reportDao.insertReport(patientReport(id = "rpt-patient", patientId = "patient-1"))

            val results = reportDao.observeFilteredReports(
                userId = USER_ID,
                startMillis = null,
                endMillis = null,
                species = null,
                query = "Cruz",
                limit = 50,
                offset = 0,
            ).first()

            assertEquals(listOf("rpt-patient"), results.map { it.report.reportId })
            assertEquals("Cruz", results.single().patientLastname)
        }

    @Test
    fun `a patient report is found by the patient's firstname`() = runTest {
        seedPatient("patient-1", lastname = "Cruz", firstname = "Gerald")
        reportDao.insertReport(patientReport(id = "rpt-patient", patientId = "patient-1"))

        val count = reportDao.observeFilteredReportsCount(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "Gerald",
        ).first()

        assertEquals(1, count)
    }

    @Test
    fun `a search for another patient's name does not match a patient report`() = runTest {
        seedPatient("patient-1", lastname = "Cruz", firstname = "Gerald")
        reportDao.insertReport(patientReport(id = "rpt-patient", patientId = "patient-1"))

        val results = reportDao.observeFilteredReports(
            userId = USER_ID,
            startMillis = null,
            endMillis = null,
            species = null,
            query = "Santos",
            limit = 50,
            offset = 0,
        ).first()

        assertEquals(emptyList<String>(), results.map { it.report.reportId })
    }

    @Test
    fun `a session report and a patient report for different patients each match only their own name`() = runTest {
        seedPatient("patient-1", lastname = "Cruz", firstname = "Gerald")
        seedPatient("patient-2", lastname = "Santos", firstname = "Maria")
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = "sess-1",
                userId = USER_ID,
                patientId = "patient-1",
                deviceId = "device-1",
                startedAt = 1_000L,
            ),
        )
        reportDao.insertReport(
            ReportEntity(
                reportId = "rpt-session",
                sessionId = "sess-1",
                userId = USER_ID,
                generatedAt = 3_000L,
                totalSamples = 1,
                totalEggsConfirmed = 0,
                positiveSpeciesJson = "[]",
                lpfPerSpeciesJson = "{}",
                csvFilePath = null,
                pdfFilePath = null,
                createdAt = 3_000L,
            ),
        )
        reportDao.insertReport(patientReport(id = "rpt-patient", patientId = "patient-2"))

        val cruzResults = reportDao.observeFilteredReports(
            userId = USER_ID, startMillis = null, endMillis = null, species = null,
            query = "Cruz", limit = 50, offset = 0,
        ).first()
        val santosResults = reportDao.observeFilteredReports(
            userId = USER_ID, startMillis = null, endMillis = null, species = null,
            query = "Santos", limit = 50, offset = 0,
        ).first()

        assertEquals(listOf("rpt-session"), cruzResults.map { it.report.reportId })
        assertEquals(listOf("rpt-patient"), santosResults.map { it.report.reportId })
    }

    private suspend fun seedPatient(id: String, lastname: String, firstname: String) {
        patientDao.upsertPatient(
            PatientEntity(
                patientId = id,
                lastname = lastname,
                firstname = firstname,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = USER_ID,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
    }

    private fun patientReport(id: String, patientId: String) = ReportEntity(
        reportId = id,
        sessionId = null,
        patientId = patientId,
        sessionIdsJson = """["sess-a","sess-b"]""",
        userId = USER_ID,
        reportType = "patient",
        generatedAt = 3_000L,
        totalSamples = 2,
        totalEggsConfirmed = 1,
        positiveSpeciesJson = "[]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = null,
        pdfFilePath = null,
        createdAt = 3_000L,
    )

    private companion object {
        const val USER_ID = "user-a"
    }
}
