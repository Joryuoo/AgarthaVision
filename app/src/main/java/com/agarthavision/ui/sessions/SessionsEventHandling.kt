package com.agarthavision.ui.sessions

import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.records.viewReportPdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles one [SessionsEvent], split out of [SessionsScreen] so the composable itself stays
 * simple: this `when` lives in its own function so its branch count is counted against this
 * function's cyclomatic complexity rather than the screen's.
 */
@Suppress("LongParameterList")
internal suspend fun handleSessionsEvent(
    event: SessionsEvent,
    context: Context,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    generatedSnackbarLabel: String,
    openActionLabel: String,
    onNavigateToCapture: (String) -> Unit,
    onNavigate: (String) -> Unit,
    setShowCreateDialog: (Boolean) -> Unit,
) {
    when (event) {
        is SessionsEvent.NavigateToCapture -> {
            setShowCreateDialog(false)
            onNavigateToCapture(event.sessionId)
        }
        is SessionsEvent.NavigateToVerificationQueue -> onNavigate(Screen.VerificationQueue.route)
        is SessionsEvent.ShareExport -> {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, event.content)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
        is SessionsEvent.PatientReportGenerated -> coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = generatedSnackbarLabel,
                actionLabel = openActionLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewReportPdf(context, event.pdfPath)
            }
        }
    }
}
