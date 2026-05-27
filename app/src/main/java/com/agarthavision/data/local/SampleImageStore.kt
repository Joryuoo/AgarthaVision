package com.agarthavision.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun persistJpeg(userId: String, sampleId: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "users/$userId/samples").also { it.mkdirs() }
        val file = File(dir, "$sampleId.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
