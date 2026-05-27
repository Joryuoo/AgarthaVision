package com.agarthavision.domain.repository

/**
 * Repository contract for remote sample image access.
 */
interface SampleImageRepository {
    /**
     * Creates a short-lived signed URL for a private Supabase Storage object.
     */
    suspend fun createSignedImageUrl(storagePath: String): String
}
