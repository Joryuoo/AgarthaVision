package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.data.local.entity.SpeciesSuggestionEntity
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
 * The "Other species" suggestion lookup.
 *
 * Free text is the only path by which a species outside `EggSpecies` enters the corpus, and
 * those rows double as the retraining set, so a lookup that quietly matches the wrong things
 * is worse than one that matches nothing.
 */
class SearchSpeciesSuggestionsUseCaseTest {

    private val dao: SpeciesSuggestionDao = mock()
    private val useCase = SearchSpeciesSuggestionsUseCase(dao)

    @Test
    fun `a query shorter than the minimum returns empty without touching the index`() = runTest {
        val result = useCase("a")

        assertEquals(emptyList<String>(), result.getOrThrow())
        verify(dao, never()).searchByPrefix(any(), any())
    }

    @Test
    fun `the needle is lowercased so the prefix match is case-insensitive`() = runTest {
        whenever(dao.searchByPrefix(any(), any())).thenReturn(emptyList())

        useCase("Asca")

        // Lowercased in Kotlin rather than by SQL lower(), which folds ASCII only.
        verify(dao).searchByPrefix(prefix = eq("asca"), limit = any())
    }

    @Test
    fun `a wildcard typed into the field is matched literally`() = runTest {
        whenever(dao.searchByPrefix(any(), any())).thenReturn(emptyList())

        useCase("asc%")

        // Unescaped, `%` would make every name in the index a match, and the medtech would
        // pick the first plausible one rather than the one they were typing.
        verify(dao).searchByPrefix(prefix = eq("asc\\%"), limit = any())
    }

    @Test
    fun `a failing index degrades to no suggestions rather than a thrown result`() = runTest {
        whenever(dao.searchByPrefix(any(), any())).thenThrow(RuntimeException("index gone"))

        val result = useCase("asca")

        // The field falls back to plain free text. Losing a verified frame because an
        // autocomplete failed would be far worse than the misspellings this prevents.
        assertTrue(result.isFailure)
    }

    @Test
    fun `results come back as plain names`() = runTest {
        whenever(dao.searchByPrefix(any(), any())).thenReturn(
            listOf(
                SpeciesSuggestionEntity(species = "Ascaris lumbricoides", lookup = "ascaris lumbricoides"),
                SpeciesSuggestionEntity(species = "Ascaris suum", lookup = "ascaris suum"),
            ),
        )

        val result = useCase("asca").getOrThrow()

        assertEquals(listOf("Ascaris lumbricoides", "Ascaris suum"), result)
    }
}
