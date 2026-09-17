package com.agarthavision.domain.usecase.patients

import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ObservePatientsUseCaseTest {

    private val patientRepository: PatientRepository = mock()
    private val psgcRepository: PsgcRepository = mock()
    private val useCase = ObservePatientsUseCase(patientRepository, psgcRepository)

    private fun patient(id: String, code: String = LAHUG) = Patient(
        id = id,
        lastname = "Cruz",
        firstname = "Gerald",
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1998, 7, 30),
        psgcBarangayCode = code,
        createdBy = "user-a",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun stubPage(patients: List<Patient>, total: Int = patients.size) {
        whenever(patientRepository.observePatients("user-a", "", 20, 0)).thenReturn(flowOf(patients))
        whenever(patientRepository.observePatientCount("user-a", "")).thenReturn(flowOf(total))
    }

    @Test
    fun `a wildcard typed into the search box is matched literally`() = runTest {
        // `_` is a single-character wildcard in LIKE. Unescaped, a medtech searching for a
        // surname that contains one gets everyone whose name is the same length, and a lone
        // `%` returns every patient on the device.
        whenever(patientRepository.observePatients("user-a", "de\\_la", 20, 0))
            .thenReturn(flowOf(listOf(patient("p-1"))))
        whenever(patientRepository.observePatientCount("user-a", "de\\_la")).thenReturn(flowOf(1))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        val result = useCase("user-a", PatientsQuery(query = "de_la")).first()

        assertEquals(1, result.items.size)
        // The page and the count run the same predicate, so both must see the same needle.
        verify(patientRepository).observePatients("user-a", "de\\_la", 20, 0)
        verify(patientRepository).observePatientCount("user-a", "de\\_la")
    }

    @Test
    fun `resolves each patient's barangay name for the row`() = runTest {
        stubPage(listOf(patient("p-1")))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        val result = useCase("user-a", PatientsQuery()).first()

        assertEquals("Lahug", result.items.single().barangayName)
        assertEquals(1, result.total)
    }

    @Test
    fun `an unresolvable code yields a null label rather than failing the page`() = runTest {
        stubPage(listOf(patient("p-1", code = "9999999999")))
        whenever(psgcRepository.getBarangay("9999999999")).thenReturn(null)

        val result = useCase("user-a", PatientsQuery()).first()

        // The row renders the raw code. A PSGC vintage change can retire one, and losing
        // the whole list over a single retired code would be the wrong trade.
        assertNull(result.items.single().barangayName)
        assertEquals(1, result.items.size)
    }

    @Test
    fun `one barangay shared by a page is looked up once`() = runTest {
        stubPage((1..5).map { patient("p-$it") })
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        val result = useCase("user-a", PatientsQuery()).first()

        assertEquals(5, result.items.size)
        // Patients cluster by barangay, so a filtered search is usually one code repeated
        // down the list. Five reads for one answer is the thing the memo avoids.
        verify(psgcRepository, times(1)).getBarangay(LAHUG)
    }

    @Test
    fun `distinct barangays are each looked up`() = runTest {
        stubPage(listOf(patient("p-1", LAHUG), patient("p-2", ADAMS)))
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())
        whenever(psgcRepository.getBarangay(ADAMS)).thenReturn(
            PsgcBarangay(ADAMS, "Adams", "Adams", "Ilocos Norte", "Region I (Ilocos Region)"),
        )

        val result = useCase("user-a", PatientsQuery()).first()

        assertEquals(listOf("Lahug", "Adams"), result.items.map { it.barangayName })
    }

    @Test
    fun `a null user yields nothing rather than everything on the device`() = runTest {
        val result = useCase(null, PatientsQuery()).first()

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.total)
        verify(patientRepository, never()).observePatients(any(), any(), any(), any())
    }

    @Test
    fun `the total is the filtered count, not the page size`() = runTest {
        stubPage((1..20).map { patient("p-$it") }, total = 57)
        whenever(psgcRepository.getBarangay(LAHUG)).thenReturn(lahug())

        val result = useCase("user-a", PatientsQuery()).first()

        // canLoadMore is items.size < total upstream, so a wrong total here silently
        // ends pagination at the first page.
        assertEquals(20, result.items.size)
        assertEquals(57, result.total)
    }

    private fun lahug() =
        PsgcBarangay(LAHUG, "Lahug", "City of Cebu", null, "Region VII (Central Visayas)")

    private companion object {
        const val LAHUG = "0723017001"
        const val ADAMS = "0102801001"
    }
}
