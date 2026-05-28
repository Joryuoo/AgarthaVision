@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.ui.components.MicroscopyViewport
import com.agarthavision.ui.components.ShutterButton
import com.agarthavision.ui.verify.VerificationQueueSheet
import com.agarthavision.ui.verify.VerificationSheet
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sonnerHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val detectionFallback = stringResource(R.string.capture_detection_fallback)
    val detectionView = stringResource(R.string.capture_detection_view)
    val detectionMessage = stringResource(R.string.capture_detection_message)
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

    LaunchedEffect(viewModel) {
        viewModel.state
            .map { it.flaggedFrames.firstOrNull()?.capturedAt }
            .distinctUntilChanged()
            .collect { capturedAt ->
                if (capturedAt != null) {
                    val latest = viewModel.state.value.flaggedFrames.firstOrNull() ?: return@collect
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
                title = { Text(stringResource(R.string.capture_title)) },
                actions = {
                    IconButton(onClick = viewModel::onQueueTap) {
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

                    if (state.isRecording) {
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
                        ShutterButton(
                            isRecording = state.isRecording,
                            onClick = {
                                if (state.isRecording) viewModel.stopRecording()
                                else viewModel.startRecording()
                            }
                        )
                    }

                    IconButton(
                        onClick = onRecordsClick,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.capture_records_action_desc),
                            tint = MaterialTheme.styles.foreground,
                        )
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

    if (state.isQueueOpen) {
        VerificationQueueSheet(
            frames = state.flaggedFrames,
            onRowClick = viewModel::onQueueItemSelected,
            onRowDelete = viewModel::onQueueItemDeleted,
            onDismiss = viewModel::onQueueDismiss,
        )
    }

    val target = state.verificationTarget
    if (target != null) {
        VerificationSheet(
            frame = target,
            onDismiss = viewModel::onVerificationDismissed,
        )
    }
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
