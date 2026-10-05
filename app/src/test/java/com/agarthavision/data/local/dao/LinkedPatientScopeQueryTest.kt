package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.ReportEntity
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
 * In-memory Room tests for the two queries that scope the pull (14zcqntjt3p).
 *
 * Rule: a user is given the sessions, and the names of the colleagues, of the patients they are
 * assigned to, and nothing about a patient they are not, however much of it the device holds.
 *
 * Fixture: `user-1` is assigned to `pat-mine`, which `user-2` also works on. `pat-other` belongs
 * to `user-2` alone, and `user-3` only ever touched it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinkedPatientScopeQueryTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao
    private lateinit var reportDao: ReportDao
    private lateinit var colleagueDao: ColleagueDao

    @Before
    fun setUp() = runTest {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
        reportDao = db.reportDao()
        colleagueDao = db.colleagueDao()

        patientDao.upsertPatient(patient("pat-mine"))
        patientDao.upsertPatient(patient("pat-other"))
        patientDao.linkPatientsToUsers(
            listOf(
                PatientUserEntity("pat-mine", "user-1", 1_000L),
                PatientUserEntity("pat-mine", "user-2", 1_000L),
                PatientUserEntity("pat-other", "user-2", 1_000L),
            ),
        )
        sessionDao.upsertSession(session("s-mine-own", "pat-mine", userId = "user-1"))
        sessionDao.upsertSession(session("s-mine-colleague", "pat-mine", userId = "user-2"))
        sessionDao.upsertSession(session("s-other", "pat-other", userId = "user-2"))
        sessionDao.upsertSession(session("s-other-3", "pat-other", userId = "user-3"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the sessions of the user's own patients, whoever wrote them, and no others`() = runTest {
        assertEquals(
            setOf("s-mine-own", "s-mine-colleague"),
            sessionDao.getSessionIdsOnLinkedPatients("user-1").toSet(),
        )
    }

    @Test
    fun `a user with no assignments is given no sessions, however many the device holds`() = runTest {
        assertEquals(emptyList<String>(), sessionDao.getSessionIdsOnLinkedPatients("user-9"))
    }

    @Test
    fun `colleagues are the other authors on the user's own patients`() = runTest {
        // user-3 wrote only on pat-other, which user-1 is not assigned to.
        assertEquals(setOf("user-2"), colleagueDao.getColleagueIdsOnLinkedPatients("user-1").toSet())
    }

    @Test
    fun `a report's author counts as a colleague, as 0008 has it`() = runTest {
        reportDao.insertReport(report("r-1", sessionId = "s-mine-own", userId = "user-4"))
        reportDao.insertReport(report("r-2", sessionId = "s-other", userId = "user-5"))

        assertEquals(
            setOf("user-2", "user-4"),
            colleagueDao.getColleagueIdsOnLinkedPatients("user-1").toSet(),
        )
    }

    @Test
    fun `the user is never their own colleague`() = runTest {
        reportDao.insertReport(report("r-1", sessionId = "s-mine-colleague", userId = "user-1"))

        assertEquals(setOf("user-2"), colleagueDao.getColleagueIdsOnLinkedPatients("user-1").toSet())
    }
}

private fun patient(id: String) = PatientEntity(
    patientId = id,
    lastname = "Cruz",
    firstname = "Gerald",
    middleName = null,
    sex = "M",
    birthdate = 0L,
    psgcBarangayCode = "0102801001",
    createdBy = "user-2",
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

private fun session(id: String, patientId: String, userId: String) = SessionEntity(
    sessionId = id,
    userId = userId,
    patientId = patientId,
    deviceId = "device-1",
    startedAt = 1_000L,
    label = id,
)

private fun report(id: String, sessionId: String, userId: String) = ReportEntity(
    reportId = id,
    sessionId = sessionId,
    userId = userId,
    generatedAt = 1_000L,
    totalSamples = 0,
    totalEggsConfirmed = 0,
    positiveSpeciesJson = "[]",
    lpfPerSpeciesJson = "{}",
    csvFilePath = null,
    pdfFilePath = null,
    createdAt = 1_000L,
)
