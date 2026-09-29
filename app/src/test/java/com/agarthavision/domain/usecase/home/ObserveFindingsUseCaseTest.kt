package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.SessionFinding
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveFindingsUseCaseTest {

    private val detectionRepository: DetectionRepository = mock()
    private val useCase = ObserveFindingsUseCase(detectionRepository)
    private val window = TimeWindow(1_000L, 2_000L)

    @Test
    fun `a session with two positive frames of the same species counts once and aliases merge`() = runTest {
        val rows = listOf(
            SessionFinding(sessionId = "s1", rawSpecies = "Ascaris", townCode = "1001"),
            SessionFinding(sessionId = "s1", rawSpecies = "Ascaris lumbricoides", townCode = "1001"),
        )
        whenever(detectionRepository.observeSessionFindingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(rows))

        val result = useCase("user-1", window).first()

        assertEquals(1, result.positiveSmearsCount)
        assertEquals(1, result.species.size)
        val finding = result.species.single()
        assertEquals("Ascaris lumbricoides", finding.name)
        assertEquals(1, finding.count)
        assertEquals(1.0f, finding.ratio, 0.001f)
        assertEquals("100%", finding.formattedPercentage)
    }

    @Test
    fun `shares sum to 100 percent across multiple species and sessions`() = runTest {
        val rows = listOf(
            SessionFinding(sessionId = "s1", rawSpecies = "Ascaris lumbricoides", townCode = null),
            SessionFinding(sessionId = "s1", rawSpecies = "Trichuris trichiura", townCode = null),
            SessionFinding(sessionId = "s2", rawSpecies = "Ascaris", townCode = null),
        )
        whenever(detectionRepository.observeSessionFindingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(rows))

        val result = useCase("user-1", window).first()

        assertEquals(2, result.positiveSmearsCount)
        assertEquals(2, result.species.size)

        val ascaris = result.species.first { it.name == "Ascaris lumbricoides" }
        val trichuris = result.species.first { it.name == "Trichuris trichiura" }

        assertEquals(2, ascaris.count)
        assertEquals(1, trichuris.count)

        // Total pairs = 3 (s1-ascaris, s1-trichuris, s2-ascaris)
        assertEquals(2f / 3f, ascaris.ratio, 0.001f)
        assertEquals(1f / 3f, trichuris.ratio, 0.001f)
        assertTrue(ascaris.ratio + trichuris.ratio > 0.999f)
    }

    @Test
    fun `townCodes filter restricts results to matching towns`() = runTest {
        val rows = listOf(
            SessionFinding(sessionId = "s1", rawSpecies = "Hookworm", townCode = "town-A"),
            SessionFinding(sessionId = "s2", rawSpecies = "Ascaris", townCode = "town-B"),
            SessionFinding(sessionId = "s3", rawSpecies = "Ascaris", townCode = null),
        )
        whenever(detectionRepository.observeSessionFindingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(rows))

        val result = useCase("user-1", window, townCodes = setOf("town-A")).first()

        assertEquals(1, result.positiveSmearsCount)
        assertEquals(1, result.species.size)
        assertEquals("Hookworm", result.species.single().name)
    }

    @Test
    fun `empty findings yields zero smears and empty species list`() = runTest {
        whenever(detectionRepository.observeSessionFindingsBetween(eq("user-1"), any(), any()))
            .thenReturn(flowOf(emptyList()))

        val result = useCase("user-1", window).first()

        assertEquals(0, result.positiveSmearsCount)
        assertTrue(result.species.isEmpty())
    }
}
