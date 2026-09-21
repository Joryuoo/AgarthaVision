package com.agarthavision.data.local.dao

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SampleStatus
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
 * Pins `SampleDao.getCacheableSamples`, the query that decides which frames the sync prefetch
 * may hold and, more consequentially, which it may throw away (86d4by5n9).
 *
 * This SQL had no test that executed it. `CacheSampleImagesUseCaseTest` mocks the DAO, so it
 * checks the arithmetic of the budget against whatever list the mock is told to return — which
 * left the one rule that keeps eviction from destroying a clinical record stated in a WHERE
 * clause and verified nowhere.
 *
 * **The rule: an unpushed sample is never a candidate.** Its local JPEG is the only copy in
 * existence until it reaches Storage, so evicting it is not cache management, it is deleting
 * the record (C8). A non-empty `storage_path` is the whole of what stands between those two
 * things, and `TRIM` is why a path of spaces does not count as one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SampleDaoCacheableSamplesTest {

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
    fun `a sample the server does not hold is never a candidate`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(id = "smp-unpushed", storagePath = null))
        sampleDao.upsertSample(sample(id = "smp-pushed", storagePath = "users/user-a/smp.jpg"))

        val candidates = sampleDao.getCacheableSamples(USER_ID)

        // C8. The pushed one may be evicted because the server can hand it back; the unpushed
        // one may not, because nothing can.
        assertEquals(listOf("smp-pushed"), candidates.map { it.sampleId })
    }

    @Test
    fun `a storage path of only whitespace is not a storage path`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(id = "smp-blank", storagePath = "   "))

        // The TRIM in the query, which is the part a mocked DAO cannot check. Without it a row
        // whose push half-failed would read as safely evictable.
        assertTrue(sampleDao.getCacheableSamples(USER_ID).isEmpty())
    }

    @Test
    fun `a soft-deleted sample is never a candidate`() = runTest {
        seedSession()
        sampleDao.upsertSample(
            sample(id = "smp-deleted", storagePath = "users/user-a/smp.jpg").copy(deletedAt = 9_000L),
        )

        assertTrue(sampleDao.getCacheableSamples(USER_ID).isEmpty())
    }

    @Test
    fun `another medtech's samples are not candidates`() = runTest {
        seedSession()
        sampleDao.upsertSample(
            sample(id = "smp-theirs", storagePath = "users/user-b/smp.jpg").copy(userId = "user-b"),
        )

        // A shared device holds more than one person's work. Spending this user's budget on
        // someone else's frames, or evicting them, is not this pass's business.
        assertTrue(sampleDao.getCacheableSamples(USER_ID).isEmpty())
    }

    @Test
    fun `candidates come back newest verification first`() = runTest {
        seedSession()
        sampleDao.upsertSample(sample(id = "smp-old", storagePath = "p", verifiedAt = 1_000L))
        sampleDao.upsertSample(sample(id = "smp-new", storagePath = "p", verifiedAt = 3_000L))
        sampleDao.upsertSample(sample(id = "smp-mid", storagePath = "p", verifiedAt = 2_000L))

        // The order *is* the retention policy: the budget fills from the top of this list and
        // evicts from the bottom, so "newest verification first" has to come from the SQL
        // rather than from whatever order the rows happened to be inserted in.
        assertEquals(
            listOf("smp-new", "smp-mid", "smp-old"),
            sampleDao.getCacheableSamples(USER_ID).map { it.sampleId },
        )
    }

    @Test
    fun `an unverified sample sorts behind every verified one, on capture time`() = runTest {
        seedSession()
        sampleDao.upsertSample(
            sample(id = "smp-unverified-late", storagePath = "p", verifiedAt = 0L, timestamp = 8_000L),
        )
        sampleDao.upsertSample(
            sample(id = "smp-unverified-early", storagePath = "p", verifiedAt = 0L, timestamp = 7_000L),
        )
        sampleDao.upsertSample(
            sample(id = "smp-verified", storagePath = "p", verifiedAt = 1L, timestamp = 1_000L),
        )

        // `verified_at` defaults to 0 rather than NULL, so an unverified row cannot outrank a
        // verified one no matter how recently it was captured - and the timestamp tiebreak
        // orders them among themselves. A frame nobody has adjudicated is the first thing the
        // budget should give up.
        assertEquals(
            listOf("smp-verified", "smp-unverified-late", "smp-unverified-early"),
            sampleDao.getCacheableSamples(USER_ID).map { it.sampleId },
        )
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

    private fun sample(
        id: String,
        storagePath: String?,
        verifiedAt: Long = 2_000L,
        timestamp: Long = 1_500L,
    ) = SampleEntity(
        sampleId = id,
        sessionId = SESSION_ID,
        userId = USER_ID,
        deviceId = "device-1",
        timestamp = timestamp,
        verifiedAt = verifiedAt,
        imagePath = "/tmp/$id.jpg",
        storagePath = storagePath,
        status = SampleStatus.SYNCED.value,
    )

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
        const val USER_ID = "user-a"
    }
}
