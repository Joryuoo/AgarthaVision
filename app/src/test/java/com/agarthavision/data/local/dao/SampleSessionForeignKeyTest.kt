package com.agarthavision.data.local.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
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
 * Pins the `samples.session_id` foreign key added in Room 16.
 *
 * The server has always declared it (`0001_init.sql:183`) while Room declared no foreign key on
 * `samples` at all, so a sample could sit on the device pointing at a session id that resolved to
 * nothing. Every other parent/child pair here declared one; this table was the gap.
 *
 * Without these tests the constraint is invisible to the suite - the existing DAO tests happen to
 * seed a session before inserting samples, so they would keep passing if someone removed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SampleSessionForeignKeyTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sampleDao: SampleDao
    private lateinit var sessionDao: SessionDao
    private lateinit var patientDao: PatientDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sampleDao = db.sampleDao()
        sessionDao = db.sessionDao()
        patientDao = db.patientDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a sample cannot be inserted for a session that does not exist`() = runTest {
        // The whole point of the key. Before Room 16 this insert succeeded and left an orphan
        // that every query not joining sessions would happily return.
        val error = runCatching { sampleDao.upsertSample(sample(sessionId = "no-such-session")) }
            .exceptionOrNull()

        assertTrue(
            "expected a foreign-key violation, got $error",
            error is SQLiteConstraintException,
        )
    }

    @Test
    fun `deleting a session that still owns samples is refused rather than cascading`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(sessionId = SESSION_ID))

        // NO_ACTION, not CASCADE, and deliberately so: a cascade would take the sample and its
        // detections with the session, which is what C8 and docs/non-negotiables.md forbid. The
        // caller has to deal with the samples first - DiscardUnsyncedDataUseCase already does.
        val error = runCatching { sessionDao.deleteSession(SESSION_ID) }.exceptionOrNull()

        assertTrue(
            "expected a foreign-key violation, got $error",
            error is SQLiteConstraintException,
        )
        assertEquals(1, sampleDao.getSamplesPendingSyncIncludingDeleted(USER_ID).size)
    }

    @Test
    fun `a sample inserts normally once its session exists`() = runTest {
        seedSession()

        sampleDao.upsertSample(sample(sessionId = SESSION_ID))

        assertEquals(1, sampleDao.getSamplesPendingSyncIncludingDeleted(USER_ID).size)
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    private suspend fun seedSession() {
        // The patient goes in first: sessions.patient_id is a foreign key onto patients, so an
        // unseeded patient fails the session insert before any assertion here runs.
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
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = SESSION_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 1_000L,
            ),
        )
    }

    private fun sample(sessionId: String) = SampleEntity(
        sampleId = "smp-1",
        sessionId = sessionId,
        userId = USER_ID,
        deviceId = "device-1",
        timestamp = 1_500L,
        verifiedAt = 2_000L,
        imagePath = "/tmp/smp-1.jpg",
        storagePath = null,
        status = "verified",
    )

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
        const val USER_ID = "user-a"
    }
}
