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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.components.glassChrome
import com.agarthavision.ui.components.glassChromeStrong
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.shadow

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

    // Design system tokens
    val Brand = Color(0xFF1E40AF)
    val Danger = Color(0xFFFF3B30)
    val Success = Color(0xFF34C759)
    val SuccessDeep = Color(0xFF248A3D)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF6E6E73)
    val BodyColor = Color(0xFF3C3C43)
    val AiPurple = Color(0xFFAF52DE)
    
    // Animation states
    val isRecording = state.isInferenceRunning
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Full screen container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full-bleed Viewfinder
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

        // Viewfinder Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.18f),
                        0.18f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.18f)
                    )
                )
        )

        // 2. Dynamic Island Emulation (Recording Live Activity)
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFF0A0A0B))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pulsing Dot
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Danger)
                            .shadow(if (pulseScale > 0.95f) 8.dp else 0.dp, CircleShape, spotColor = Danger)
                    )
                    Text("00:00:00", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. Top Chrome Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .glassChrome(shape = RoundedCornerShape(22.dp))
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Counter
            Column {
                Text("CAPTURED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Muted, fontFamily = FontFamily.Default)
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${state.flaggedFrames.size}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = Ink, fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
                    Text("frames", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Muted, fontFamily = FontFamily.Default)
                }
            }
            
            // Icon Group
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Stats
                Box(modifier = Modifier.size(38.dp).background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(13.dp)).border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(13.dp)).clickable { }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assessment, contentDescription = "Stats", tint = Ink, modifier = Modifier.size(19.dp))
                }
                // History / Verify Queue
                Box(modifier = Modifier.size(38.dp).background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(13.dp)).border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(13.dp)).clickable { onVerifyQueueClick() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.History, contentDescription = "History", tint = Ink, modifier = Modifier.size(19.dp))
                }
                // Export
                Box(modifier = Modifier.size(38.dp).background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(13.dp)).border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(13.dp)).clickable { state.activeSessionId?.let(onReportsClick) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FactCheck, contentDescription = "Export", tint = Ink, modifier = Modifier.size(19.dp))
                }
            }
        }

        // 4. Side Rail Chips
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 184.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .glassChromeStrong(shape = RoundedCornerShape(100.dp))
                    .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).shadow(6.dp, CircleShape, spotColor = Success, ambientColor = Success).background(Success, CircleShape))
                Text("4K · 60 fps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            Row(
                modifier = Modifier
                    .glassChromeStrong(shape = RoundedCornerShape(100.dp))
                    .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = AiPurple, modifier = Modifier.size(14.dp))
                Text("AI · auto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
        }

        // 5. Focus Reticle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(84.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .shadow(0.dp, RoundedCornerShape(6.dp), spotColor = Color.Black.copy(alpha = 0.15f))
        )

        // 6. Bottom Capture Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .glassChrome(shape = RoundedCornerShape(30.dp))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
        ) {
            // Meta row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Live badge
                    Row(
                        modifier = Modifier.background(Success.copy(alpha = 0.16f), RoundedCornerShape(100.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).shadow(5.dp, CircleShape, spotColor = Success, ambientColor = Success).background(Success, CircleShape))
                        Text("LIVE", color = SuccessDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                    }
                    val sessionName = state.activeSessionLabel ?: state.activeSessionId?.take(4) ?: "None"
                    Text("Session $sessionName · ${state.flaggedFrames.size} captured", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BodyColor)
                }
                Text("2.3 GB · 47%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BodyColor, fontFamily = FontFamily.Monospace)
            }
            
            Spacer(modifier = Modifier.height(14.dp).fillMaxWidth().background(Color(0xFF3C3C43).copy(alpha = 0.12f)))
            Spacer(modifier = Modifier.height(14.dp))

            // Controls grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Gallery
                Box(modifier = Modifier.size(52.dp).background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(16.dp)).border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable { onRecordsClick() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.History, contentDescription = "Gallery", tint = Ink, modifier = Modifier.size(22.dp))
                }
                
                // Center: Record
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
                        .shadow(8.dp, CircleShape, spotColor = Color(0xFF14161E).copy(alpha = 0.12f))
                        .clickable(enabled = state.activeSessionId != null) { viewModel.onManualCapture() },
                    contentAlignment = Alignment.Center
                ) {
                    val innerShape = if (isRecording) RoundedCornerShape(8.dp) else CircleShape
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .shadow(14.dp, innerShape, spotColor = Danger.copy(alpha = 0.35f))
                            .background(Danger, innerShape)
                    )
                }
                
                // Right: End Session
                if (state.activeSessionId != null) {
                    Row(
                        modifier = Modifier
                            .background(Danger.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
                            .border(0.5.dp, Danger.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                            .clickable { showEndConfirm = true }
                            .padding(start = 11.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(14.dp).background(Danger, RoundedCornerShape(2.dp)))
                        Text("End Session", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .background(Brand, RoundedCornerShape(100.dp))
                            .clickable { } // TODO: route to Session Picker
                            .padding(start = 11.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("Start Session", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
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

        // Custom AI Detection Toast (replaces SonnerHost)
        AnimatedVisibility(
            visible = state.verificationTarget != null && state.verificationTarget?.source == FrameSource.MODEL,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp)
        ) {
            val target = state.verificationTarget
            val confidence = target?.predictions?.firstOrNull()?.confidence ?: 0f
            val species = target?.predictions?.firstOrNull()?.classLabel ?: "Ascaris egg detected"
            
            Row(
                modifier = Modifier
                    .shadow(28.dp, RoundedCornerShape(100.dp), spotColor = AiPurple.copy(alpha = 0.32f), ambientColor = AiPurple.copy(alpha = 0.2f))
                    .background(AiPurple.copy(alpha = 0.96f), RoundedCornerShape(100.dp))
                    .clickable { 
                        // It's already open in verificationTarget state, but we can do custom action
                    }
                    .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(22.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Text(species, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                Box(modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("${(confidence * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
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
