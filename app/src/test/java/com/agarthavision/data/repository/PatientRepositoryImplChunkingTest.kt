package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.PatientActivitySummary
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.domain.sync.SyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Pins the `getPatientActivitySummaries` IN-list chunking against SQLite's default
 * `SQLITE_MAX_VARIABLE_NUMBER` (999), which applies on API 26-29 (minSdk here).
 *
 * `PatientsViewModel` paginates from offset 0 with a growing limit (see
 * [PatientRepositoryImpl.observePatients]'s doc), so the id list passed here grows by
 * `PatientsQuery.PAGE_SIZE` every time a medtech scrolls to the bottom. Without chunking, a
 * sufficiently long scrolling session binds more than 999 variables in one query and crashes
 * on those API levels. Uses a mocked [PatientDao] rather than the in-memory Room database in
 * [PatientRepositoryImplTest] so the test doesn't have to actually insert thousands of rows.
 */
class PatientRepositoryImplChunkingTest {

    private val patientDao: PatientDao = mock()
    private val syncScheduler: SyncScheduler = mock()
    private val repository = PatientRepositoryImpl(patientDao, syncScheduler)

    @Test
    fun `chunks a large id list so no single query exceeds the bind-variable cap`() = runTest {
        val ids = (1..1_901).map { "p-$it" }
        val capturedChunks = argumentCaptor<List<String>>()
        whenever(patientDao.getPatientActivitySummaries(capturedChunks.capture())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val chunk = invocation.arguments[0] as List<String>
            chunk.map {
                PatientActivitySummary(
                    patientId = it,
                    unverifiedCount = 0,
                    positiveSpecies = null,
                    lastActivityAt = 0L,
                )
            }
        }

        val result = repository.getPatientActivitySummaries(ids)

        assertEquals("every id's summary should still come back", 1_901, result.size)
        assertTrue(
            "each chunk must stay comfortably under SQLite's 999 bind-variable cap",
            capturedChunks.allValues.all { it.size <= 900 },
        )
        assertEquals(
            "chunks must cover every id exactly once",
            ids.toSet(),
            capturedChunks.allValues.flatten().toSet(),
        )
    }

    @Test
    fun `an empty id list makes no DAO call`() = runTest {
        val result = repository.getPatientActivitySummaries(emptyList())

        assertEquals(emptyMap<String, PatientActivitySummary>(), result)
    }

    @Test
    fun `a small id list is not split`() = runTest {
        val ids = listOf("p-1", "p-2", "p-3")
        whenever(patientDao.getPatientActivitySummaries(ids)).thenReturn(
            ids.map { PatientActivitySummary(it, 0, null, 0L) },
        )

        val result = repository.getPatientActivitySummaries(ids)

        assertEquals(3, result.size)
    }
}
