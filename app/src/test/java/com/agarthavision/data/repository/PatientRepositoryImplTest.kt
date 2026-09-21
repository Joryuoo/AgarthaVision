package com.agarthavision.data.repository

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.agarthavision.domain.sync.RecordingSyncScheduler

/**
 * In-memory Room tests for [PatientRepositoryImpl].
 *
 * The rule under test is the one that has to match Supabase exactly: **visibility resolves
 * through `patient_users`, not `created_by`.** A patient created by user A is invisible to
 * user B until a link row exists — and visible the moment one does, which is what an admin
 * sharing a patient looks like. Getting this wrong on the client gives the app a second,
 * quieter definition of who can see a patient than the RLS policy has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatientRepositoryImplTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var dao: PatientDao
    private val syncScheduler = RecordingSyncScheduler()

    private lateinit var repository: PatientRepositoryImpl

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.patientDao()
        repository = PatientRepositoryImpl(dao, syncScheduler)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun patient(
        id: String = "p-1",
        lastname: String = "Cruz",
        firstname: String = "Gerald",
        createdBy: String = USER_A,
    ) = Patient(
        id = id,
        lastname = lastname,
        firstname = firstname,
        middleName = "Mendoza",
        sex = Sex.MALE,
        birthdate = LocalDate.of(1998, 7, 30),
        psgcBarangayCode = "0102801001",
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000),
    )

    private suspend fun page(userId: String, query: String = "") =
        repository.observePatients(userId, query, limit = 50).first()

    // ── visibility resolves through patient_users ─────────────────────────────

    @Test
    fun `saving a patient asks for a sync pass`() = runTest {
        // A patient is the first thing the server needs: sessions.patient_id references it,
        // so a session pushed ahead of its patient is rejected. Asking here is what stops
        // that wait being "until somebody opens Settings".
        repository.insert(patient())
        assertEquals(1, syncScheduler.requests)

        repository.update(patient().copy(lastname = "Reyes"))
        assertEquals(2, syncScheduler.requests)
    }

    @Test
    fun `a patient created by user A is visible to user A`() = runTest {
        repository.insert(patient())

        assertEquals(listOf("p-1"), page(USER_A).map { it.id })
    }

    @Test
    fun `a patient created by user A is not visible to user B`() = runTest {
        repository.insert(patient())

        assertTrue(page(USER_B).isEmpty())
    }

    @Test
    fun `user B sees the patient once a link row shares it with them`() = runTest {
        repository.insert(patient())

        dao.linkPatientToUser(PatientUserEntity("p-1", USER_B, linkedAt = 1_700_000_000_000))

        assertEquals(listOf("p-1"), page(USER_B).map { it.id })
    }

    @Test
    fun `insert writes the creator link in the same transaction`() = runTest {
        repository.insert(patient())

        val links = dao.getLinksForUser(USER_A)
        assertEquals(1, links.size)
        assertEquals("p-1", links.first().patientId)
        assertEquals(1_700_000_000_000, links.first().linkedAt)
    }

    // ── filtering and counting ────────────────────────────────────────────────

    @Test
    fun `a blank query matches every visible patient`() = runTest {
        repository.insert(patient(id = "p-1", lastname = "Cruz"))
        repository.insert(patient(id = "p-2", lastname = "Santos"))

        assertEquals(2, page(USER_A).size)
    }

    @Test
    fun `the query filters on lastname`() = runTest {
        repository.insert(patient(id = "p-1", lastname = "Cruz"))
        repository.insert(patient(id = "p-2", lastname = "Santos"))

        assertEquals(listOf("p-2"), page(USER_A, query = "Santos").map { it.id })
    }

    @Test
    fun `the query filters on firstname`() = runTest {
        repository.insert(patient(id = "p-1", firstname = "Gerald"))
        repository.insert(patient(id = "p-2", firstname = "Maria"))

        assertEquals(listOf("p-2"), page(USER_A, query = "Maria").map { it.id })
    }

    @Test
    fun `the count matches the filtered page and is scoped the same way`() = runTest {
        repository.insert(patient(id = "p-1", lastname = "Cruz"))
        repository.insert(patient(id = "p-2", lastname = "Santos"))

        assertEquals(2, repository.observePatientCount(USER_A, "").first())
        assertEquals(1, repository.observePatientCount(USER_A, "Santos").first())
        assertEquals(0, repository.observePatientCount(USER_B, "").first())
    }

    // ── sorting ───────────────────────────────────────────────────────────────

    @Test
    fun `observePatients orders by recent activity by default`() = runTest {
        repository.insert(patient(id = "p-1", lastname = "Cruz").copy(updatedAt = Instant.ofEpochMilli(1_000)))
        repository.insert(patient(id = "p-2", lastname = "Santos").copy(updatedAt = Instant.ofEpochMilli(2_000)))

        val results = repository.observePatients(USER_A, "", 50).first()
        assertEquals(listOf("p-2", "p-1"), results.map { it.id })
    }

    @Test
    fun `observePatients orders by lastname when LAST_NAME sort requested`() = runTest {
        repository.insert(patient(id = "p-1", lastname = "Santos").copy(updatedAt = Instant.ofEpochMilli(2_000)))
        repository.insert(patient(id = "p-2", lastname = "Abad").copy(updatedAt = Instant.ofEpochMilli(1_000)))

        val results = repository.observePatients(
            USER_A,
            "",
            50,
            sort = com.agarthavision.domain.usecase.patients.PatientSort.LAST_NAME,
        ).first()
        assertEquals(listOf("p-2", "p-1"), results.map { it.id })
    }

    @Test
    fun `observePatients orders by firstname when FIRST_NAME sort requested`() = runTest {
        repository.insert(patient(id = "p-1", firstname = "Zoren").copy(updatedAt = Instant.ofEpochMilli(2_000)))
        repository.insert(patient(id = "p-2", firstname = "Ana").copy(updatedAt = Instant.ofEpochMilli(1_000)))

        val results = repository.observePatients(
            USER_A,
            "",
            50,
            sort = com.agarthavision.domain.usecase.patients.PatientSort.FIRST_NAME,
        ).first()
        assertEquals(listOf("p-2", "p-1"), results.map { it.id })
    }

    // ── single reads and update ───────────────────────────────────────────────

    @Test
    fun `getPatientById round-trips the domain model`() = runTest {
        repository.insert(patient())

        val loaded = repository.getPatientById("p-1")
        assertEquals("Cruz, Gerald M.", loaded?.displayName)
        assertEquals(LocalDate.of(1998, 7, 30), loaded?.birthdate)
    }

    @Test
    fun `getPatientById returns null for an unknown id`() = runTest {
        assertNull(repository.getPatientById("nope"))
    }

    @Test
    fun `observePatientById emits the stored patient`() = runTest {
        repository.insert(patient())

        assertEquals("p-1", repository.observePatientById("p-1").first()?.id)
    }

    @Test
    fun `update re-queues the row for sync`() = runTest {
        repository.insert(patient())
        dao.updateSyncStatus("p-1", "synced")

        repository.update(patient().copy(lastname = "Cruz-Reyes"))

        val stored = dao.getPatientById("p-1")
        assertEquals("Cruz-Reyes", stored?.lastname)
        assertEquals("pending", stored?.supabaseStatus)
        assertEquals(1, dao.getPatientsPendingSync(USER_A).size)
    }

    // ── the upsert must not cascade the link away ─────────────────────────────

    @Test
    fun `re-upserting a patient keeps its creator link, and the patient visible`() = runTest {
        repository.insert(patient())
        assertEquals(listOf("p-1"), page(USER_A).map { it.id })

        // What a pull does to a patient the device already holds.
        dao.upsertPatient(
            dao.getPatientById("p-1")!!.copy(lastname = "Cruz-Reyes", supabaseStatus = "synced"),
        )

        // Fails on @Insert(REPLACE): SQLite resolves the conflict by deleting the row, which
        // cascades patient_users away, and every read here resolves through that join. The
        // patient would still be on the device and invisible to the medtech who created it.
        assertEquals(listOf("p-1"), page(USER_A).map { it.id })
        assertEquals(listOf("p-1"), dao.getLinksForUser(USER_A).map { it.patientId })
        assertEquals("Cruz-Reyes", dao.getPatientById("p-1")?.lastname)
    }

    @Test
    fun `a bulk upsert keeps links too`() = runTest {
        repository.insert(patient(id = "p-1"))
        repository.insert(patient(id = "p-2", lastname = "Santos"))

        dao.upsertPatients(listOf(dao.getPatientById("p-1")!!, dao.getPatientById("p-2")!!))

        assertEquals(listOf("p-1", "p-2"), page(USER_A).map { it.id }.sorted())
    }

    // ── pending sync is scoped to the medtech ─────────────────────────────────

    @Test
    fun `getPatientsPendingSync returns only the calling medtech's rows`() = runTest {
        repository.insert(patient(id = "p-1", createdBy = USER_A))
        repository.insert(patient(id = "p-2", lastname = "Santos", createdBy = USER_B))

        // Unscoped, a sync pass run by A pushed B's offline patient under A's session; the
        // server rejects it and the row lands back here marked sync_failed.
        assertEquals(listOf("p-1"), dao.getPatientsPendingSync(USER_A).map { it.patientId })
        assertEquals(listOf("p-2"), dao.getPatientsPendingSync(USER_B).map { it.patientId })
    }

    @Test
    fun `getPatientsPendingSync includes a patient shared by an admin`() = runTest {
        repository.insert(patient(id = "p-1", createdBy = USER_A))
        dao.linkPatientToUser(PatientUserEntity("p-1", USER_B, linkedAt = 1_700_000_000_000))

        // Scoping on created_by instead of the join would hide it, which is the whole reason
        // the join table exists.
        assertEquals(listOf("p-1"), dao.getPatientsPendingSync(USER_B).map { it.patientId })
    }

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }
}
