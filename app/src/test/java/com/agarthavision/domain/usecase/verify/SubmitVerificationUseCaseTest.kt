package com.agarthavision.domain.usecase.verify

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.data.repository.FlaggedFrameStore
import com.agarthavision.data.supabase.SyncSampleUseCase
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.LocationProvider
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitVerificationUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val authRepository: AuthRepository = mock()
    private val sampleImageStore: SampleImageStore = mock()
    private val flaggedFrameStore: FlaggedFrameStore = mock()
    private val locationProvider: LocationProvider = mock()
    private val deviceIdProvider: DeviceIdProvider = mock()
    private val syncSampleUseCase: SyncSampleUseCase = mock()

    private val useCase = SubmitVerificationUseCase(
        authRepository = authRepository,
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        sampleImageStore = sampleImageStore,
        flaggedFrameStore = flaggedFrameStore,
        locationProvider = locationProvider,
        deviceIdProvider = deviceIdProvider,
        syncSampleUseCase = syncSampleUseCase,
    )

    private val prediction = PredictionDto(
        classLabel = "Ascaris",
        confidence = 0.9f,
        x = 100f,
        y = 200f,
        width = 50f,
        height = 60f,
    )

    private val frame = FlaggedFrame(
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(10),
        predictions = listOf(prediction),
        inferenceModelVersion = "v2",
    )

    @Test
    fun `submit persists sample with VERIFIED status and one detection per box`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val answers = listOf(
                VerificationAnswers(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS)
            )
            val result = useCase(frame, answers, missedEgg = null)
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val captor = argumentCaptor<SampleEntity>()
            verify(sampleDao).insertSample(captor.capture())
            assertEquals(SampleStatus.VERIFIED.value, captor.firstValue.status)
            verify(detectionDao).insertDetections(any())
        }

    @Test
    fun `submit with all FALSE_POSITIVE still writes sample row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val answers = listOf(VerificationAnswers(isEgg = false))
            val result = useCase(frame, answers, missedEgg = null)
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            verify(sampleDao).insertSample(any())
            verify(flaggedFrameStore).remove(frame)
        }

    @Test
    fun `Q4 yes sets needsReannotation true on sample row`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val answers = listOf(
                VerificationAnswers(isEgg = true, isBoxCorrect = true, species = EggSpecies.ASCARIS)
            )
            useCase(frame, answers, missedEgg = true)
            advanceUntilIdle()

            val captor = argumentCaptor<SampleEntity>()
            verify(sampleDao).insertSample(captor.capture())
            assertTrue(captor.firstValue.needsReannotation)
        }

    @Test
    fun `submit succeeds with null GPS when LocationProvider returns null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val answers = listOf(VerificationAnswers(isEgg = false))
            val result = useCase(frame, answers, missedEgg = null)
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val captor = argumentCaptor<SampleEntity>()
            verify(sampleDao).insertSample(captor.capture())
            assertNull(captor.firstValue.gpsLatitude)
            assertNull(captor.firstValue.gpsLongitude)
        }
}
