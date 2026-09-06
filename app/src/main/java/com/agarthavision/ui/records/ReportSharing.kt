package com.agarthavision.ui.records

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.agarthavision.R
import java.io.File

private const val CSV_MIME_TYPE = "text/csv"
private const val CONTENT_URI_PREFIX = "content://"

/**
 * Opens the system share sheet for a report's CSV.
 *
 * Sharing from inside the app is the point: before this, getting a report to
 * someone meant leaving for a file manager and finding the file by hand.
 *
 * `csvFilePath` holds one of two shapes depending on where the report was written —
 * a MediaStore `content://` URI (API 29+) or an absolute file path (API 26-28, and
 * anything an older build left in Downloads) — so both are handled.
 *
 * @return null on success, or a string resource explaining why the share could not
 *   start. Every failure is reported: these used to be swallowed, so a report whose
 *   file had been cleared away was simply a tap that did nothing.
 */
@StringRes
internal fun shareReportCsv(context: Context, csvFilePath: String?): Int? {
    val uri = csvFilePath?.let { resolveReportUri(context, it) }
    return when {
        csvFilePath == null -> R.string.report_share_missing_path
        uri == null -> R.string.report_share_file_gone
        else -> startShareChooser(context, uri, csvFilePath.substringAfterLast('/'))
    }
}

/**
 * Resolves a stored `csvFilePath` to a shareable URI, or null if the file is gone.
 *
 * A `content://` value came from MediaStore and is already shareable; anything else
 * is a filesystem path and has to go through [FileProvider].
 */
private fun resolveReportUri(context: Context, path: String): Uri? =
    if (path.startsWith(CONTENT_URI_PREFIX)) {
        path.toUri()
    } else {
        File(path).takeIf { it.exists() }?.let { file ->
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }
    }

@StringRes
private fun startShareChooser(context: Context, uri: Uri, label: String): Int? {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = CSV_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, label)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, label).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }
        .fold(onSuccess = { null }, onFailure = { R.string.report_share_no_app })
}
