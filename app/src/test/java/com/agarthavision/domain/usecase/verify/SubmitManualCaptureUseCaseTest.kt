package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.DetectionEntity
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitManualCaptureUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDao: SampleDao = mock()
    private val detectionDao: DetectionDao = mock()
    private val authRepository: AuthRepository = mock()
    private val locationProvider: LocationProvider = mock()
    private val syncSampleUseCase: SyncSampleUseCase = mock()

    private val useCase = SubmitManualCaptureUseCase(
        authRepository = authRepository,
        sampleDao = sampleDao,
        detectionDao = detectionDao,
        locationProvider = locationProvider,
        syncSampleUseCase = syncSampleUseCase,
    )

    private val frame = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = ByteArray(10),
        predictions = emptyList(),
    )

    @Test
    fun `manual submit writes sample and detection with null bbox`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val result = useCase(
                frame = frame,
                species = EggSpecies.ASCARIS,
                otherSpeciesText = "",
                userNote = "note",
                isRepeat = false,
            )
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(true),
                userNote = eq("note"),
                isRepeat = eq(false),
                gpsLatitude = isNull(),
                gpsLongitude = isNull(),
                gpsAccuracy = isNull(),
            )

            val detectionCaptor = argumentCaptor<DetectionEntity>()
            verify(detectionDao).insertDetection(detectionCaptor.capture())
            assertTrue(detectionCaptor.firstValue.bboxX == null)
            assertTrue(detectionCaptor.firstValue.bboxY == null)
            assertTrue(detectionCaptor.firstValue.bboxW == null)
            assertTrue(detectionCaptor.firstValue.bboxH == null)
        }

    @Test
    fun `manual submit uses other species text when selected`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(locationProvider.getCurrentLocation()).thenReturn(null)
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(syncSampleUseCase.invoke(any())).thenReturn(Result.success(Unit))

            val result = useCase(
                frame = frame,
                species = EggSpecies.OTHER,
                otherSpeciesText = "Taenia",
                userNote = null,
                isRepeat = false,
            )
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            verify(sampleDao).updateSampleOnVerify(
                sampleId = eq("sample-1"),
                status = eq(SampleStatus.VERIFIED.value),
                verifiedAt = any(),
                needsReannotation = eq(true),
                userNote = isNull(),
                isRepeat = eq(false),
                gpsLatitude = isNull(),
                gpsLongitude = isNull(),
                gpsAccuracy = isNull(),
            )
            val detectionCaptor = argumentCaptor<DetectionEntity>()
            verify(detectionDao).insertDetection(detectionCaptor.capture())
            assertEquals("Taenia", detectionCaptor.firstValue.classLabel)
            assertEquals("Taenia", detectionCaptor.firstValue.expertClass)
        }
}
