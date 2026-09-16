package com.agarthavision.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.agarthavision.domain.repository.ReportFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Writes session report CSVs and PDFs to `Documents/AgarthaVision/`, keyed by `reportId` so
 * multiple reports for the same session don't collide.
 *
 * Two paths, because scoped storage changed how this works:
 * - **API 29+** goes through [MediaStore], the only way to create a folder in shared
 *   storage. Returns a `content://` URI.
 * - **API 26–28** writes a plain [File], covered by the `WRITE_EXTERNAL_STORAGE`
 *   declaration capped at `maxSdkVersion="28"`. Returns an absolute path.
 *
 * Callers must therefore treat the returned string as opaque and hand it to
 * `shareReportCsv`, which knows both shapes.
 */
class DocumentsReportFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportFileStore {

    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String {
        val fileName = "agarthavision-session-${sessionId.sanitize()}-${reportId.sanitize()}.csv"
        return writeBytes(fileName, CSV_MIME_TYPE, csv.toByteArray())
    }

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String {
        val fileName = "agarthavision-session-${sessionId.sanitize()}-${reportId.sanitize()}.pdf"
        return writeBytes(fileName, PDF_MIME_TYPE, pdf)
    }

    private fun writeBytes(fileName: String, mimeType: String, bytes: ByteArray): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(fileName, mimeType, bytes)
        } else {
            writeLegacyFile(fileName, bytes)
        }

    private fun writeViaMediaStore(fileName: String, mimeType: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, REPORT_RELATIVE_PATH)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore rejected $REPORT_RELATIVE_PATH/$fileName")

        runCatching {
            checkNotNull(resolver.openOutputStream(uri)) { "Could not open $uri for writing" }
                .use { it.write(bytes) }
        }.onFailure { cause ->
            // Leave no empty placeholder row behind for a write that never landed.
            runCatching { resolver.delete(uri, null, null) }
            throw IOException("Could not write $REPORT_RELATIVE_PATH/$fileName", cause)
        }

        return uri.toString()
    }

    private fun writeLegacyFile(fileName: String, bytes: ByteArray): String {
        @Suppress("DEPRECATION")
        val documentsDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val reportDir = File(documentsDir, REPORT_FOLDER_NAME)
            .takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir

        val file = File(reportDir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        private const val REPORT_FOLDER_NAME = "AgarthaVision"
        private const val CSV_MIME_TYPE = "text/csv"
        private const val PDF_MIME_TYPE = "application/pdf"
        private val REPORT_RELATIVE_PATH =
            "${Environment.DIRECTORY_DOCUMENTS}/$REPORT_FOLDER_NAME"
    }
}
