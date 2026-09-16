package com.agarthavision.domain.usecase.sessions

import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.repository.PsgcRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SearchBarangaysUseCase].
 */
class SearchBarangaysUseCaseTest {

    private val repository: PsgcRepository = mock()
    private val useCase = SearchBarangaysUseCase(repository)

    @Test
    fun `returns matches for a long enough query`() = runTest {
        whenever(repository.searchBarangays(any(), any())).thenReturn(listOf(adams))

        val result = useCase("adams")

        assertEquals(listOf(adams), result.getOrThrow())
        verify(repository).searchBarangays(
            query = eq("adams"),
            limit = eq(SearchBarangaysUseCase.RESULT_LIMIT),
        )
    }

    @Test
    fun `does not scan for a query shorter than the minimum`() = runTest {
        val result = useCase("a")

        assertTrue(result.getOrThrow().isEmpty())
        verify(repository, never()).searchBarangays(any(), any())
    }

    @Test
    fun `does not scan for a blank query`() = runTest {
        val result = useCase("   ")

        assertTrue(result.getOrThrow().isEmpty())
        verify(repository, never()).searchBarangays(any(), any())
    }

    @Test
    fun `trims before measuring and before searching`() = runTest {
        whenever(repository.searchBarangays(any(), any())).thenReturn(emptyList())

        useCase("  adams  ")

        verify(repository).searchBarangays(query = eq("adams"), limit = any())
    }

    @Test
    fun `wraps a repository failure instead of throwing`() = runTest {
        whenever(repository.searchBarangays(any(), any())).thenThrow(IllegalStateException("db gone"))

        val result = useCase("adams")

        assertTrue(result.isFailure)
    }

    private val adams = PsgcBarangay(
        code = "0102801001",
        name = "Adams",
        cityMuniName = "Adams",
        provinceName = "Ilocos Norte",
        regionName = "Region I (Ilocos Region)",
    )
}
