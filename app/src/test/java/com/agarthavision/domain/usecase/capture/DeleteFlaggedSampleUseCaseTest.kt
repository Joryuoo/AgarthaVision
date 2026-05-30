package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteFlaggedSampleUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDao: SampleDao = mock()
    private val sampleImageStore: SampleImageStore = mock()

    private val useCase = DeleteFlaggedSampleUseCase(
        sampleDao = sampleDao,
        sampleImageStore = sampleImageStore,
    )

    @Test
    fun `delete removes sample row and jpeg`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val sample = SampleEntity(
                sampleId = "sample-1",
                sessionId = "session-1",
                userId = "user-1",
                deviceId = "device-1",
                timestamp = 123L,
                imagePath = "/data/samples/sample-1.jpg",
                gpsLatitude = null,
                gpsLongitude = null,
                gpsAccuracy = null,
                status = SampleStatus.FLAGGED.value,
            )
            whenever(sampleDao.getSampleById("sample-1")).thenReturn(sample)

            useCase("sample-1")
            advanceUntilIdle()

            verify(sampleImageStore).deleteJpeg(sample.imagePath)
            verify(sampleDao).deleteSample("sample-1")
        }
}
