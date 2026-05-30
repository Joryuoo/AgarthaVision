package com.agarthavision.data.repository

import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.domain.repository.SampleImageRepository
import javax.inject.Inject

/**
 * Supabase-backed implementation of sample image remote access.
 */
class SupabaseSampleImageRepository @Inject constructor(
    private val remoteDataSource: SampleRemoteDataSource,
) : SampleImageRepository {
    override suspend fun createSignedImageUrl(storagePath: String): String =
        remoteDataSource.createSignedSampleImageUrl(storagePath)
}
