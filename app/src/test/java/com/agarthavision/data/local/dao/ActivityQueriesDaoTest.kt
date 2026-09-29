package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
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
 * In-memory Room tests for the recent-activity DAO queries added for the Home activity feed:
 * [SampleDao.observeCaptureActivity], [SampleDao.observeVerifyActivity],
 * [PatientDao.observeAddedActivity].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityQueriesDaoTest {

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
    fun tearDown() {
        db.close()
    }

    private suspend fun seedPatientAndSession(
        patientId: String = "patient-1",
        sessionId: String = "session-1",
        userId: String = "user-a",
    ) {
        patientDao.upsertPatient(
            PatientEntity(
                patientId = patientId,
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = userId,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        sessionDao.upsertSession(
            SessionEntity(
                sessionId = sessionId,
                userId = userId,
                patientId = patientId,
                deviceId = "device-1",
                startedAt = 1_000L,
                label = "Smear 1",
            ),
        )
    }

    /** All rows in this file belong to the fixed `session-1` / `user-a` pair seeded above. */
    private fun sample(
        id: String,
        timestamp: Long,
        verifiedAt: Long = 0L,
        status: String = "pending_verification",
        deletedAt: Long? = null,
    ) = SampleEntity(
        sampleId = id,
        sessionId = "session-1",
        userId = "user-a",
        deviceId = "device-1",
        timestamp = timestamp,
        verifiedAt = verifiedAt,
        imagePath = "/tmp/$id.jpg",
        storagePath = null,
        status = status,
        deletedAt = deletedAt,
    )

    // ───────────────────── observeCaptureActivity ─────────────────────

    @Test
    fun `observeCaptureActivity excludes soft-deleted samples`() = runTest {
        seedPatientAndSession()
        sampleDao.upsertSample(sample("smp-1", timestamp = 1_000L))
        sampleDao.upsertSample(
            sample("smp-2", timestamp = 2_000L, deletedAt = 5_000L),
        )

        val rows = sampleDao.observeCaptureActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        assertEquals(1, rows.first().frameCount)
    }

    @Test
    fun `observeCaptureActivity groups two captures same session same Manila day into one row`() = runTest {
        seedPatientAndSession()
        // Manila is UTC+8. Use two timestamps clearly on the same UTC+8 calendar day.
        val morning = 1_700_000_000_000L // 2023-11-15 06:13:20 UTC -> 14:13:20 Manila
        val laterSameDay = morning + 60_000L
        sampleDao.upsertSample(sample("smp-1", timestamp = morning))
        sampleDao.upsertSample(sample("smp-2", timestamp = laterSameDay))

        val rows = sampleDao.observeCaptureActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        assertEquals(2, rows.first().frameCount)
        assertEquals(laterSameDay, rows.first().occurredAt)
    }

    @Test
    fun `observeCaptureActivity splits captures across a Manila-day boundary into two rows`() = runTest {
        seedPatientAndSession()
        // 2023-11-14 23:00:00 Manila and 2023-11-15 01:00:00 Manila (two Manila calendar days).
        val manilaOffsetMillis = 8L * 60 * 60 * 1000
        val day1Manila2300 = java.time.LocalDate.of(2023, 11, 14)
            .atTime(23, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L - manilaOffsetMillis
        val day2Manila0100 = java.time.LocalDate.of(2023, 11, 15)
            .atTime(1, 0).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L - manilaOffsetMillis

        sampleDao.upsertSample(sample("smp-1", timestamp = day1Manila2300))
        sampleDao.upsertSample(sample("smp-2", timestamp = day2Manila0100))

        val rows = sampleDao.observeCaptureActivity("user-a", 10).first()

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.frameCount == 1 })
    }

    // ───────────────────── observeVerifyActivity ─────────────────────

    @Test
    fun `observeVerifyActivity excludes flagged samples`() = runTest {
        seedPatientAndSession()
        sampleDao.upsertSample(
            sample(
                "smp-1",
                timestamp = 1_000L,
                verifiedAt = 2_000L,
                status = "flagged",
            ),
        )

        val rows = sampleDao.observeVerifyActivity("user-a", 10).first()

        assertTrue("flagged samples must not count as verify-activity", rows.isEmpty())
    }

    @Test
    fun `observeVerifyActivity excludes samples with verified_at of 0`() = runTest {
        seedPatientAndSession()
        sampleDao.upsertSample(
            sample(
                "smp-1",
                timestamp = 1_000L,
                verifiedAt = 0L,
                status = "synced",
            ),
        )

        val rows = sampleDao.observeVerifyActivity("user-a", 10).first()

        assertTrue("unverified samples (verified_at = 0) must not count as verify-activity", rows.isEmpty())
    }

    @Test
    fun `observeVerifyActivity excludes soft-deleted samples`() = runTest {
        seedPatientAndSession()
        sampleDao.upsertSample(
            sample(
                "smp-1",
                timestamp = 1_000L,
                verifiedAt = 2_000L,
                status = "synced",
                deletedAt = 3_000L,
            ),
        )

        val rows = sampleDao.observeVerifyActivity("user-a", 10).first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `observeVerifyActivity counts a normally verified sample`() = runTest {
        seedPatientAndSession()
        sampleDao.upsertSample(
            sample(
                "smp-1",
                timestamp = 1_000L,
                verifiedAt = 2_000L,
                status = "synced",
            ),
        )

        val rows = sampleDao.observeVerifyActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        assertEquals(1, rows.first().frameCount)
        assertEquals(2_000L, rows.first().occurredAt)
    }

    // ───────────────────── observeAddedActivity ─────────────────────

    @Test
    fun `observeAddedActivity excludes a patient shared with the caller but created by someone else`() = runTest {
        // Patient created by user-b, then shared (linked) to user-a.
        patientDao.upsertPatient(
            PatientEntity(
                patientId = "patient-shared",
                lastname = "Reyes",
                firstname = "Ana",
                middleName = null,
                sex = "F",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = "user-b",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        patientDao.linkPatientToUser(
            PatientUserEntity(patientId = "patient-shared", userId = "user-a", linkedAt = 1_500L),
        )
        patientDao.linkPatientToUser(
            PatientUserEntity(patientId = "patient-shared", userId = "user-b", linkedAt = 1_000L),
        )

        val rowsForA = patientDao.observeAddedActivity("user-a", 10).first()
        val rowsForB = patientDao.observeAddedActivity("user-b", 10).first()

        assertTrue(
            "a patient created by user-b and merely shared with user-a must not appear as " +
                "user-a's own 'added patient' activity",
            rowsForA.isEmpty(),
        )
        assertEquals(1, rowsForB.size)
    }

    @Test
    fun `observeAddedActivity returns a patient the caller actually created`() = runTest {
        patientDao.insertPatientWithCreatorLink(
            PatientEntity(
                patientId = "patient-own",
                lastname = "Cruz",
                firstname = "Gerald",
                middleName = null,
                sex = "M",
                birthdate = 0L,
                psgcBarangayCode = "0102801001",
                createdBy = "user-a",
                createdAt = 4_000L,
                updatedAt = 4_000L,
            ),
            linkedAt = 4_000L,
        )

        val rows = patientDao.observeAddedActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        assertEquals("patient-own", rows.first().patientId)
    }
}
