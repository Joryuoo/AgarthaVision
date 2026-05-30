package com.agarthavision.data.repository

import android.content.Context
import android.os.Environment
import com.agarthavision.domain.repository.ReportFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Writes session report CSVs to the device Downloads directory. Keyed by
 * `reportId` so multiple reports for the same session don't collide.
 */
class DownloadsReportFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportFileStore {
    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String {
        val downloadsDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val safeSessionId = sessionId.sanitize()
        val safeReportId = reportId.sanitize()
        val file = File(downloadsDir, "agarthavision-session-$safeSessionId-$safeReportId.csv")
        file.writeText(csv)
        return file.absolutePath
    }

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9_-]"), "_")
}
