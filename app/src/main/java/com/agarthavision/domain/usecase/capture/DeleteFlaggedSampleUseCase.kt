package com.agarthavision.domain.usecase.capture

import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import javax.inject.Inject

class DeleteFlaggedSampleUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val sampleImageStore: SampleImageStore,
) {
    suspend operator fun invoke(sampleId: String) {
        val sample = sampleDao.getSampleById(sampleId) ?: return
        sampleImageStore.deleteJpeg(sample.imagePath)
        sampleDao.deleteSample(sampleId)
    }
}
