package com.agarthavision.data.repository

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.sync.RecordingSyncScheduler
import java.time.Instant
import java.time.LocalDate
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
 * Exercises [PatientRepositoryImpl.observeAddedActivity]'s PII masking end to end (real
 * [com.agarthavision.core.util.NameMasking], not a fake) rather than trusting the
 * implementation's own claim that codenames are left alone and real names are masked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatientRepositoryImplActivityMaskingTest {

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
        repository = PatientRepositoryImpl(dao, RecordingSyncScheduler())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun patient(id: String, lastname: String, firstname: String) = Patient(
        id = id,
        lastname = lastname,
        firstname = firstname,
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1998, 7, 30),
        psgcBarangayCode = "0102801001",
        createdBy = "user-a",
        createdAt = Instant.ofEpochMilli(1_000L),
        updatedAt = Instant.ofEpochMilli(1_000L),
    )

    @Test
    fun `a real patient name is masked in the activity feed`() = runTest {
        repository.insert(patient("p-1", lastname = "Escolano", firstname = "Joseph"))

        val rows = repository.observeAddedActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        val item = rows.first() as ActivityItem.PatientAdded
        // NameMasking keeps first+last char, replaces the middle with bullets, uppercased.
        assertEquals("E••O, J••H", item.maskedName)
    }

    @Test
    fun `a codename patient name is left unmasked in the activity feed`() = runTest {
        repository.insert(patient("p-2", lastname = "ALPHA-M24", firstname = ""))

        val rows = repository.observeAddedActivity("user-a", 10).first()

        assertEquals(1, rows.size)
        val item = rows.first() as ActivityItem.PatientAdded
        assertEquals("ALPHA-M24", item.maskedName)
    }
}
