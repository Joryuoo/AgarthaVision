package com.agarthavision.ui.records

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.agarthavision.R

/**
 * The file format a medtech chooses to export a [com.agarthavision.domain.model.Report] as.
 * The choice is passed to `GenerateSessionReportUseCase`, which writes only that format's file,
 * so a report carries either a PDF or a CSV, never both.
 */
enum class ExportFormat { PDF, CSV }

/**
 * The PDF/CSV choice offered from both [GenerateReportButton] and [ReportRow] — same two items,
 * differing only in which are enabled for a given report's existing files.
 */
@Composable
internal fun ExportFormatMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExportFormat) -> Unit,
    pdfEnabled: Boolean = true,
    csvEnabled: Boolean = true,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.report_export_as_pdf)) },
            enabled = pdfEnabled,
            onClick = {
                onDismiss()
                onSelect(ExportFormat.PDF)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.report_export_as_csv)) },
            enabled = csvEnabled,
            onClick = {
                onDismiss()
                onSelect(ExportFormat.CSV)
            },
        )
    }
}
