package com.agarthavision.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's copy of every sample JPEG, under `filesDir/users/<userId>/samples/<sampleId>.jpg`.
 *
 * **The path is derived, never invented.** That one property is what lets the sync prefetch ask
 * "do we already hold this image?" without a column, a migration or a manifest to fall out of
 * step with the disk: the answer is whether the file at [pathFor] exists. `samples.image_path`
 * stays the row's own record of where its frame is, but it is no longer the only way to find
 * one — which matters, because a pulled row arrives with it empty.
 */
@Singleton
class SampleImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun persistJpeg(userId: String, sampleId: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val file = File(pathFor(userId, sampleId)).also { it.parentFile?.mkdirs() }
            file.writeBytes(bytes)
            file.absolutePath
        }

    /**
     * Where this sample's JPEG lives, whether or not it is there yet.
     *
     * Pure, and deliberately not suspending: it touches no disk. Callers that need to know
     * whether the file exists ask [cachedPathOrNull].
     */
    fun pathFor(userId: String, sampleId: String): String =
        File(File(context.filesDir, "users/$userId/samples"), "$sampleId.jpg").absolutePath

    /** The path, when a readable file is actually there; null otherwise. */
    suspend fun cachedPathOrNull(userId: String, sampleId: String): String? =
        withContext(Dispatchers.IO) {
            pathFor(userId, sampleId).takeIf { File(it).isFile }
        }

    /** Bytes on disk for this sample's JPEG, or zero when it is not held. */
    suspend fun sizeOf(userId: String, sampleId: String): Long = withContext(Dispatchers.IO) {
        File(pathFor(userId, sampleId)).let { if (it.isFile) it.length() else 0L }
    }

    suspend fun deleteJpeg(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(imagePath).delete() }.getOrDefault(false)
    }

    /** Drops this sample's cached JPEG. Returns true when a file was actually removed. */
    suspend fun evict(userId: String, sampleId: String): Boolean =
        deleteJpeg(pathFor(userId, sampleId))
}
