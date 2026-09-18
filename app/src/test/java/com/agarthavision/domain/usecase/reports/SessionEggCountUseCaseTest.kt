package com.agarthavision.domain.usecase.reports

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionEggCountUseCaseTest {
    @Test
    fun `returns empty payload when no data available`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        val sampleRepository: SampleRepository = mock()
        val findingDao: SampleSpeciesFindingDao = mock()

        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(sampleRepository.getSamplesForSession("session-1", "user-1")).thenReturn(emptyList())
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(emptyList())
        whenever(findingDao.getFindingsForSession("session-1", "user-1")).thenReturn(emptyList())

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository, sampleRepository, findingDao)
        val result = useCase("session-1").getOrThrow()

        assertEquals(0, result.totalEggCount)
        assertEquals(1, result.fieldCount) // coerceAtLeast(1)
        assertEquals(0, result.lpfPerSpecies.size)
    }

    @Test
    fun `computes lpf density correctly across multiple fields`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        val sampleRepository: SampleRepository = mock()
        val findingDao: SampleSpeciesFindingDao = mock()

        whenever(authRepository.currentLocalUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1"))
            .thenReturn(emptyList())
        
        // 3 fields examined
        val samples = listOf(
            mockSample("s1"), mockSample("s2"), mockSample("s3")
        )
        whenever(sampleRepository.getSamplesForSession("session-1", "user-1")).thenReturn(samples)

        // Findings:
        // s1: Ascaris 2
        // s2: Ascaris 4, Hookworm 1
        // s3: clean (0 eggs)
        val findings = listOf(
            SampleSpeciesFindingEntity("f1", "s1", "Ascaris", null, 2),
            SampleSpeciesFindingEntity("f2", "s2", "Ascaris", null, 4),
            SampleSpeciesFindingEntity("f3", "s2", "Hookworm", null, 1),
        )
        whenever(findingDao.getFindingsForSession("session-1", "user-1")).thenReturn(findings)

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository, sampleRepository, findingDao)
        val result = useCase("session-1").getOrThrow()

        assertEquals(3, result.fieldCount)
        
        // Ascaris: mean (2+4+0)/3 = 2.0, min 0, max 4
        assertTrue("Ascaris should be in results", result.lpfPerSpecies.containsKey("Ascaris"))
        val ascaris = result.lpfPerSpecies["Ascaris"]!!
        assertEquals(2.0f, ascaris.mean, 0.01f)
        assertEquals(0, ascaris.min)
        assertEquals(4, ascaris.max)

        // Hookworm: mean (0+1+0)/3 = 0.33, min 0, max 1
        assertTrue("Hookworm should be in results", result.lpfPerSpecies.containsKey("Hookworm"))
        val hookworm = result.lpfPerSpecies["Hookworm"]!!
        assertEquals(0.33f, hookworm.mean, 0.01f)
        assertEquals(0, hookworm.min)
        assertEquals(1, hookworm.max)
    }

    private fun mockSample(id: String) = Sample(
        id = id,
        sessionId = "session-1",
        userId = "user-1",
        timestamp = 0,
        verifiedAt = 0,
        deviceId = "device-1",
        filePath = "path/to/file",
        status = com.agarthavision.domain.model.SampleStatus.VERIFIED,
        isManual = false,
        userNote = null,
        inferenceModelVersion = "v1"
    )
}
