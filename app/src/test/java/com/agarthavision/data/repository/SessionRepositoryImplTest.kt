package com.agarthavision.data.repository

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for the visibility rules on [SessionRepositoryImpl].
 *
 * **The rule: a session belongs to the medtech who recorded it, and a signed-out reader sees
 * nothing rather than everything.** The paginated Session List used to fall through to a
 * predicate carrying the patient scope and the date range but no owner guard, so signing out
 * turned it into every smear recorded under that patient by anyone who had used the phone —
 * on a shared device, another medtech's work.
 *
 * These cases are about a *shared device*, which is the deployment this app is written for.
 * On a personal phone the leak is invisible, which is exactly why it survived review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionRepositoryImplTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao
    private lateinit var repository: SessionRepositoryImpl

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
        repository = SessionRepositoryImpl(sessionDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── the signed-out case ───────────────────────────────────────────────────

    @Test
    fun `a signed-out reader sees no sessions on a shared device`() = runTest {
        givenPatientWithSessions()

        assertTrue(page(userId = null).isEmpty())
    }

    @Test
    fun `a signed-out reader gets zeroed counts, not a count of everything`() = runTest {
        // The header is a separate query. If it kept counting while the list went empty, the
        // screen would read "3 sessions" over an empty list — visibly wrong, but only after
        // someone noticed, and it would still be leaking the number.
        givenPatientWithSessions()

        val counts = repository.observeVisibleSessionsCounts(
            userId = null,
            patientId = PATIENT,
            activeSessionId = null,
            sinceMillis = 0L,
            startMillis = null,
            endMillis = null,
            query = "",
        ).first()

        assertEquals(0, counts.totalCount)
    }

    @Test
    fun `observeVisibleSessions is empty when signed out`() = runTest {
        givenPatientWithSessions()

        assertTrue(repository.observeVisibleSessions(userId = null).first().isEmpty())
    }

    // ── the signed-in case still works ────────────────────────────────────────

    @Test
    fun `a medtech sees their own sessions for the patient`() = runTest {
        givenPatientWithSessions()

        assertEquals(listOf("s-a1", "s-a2"), page(USER_A).map { it.session.id }.sorted())
    }

    @Test
    fun `a medtech does not see another medtech's sessions for the same patient`() = runTest {
        givenPatientWithSessions()

        assertEquals(listOf("s-b1"), page(USER_B).map { it.session.id })
    }

    @Test
    fun `the list is scoped to the patient in the route`() = runTest {
        givenPatientWithSessions()
        patientDao.upsertPatient(patient(id = OTHER_PATIENT).toEntity())
        sessionDao.insertSession(session("s-a3", USER_A, OTHER_PATIENT))

        assertEquals(listOf("s-a1", "s-a2"), page(USER_A).map { it.session.id }.sorted())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun page(userId: String?) = repository.observeVisibleSessionsPage(
        userId = userId,
        patientId = PATIENT,
        activeSessionId = null,
        sinceMillis = 0L,
        startMillis = null,
        endMillis = null,
        query = "",
        limit = 50,
    ).first()

    /** One patient, two smears by medtech A and one by medtech B. */
    private suspend fun givenPatientWithSessions() {
        patientDao.upsertPatient(patient(id = PATIENT).toEntity())
        sessionDao.insertSession(session("s-a1", USER_A, PATIENT))
        sessionDao.insertSession(session("s-a2", USER_A, PATIENT))
        sessionDao.insertSession(session("s-b1", USER_B, PATIENT))
    }

    private fun session(id: String, userId: String, patientId: String) = SessionEntity(
        sessionId = id,
        userId = userId,
        patientId = patientId,
        deviceId = "device-1",
        startedAt = 1_700_000_000_000,
        label = "Smear $id",
    )

    private fun patient(id: String) = Patient(
        id = id,
        lastname = "Cruz",
        firstname = "Gerald",
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1998, 7, 30),
        psgcBarangayCode = "0102801001",
        createdBy = USER_A,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000),
    )

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
        const val PATIENT = "patient-1"
        const val OTHER_PATIENT = "patient-2"
    }
}
