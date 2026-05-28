@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.components.MicroscopyViewport
import com.agarthavision.ui.components.ShutterButton
import com.agarthavision.ui.theme.AgarthaSpacing
import com.agarthavision.ui.verify.ManualSheet
import com.agarthavision.ui.verify.VerificationSheet
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.components.Input
import com.komoui.components.sooner.SonnerAction
import com.komoui.components.sooner.SonnerEvent
import com.komoui.components.sooner.SonnerHost
import com.komoui.components.sooner.SonnerVariant
import com.komoui.components.sooner.showSonner
import com.komoui.themes.styles
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = hiltViewModel(),
    cameraManager: CameraManager,
    frameSampler: FrameSampler,
    onRecordsClick: () -> Unit,
    onReportsClick: (String) -> Unit,
    onVerifyQueueClick: () -> Unit,
    onSessionEnded: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sonnerHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val detectionFallback = stringResource(R.string.capture_detection_fallback)
    val detectionView = stringResource(R.string.capture_detection_view)
    val detectionMessage = stringResource(R.string.capture_detection_message)
    var showEndConfirm by rememberSaveable { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-pause inference whenever Capture leaves the foreground (back to picker,
    // app backgrounded, etc.) and resume on return. Sheets/overlays handle their
    // own pause/resume — see CaptureViewModel.resumeInferenceIfNoOverlay().
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumeInferenceIfNoOverlay()
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseInference()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CaptureEvent.SessionEnded -> onSessionEnded()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.state
            .map { it.flaggedFrames.firstOrNull()?.capturedAt }
            .distinctUntilChanged()
            .collect { capturedAt ->
                if (capturedAt != null) {
                    val latest = viewModel.state.value.flaggedFrames.firstOrNull() ?: return@collect
                    if (latest.source != FrameSource.MODEL) return@collect
                    val eggType = latest.predictions.firstOrNull()?.classLabel ?: detectionFallback
                    val confidence = latest.predictions.firstOrNull()?.confidence ?: 0f
                    sonnerHostState.showSonner(
                        SonnerEvent(
                            message = detectionMessage.format(eggType, "%.0f".format(confidence * 100)),
                            action = SonnerAction(
                                actionText = detectionView,
                                execute = { viewModel.onDetectionToastTap(latest) },
                            ),
                            withDismissAction = true,
                            variant = SonnerVariant.Default,
                        ),
                    )
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        state.activeSessionLabel?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.capture_title),
                    )
                },
                actions = {
                    IconButton(
                        onClick = { state.activeSessionId?.let(onReportsClick) },
                        enabled = state.activeSessionId != null,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = stringResource(R.string.capture_reports_action_desc),
                            tint = MaterialTheme.styles.foreground,
                        )
                    }
                    IconButton(onClick = onRecordsClick) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.capture_records_action_desc),
                            tint = MaterialTheme.styles.foreground,
                        )
                    }
                    IconButton(onClick = onVerifyQueueClick) {
                        BadgedBox(
                            badge = {
                                if (state.flaggedFrames.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.styles.primary,
                                        contentColor = MaterialTheme.styles.primaryForeground,
                                    ) {
                                        Text(state.flaggedFrames.size.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = stringResource(R.string.capture_verify_action_desc),
                                tint = MaterialTheme.styles.foreground,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f),
                ) {
                    if (hasCameraPermission) {
                        MicroscopyViewport(
                            cameraManager = cameraManager,
                            analyzer = frameSampler,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CameraPermissionRequired(
                            onRequestPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                        )
                    }

                    if (state.isInferenceRunning) {
                        KomoBadge(
                            variant = BadgeVariant.Destructive,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                        ) {
                            Text(stringResource(R.string.capture_rec))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator()
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgarthaSpacing.screenEdge),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShutterButton(
                                onClick = viewModel::onManualCapture,
                                enabled = state.activeSessionId != null,
                            )
                            KomoButton(
                                onClick = { showEndConfirm = true },
                                size = ButtonSize.Lg,
                                variant = ButtonVariant.Destructive,
                                enabled = state.activeSessionId != null,
                            ) {
                                Text(stringResource(R.string.capture_end_session))
                            }
                        }
                    }

                    state.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.styles.destructive,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                        )
                    }
                }
            }

            ConnectionLossBanner(
                visible = state.isConnectionLost,
                isProbing = state.isProbingConnection,
                onResume = viewModel::resumeConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )

            // Sonner toast lifted to top-center (per 05_DESIGN_SYSTEM_KOMOUI.md
            // "Bottom-control overlap" callout) so it never blocks the Stop
            // Recording button. The toast and banner are mutually exclusive in
            // practice — toasts only fire during recording, banner only shows
            // after the session is forced-stopped.
            SonnerHost(
                hostState = sonnerHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    val target = state.verificationTarget
    if (target != null) {
        if (target.source == FrameSource.MANUAL) {
            ManualSheet(
                frame = target,
                onDismiss = viewModel::onVerificationDismissed,
            )
        } else {
            VerificationSheet(
                frame = target,
                onDismiss = viewModel::onVerificationDismissed,
            )
        }
    }

    if (showEndConfirm) {
        EndSessionConfirmDialog(
            initialNotes = "",
            isBusy = state.isBusy,
            onConfirm = { notes ->
                showEndConfirm = false
                viewModel.endSession(notes)
            },
            onDismiss = { showEndConfirm = false },
        )
    }
}

@Composable
private fun EndSessionConfirmDialog(
    initialNotes: String,
    isBusy: Boolean,
    onConfirm: (notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            KomoButton(
                onClick = { onConfirm(notes.takeIf { it.isNotBlank() }) },
                variant = ButtonVariant.Destructive,
                size = ButtonSize.Default,
                enabled = !isBusy,
                loading = isBusy,
            ) {
                Text(stringResource(R.string.capture_end_session_confirm))
            }
        },
        dismissButton = {
            KomoButton(
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Default,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.verify_cancel))
            }
        },
        title = { Text(stringResource(R.string.capture_end_session_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.capture_end_session_body),
                    color = MaterialTheme.styles.mutedForeground,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.capture_end_session_notes_label),
                    color = MaterialTheme.styles.foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
                Input(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = stringResource(R.string.capture_end_session_notes_placeholder),
                    singleLine = false,
                    enabled = !isBusy,
                )
            }
        },
        containerColor = MaterialTheme.styles.popover,
        titleContentColor = MaterialTheme.styles.foreground,
        textContentColor = MaterialTheme.styles.foreground,
    )
}

@Composable
private fun CameraPermissionRequired(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.styles.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.capture_permission_required),
                color = MaterialTheme.styles.foreground,
                style = MaterialTheme.typography.bodyMedium,
            )
            KomoButton(
                onClick = onRequestPermission,
                size = ButtonSize.Default,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.capture_allow_camera))
            }
        }
    }
}
