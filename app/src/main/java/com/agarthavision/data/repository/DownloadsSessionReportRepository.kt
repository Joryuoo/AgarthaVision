package com.agarthavision.data.repository

import android.content.Context
import android.os.Environment
import com.agarthavision.domain.repository.SessionReportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Stores session CSV reports in the device Downloads directory.
 */
class DownloadsSessionReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionReportRepository {
    override suspend fun writeSessionCsv(sessionId: String, csv: String): String {
        val downloadsDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val safeSessionId = sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(downloadsDir, "agarthavision-session-$safeSessionId.csv")
        file.writeText(csv)
        return file.absolutePath
    }
}
