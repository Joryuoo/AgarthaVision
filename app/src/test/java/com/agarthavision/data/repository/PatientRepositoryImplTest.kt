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
    private lateinit var repository: PatientRepositoryImpl

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.patientDao()
        repository = PatientRepositoryImpl(dao)
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
        repository.observePatients(userId, query, limit = 50, offset = 0).first()

    // ── visibility resolves through patient_users ─────────────────────────────

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
        assertEquals(1, dao.getPatientsPendingSync().size)
    }

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }
}
