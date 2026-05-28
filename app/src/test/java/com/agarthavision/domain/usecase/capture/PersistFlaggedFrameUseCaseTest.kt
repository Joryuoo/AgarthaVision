package com.agarthavision.domain.usecase.capture

import com.agarthavision.core.util.DeviceIdProvider
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.remote.dto.PredictionDto
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.util.MainDispatcherRule
import com.google.gson.Gson
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PersistFlaggedFrameUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mock()
    private val sampleDao: SampleDao = mock()
    private val sampleImageStore: SampleImageStore = mock()
    private val deviceIdProvider: DeviceIdProvider = mock()
    private val gson = Gson()

    private val useCase = PersistFlaggedFrameUseCase(
        authRepository = authRepository,
        sampleDao = sampleDao,
        sampleImageStore = sampleImageStore,
        deviceIdProvider = deviceIdProvider,
        gson = gson,
    )

    private val prediction = PredictionDto(
        classLabel = "Ascaris",
        confidence = 0.91f,
        x = 10f,
        y = 20f,
        width = 30f,
        height = 40f,
    )

    @Test
    fun `persist stores flagged sample with predictions JSON`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")

            val frame = FlaggedFrame(
                sessionId = "session-1",
                capturedAt = Instant.EPOCH,
                jpegBytes = ByteArray(5),
                predictions = listOf(prediction),
                inferenceModelVersion = "v2",
                imageWidth = 1024,
                imageHeight = 768,
            )

            val sampleId = useCase(frame)
            advanceUntilIdle()

            val sampleCaptor = argumentCaptor<SampleEntity>()
            verify(sampleDao).insertSample(sampleCaptor.capture())
            verify(sampleImageStore).persistJpeg(eq("user-1"), eq(sampleId), any())

            val sample = sampleCaptor.firstValue
            assertEquals(sampleId, sample.sampleId)
            assertEquals(SampleStatus.FLAGGED.value, sample.status)
            assertEquals("/data/samples/id.jpg", sample.imagePath)
            assertEquals("v2", sample.inferenceModelVersion)
            assertEquals(1024, sample.imageWidth)
            assertEquals(768, sample.imageHeight)
            assertTrue(sample.predictionsJson?.contains("Ascaris") == true)
            assertTrue(!sample.isManual)
        }

    @Test
    fun `persist marks manual samples and omits predictions JSON`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
            whenever(deviceIdProvider.id).thenReturn("device-1")
            whenever(sampleImageStore.persistJpeg(any(), any(), any())).thenReturn("/data/samples/id.jpg")

            val frame = FlaggedFrame(
                sessionId = "session-1",
                capturedAt = Instant.EPOCH,
                jpegBytes = ByteArray(5),
                predictions = emptyList(),
                source = FrameSource.MANUAL,
            )

            val sampleId = useCase(frame)
            advanceUntilIdle()

            val sampleCaptor = argumentCaptor<SampleEntity>()
            verify(sampleDao).insertSample(sampleCaptor.capture())
            verify(sampleImageStore).persistJpeg(eq("user-1"), eq(sampleId), any())

            val sample = sampleCaptor.firstValue
            assertEquals(sampleId, sample.sampleId)
            assertEquals("manual", sample.inferenceModelVersion)
            assertTrue(sample.isManual)
            assertNull(sample.predictionsJson)
        }
}
