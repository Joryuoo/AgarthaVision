package com.agarthavision.domain.usecase.sync

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.domain.model.SampleStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Drives the frame cache against a real database and a real filesystem (86d4by5n9).
 *
 * `CacheSampleImagesUseCaseTest` mocks the DAO, the store and the remote source, so it pins the
 * arithmetic and nothing else: that eviction *deletes a file*, that the row afterwards stops
 * pointing at one, and that an unpushed frame survives a pass that is over budget are all
 * claims about behaviour no mock can make. Those are the claims here.
 *
 * Robolectric gives `filesDir` a real temporary directory, so [SampleImageStore] is the
 * production class doing production IO, not a stand-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SampleImageCacheEvictionTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var sampleDao: SampleDao
    private lateinit var store: SampleImageStore
    private lateinit var useCase: CacheSampleImagesUseCase

    private val remote: SampleRemoteDataSource = mock<SampleRemoteDataSource>().also {
        // A one-byte frame: enough for a download to count as succeeded, so the pass reaches
        // its per-pass ceiling the way a real one does.
        runBlocking { whenever(it.downloadSampleImage(any())).thenReturn(byteArrayOf(1)) }
    }

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sampleDao = db.sampleDao()
        store = SampleImageStore(ctx)
        useCase = CacheSampleImagesUseCase(sampleDao, store, remote)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `the store round-trips a frame through the real filesystem`() = runTest {
        val bytes = ByteArray(64) { it.toByte() }

        val path = store.persistJpeg(USER_ID, "smp-1", bytes)

        assertEquals(path, store.cachedPathOrNull(USER_ID, "smp-1"))
        assertEquals(64L, store.sizeOf(USER_ID, "smp-1"))
        assertTrue(File(path).isFile)

        assertTrue(store.evict(USER_ID, "smp-1"))
        assertFalse(File(path).isFile)
        assertEquals(null, store.cachedPathOrNull(USER_ID, "smp-1"))
        assertEquals(0L, store.sizeOf(USER_ID, "smp-1"))
    }

    @Test
    fun `evicting a frame the device never held reports nothing removed`() = runTest {
        // Matters because the pass counts evictions from this return value. A store that
        // answered true for an absent file would report work it did not do.
        assertFalse(store.evict(USER_ID, "smp-never-here"))
    }

    @Test
    fun `eviction deletes the real file and stops the row pointing at it`() = runTest {
        seedSession()
        val oldestId = seedOverBudget()
        val oldestPath = store.persistJpeg(USER_ID, oldestId, byteArrayOf(9))
        sampleDao.updateImagePath(oldestId, oldestPath)

        val summary = useCase(USER_ID)

        assertEquals(1, summary.evicted)
        assertFalse("the evicted frame is still on disk", File(oldestPath).isFile)
        // Eviction removes a copy, never a record: the row survives and simply stops claiming a
        // file it no longer has, so reopening falls back to the signed Storage URL.
        assertEquals("", sampleDao.getSampleById(oldestId)?.imagePath)
        assertEquals(CacheSampleImagesUseCase.MAX_IMAGES_PER_PASS, summary.downloaded)
    }

    @Test
    fun `an unpushed sample's frame survives a pass that is over budget`() = runTest {
        seedSession()
        val oldestId = seedOverBudget()
        sampleDao.updateImagePath(oldestId, store.persistJpeg(USER_ID, oldestId, byteArrayOf(9)))

        // The only copy in existence: captured here, never pushed, so Storage cannot hand it
        // back. C8 says this file is a clinical record, not a cache entry.
        sampleDao.upsertSample(sample(id = UNPUSHED_ID, storagePath = null, verifiedAt = 1L))
        val unpushedPath = store.persistJpeg(USER_ID, UNPUSHED_ID, byteArrayOf(7))
        sampleDao.updateImagePath(UNPUSHED_ID, unpushedPath)

        val summary = useCase(USER_ID)

        // It is the oldest frame on the device and the pass is over budget, so every rule about
        // *when* to evict points at it. The rule about what may be evicted at all is why it is
        // still here - and the row still points at it.
        assertTrue("the only copy of an unpushed frame was deleted", File(unpushedPath).isFile)
        assertEquals(unpushedPath, sampleDao.getSampleById(UNPUSHED_ID)?.imagePath)
        assertEquals(1, summary.evicted)
        assertFalse(File(store.pathFor(USER_ID, oldestId)).isFile)
    }

    // ─────────────────────────────── helpers ─────────────────────────────────

    /**
     * Seeds exactly one sample more than the budget admits, and returns the id of the oldest —
     * the one the partition therefore drops.
     *
     * An uncached frame is charged [CacheSampleImagesUseCase.ASSUMED_IMAGE_BYTES], so the count
     * is derived from the two constants rather than written as a number that quietly stops
     * meaning "one over" the day either of them changes.
     */
    private suspend fun seedOverBudget(): String {
        val fitting =
            (CacheSampleImagesUseCase.MAX_CACHE_BYTES / CacheSampleImagesUseCase.ASSUMED_IMAGE_BYTES).toInt()
        for (i in 0..fitting) {
            // Descending verifiedAt, so the last one seeded is the oldest verification and
            // lands at the bottom of the newest-first order the query returns.
            sampleDao.upsertSample(
                sample(id = "smp-$i", storagePath = "users/user-a/smp-$i.jpg", verifiedAt = 100_000L - i),
            )
        }
        return "smp-$fitting"
    }

    private suspend fun seedSession() {
        patientDao().upsertPatient(
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
        db.sessionDao().upsertSession(
            SessionEntity(
                sessionId = SESSION_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 1_000L,
            ),
        )
    }

    private fun patientDao() = db.patientDao()

    private fun sample(
        id: String,
        storagePath: String?,
        verifiedAt: Long = 2_000L,
    ) = SampleEntity(
        sampleId = id,
        sessionId = SESSION_ID,
        userId = USER_ID,
        deviceId = "device-1",
        timestamp = 1_500L,
        verifiedAt = verifiedAt,
        imagePath = "",
        storagePath = storagePath,
        status = SampleStatus.SYNCED.value,
    )

    private companion object {
        const val PATIENT_ID = "patient-1"
        const val SESSION_ID = "session-1"
        const val USER_ID = "user-a"
        const val UNPUSHED_ID = "smp-unpushed"
    }
}
