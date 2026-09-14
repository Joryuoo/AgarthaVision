package com.agarthavision.domain.usecase.reports

import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.InfectivityLevel
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionEggCountUseCaseTest {
    @Test
    fun `returns empty counts when no user session`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn(null)

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(0, result.totalEggCount)
        assertEquals(0, result.epg)
        assertEquals(emptyList<EggCount>(), result.counts)
        assertEquals(emptyMap<EggSpecies, Int>(), result.epgPerSpecies)
        assertNull(result.infectivityLevel)
        assertNull(result.topSpecies)
    }

    @Test
    fun `computes total eggs and epg from confirmed counts`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Ascaris", 2), EggCount("Trichuris", 1)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(3, result.totalEggCount)
        assertEquals(72, result.epg)
        assertEquals(2, result.counts.size)
    }

    @Test
    fun `low counts yield a low session tier and name the responsible species`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Ascaris", 2)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(48, result.epgPerSpecies[EggSpecies.ASCARIS])
        assertEquals(InfectivityLevel.LOW, result.infectivityLevel)
        assertEquals(EggSpecies.ASCARIS, result.topSpecies)
    }

    @Test
    fun `unrecognized species is excluded from the tier and epgPerSpecies map`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Some Unknown Parasite", 500_000)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(emptyMap<EggSpecies, Int>(), result.epgPerSpecies)
        assertNull(result.infectivityLevel)
        assertNull(result.topSpecies)
    }

    @Test
    fun `alias rows for the same species fold into one epgPerSpecies entry`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Ascaris", 1), EggCount("Ascaris lumbricoides", 1)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        // Both rows are the same canonical species, so they must fold into a single
        // epgPerSpecies entry keyed on EggSpecies.ASCARIS, not two separate counts.
        assertEquals(1, result.epgPerSpecies.size)
        assertEquals(48, result.epgPerSpecies[EggSpecies.ASCARIS])
        assertEquals(InfectivityLevel.LOW, result.infectivityLevel)
        assertEquals(EggSpecies.ASCARIS, result.topSpecies)
    }

    @Test
    fun `mixed species picks the higher tier not the higher raw epg for topSpecies`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        // Hookworm 1 egg -> epg 24 (Low). Ascaris 210 eggs -> epg 5,040 (Moderate). Moderate
        // beats Low even though it isn't the species with the fewest raw eggs either way.
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Hookworm", 1), EggCount("Ascaris", 210)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(InfectivityLevel.MODERATE, result.infectivityLevel)
        assertEquals(EggSpecies.ASCARIS, result.topSpecies)
    }

    @Test
    fun `zero confirmed eggs yields no infectivity level`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            emptyList(),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertNull(result.infectivityLevel)
        assertNull(result.topSpecies)
    }
}
