@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.components.MicroscopyViewport
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
import kotlinx.coroutines.launch

@Composable
private fun SvgIcon(
    pathData: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Float = 1.6f,
    drawExtras: (DrawScope.(Color) -> Unit)? = null,
) {
    val path = remember(pathData) {
        PathParser().parsePathString(pathData).toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f // assumes 24x24 viewBox
        scale(scale, scale, pivot = Offset.Zero) {
            if (pathData.isNotEmpty()) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            drawExtras?.invoke(this, color)
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val opacity = 1f - progress * 0.35f
    val haloSize = progress * 5f
    val haloAlpha = 0.55f * (1f - progress)

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(Color(0xFFDC2626).copy(alpha = opacity), CircleShape)
            .drawBehind {
                if (haloAlpha > 0f) {
                    drawCircle(
                        color = Color(0xFFDC2626).copy(alpha = haloAlpha),
                        radius = (size.width / 2) + haloSize.dp.toPx(),
                    )
                }
            },
    )
}

@Composable
private fun IconButtonGlass(
    pathData: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    drawExtras: (DrawScope.(Color) -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color(20, 28, 42, (0.55f * 255).toInt()), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        SvgIcon(
            pathData,
            color = Color.White,
            strokeWidth = 1.8f,
            modifier = Modifier.size(22.dp),
            drawExtras = drawExtras,
        )
    }
}

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = hiltViewModel(),
    cameraManager: CameraManager,
    frameSampler: FrameSampler,
    onRecordsClick: () -> Unit,
    onReportsClick: (String) -> Unit,
    onVerifyQueueClick: () -> Unit,
    onSessionEnded: () -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sonnerHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
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

    // Hide the system navigation bar while on Capture screen (immersive sticky)
    DisposableEffect(Unit) {
        val window = (context as? androidx.activity.ComponentActivity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
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
                    // Capture the frame NOW — before the coroutine suspends on showSonner
                    val frameAtToastTime =
                        viewModel.state.value.flaggedFrames.firstOrNull() ?: return@collect
                    if (frameAtToastTime.source != FrameSource.MODEL) return@collect
                    val eggType =
                        frameAtToastTime.predictions.firstOrNull()?.classLabel ?: detectionFallback
                    val confidence = frameAtToastTime.predictions.firstOrNull()?.confidence ?: 0f
                    launch {
                        // SonnerAction.execute is NOT called by the library — only performAction()
                        // is invoked, which surfaces as SnackbarResult.ActionPerformed below.
                        val result = sonnerHostState.showSonner(
                            SonnerEvent(
                                message = detectionMessage.format(
                                    eggType,
                                    "%.0f".format(confidence * 100),
                                ),
                                action = SonnerAction(
                                    actionText = detectionView,
                                    execute = {},
                                ),
                                withDismissAction = true,
                                variant = SonnerVariant.Default,
                            ),
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            viewModel.onDetectionToastTap(frameAtToastTime)
                        }
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.styles.background),
    ) {
        // Base: camera view / permission gate
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

        // Busy overlay (keeps the old 77b5 layout, but prevents duplicate taps)
        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Top chrome (77b5 style) - action shortcuts removed (records/reports are reachable outside capture)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back
            IconButtonGlass(
                pathData = "M 15 18 L 9 12 L 15 6",
                onClick = onNavigateBack,
            )

            // Session pill
            Row(
                modifier = Modifier
                    .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(Color(20, 28, 42, (0.55f * 255).toInt()), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(start = 11.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PulsingDot()
                Text(
                    "SESSION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                )
                val sessionName = state.activeSessionLabel ?: state.activeSessionId?.take(3) ?: "---"
                Text(
                    sessionName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.15).sp,
                )
            }

            // Top-right: Verify Queue (kept from 77b5; records/reports shortcuts removed)
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box {
                    IconButtonGlass(
                        "M9 12l2 2 4-4",
                        drawExtras = { c ->
                            drawRoundRect(
                                c,
                                Offset(3f, 3f),
                                Size(18f, 18f),
                                CornerRadius(2f, 2f),
                                style = Stroke(1.6f),
                            )
                        },
                        onClick = onVerifyQueueClick,
                    )

                    val verifyCount = state.flaggedFrames.size
                    if (verifyCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .background(Color(0xFF1E3FD9), CircleShape)
                                .border(2.dp, Color(20, 28, 42, (0.85f * 255).toInt()), CircleShape)
                                .padding(horizontal = 5.dp)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$verifyCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Connection loss banner positioned under the top chrome (77b5 spacing)
        ConnectionLossBanner(
            visible = state.isConnectionLost,
            isProbing = state.isProbingConnection,
            onResume = viewModel::resumeConnection,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 108.dp)
                .padding(horizontal = 14.dp),
        )

        // Bottom chrome (77b5 style)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: frame count pill
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    modifier = Modifier
                        .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                        .background(Color(20, 28, 42, (0.55f * 255).toInt()), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .defaultMinSize(minWidth = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "FRAMES",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.55f),
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        "${state.flaggedFrames.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.15).sp,
                    )
                }
            }

            // Center: shutter
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(28.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                    .background(Color.White, CircleShape)
                    .clickable(enabled = state.activeSessionId != null && !state.isBusy) {
                        viewModel.onManualCapture()
                    },
            )

            // Right: end session
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-30).dp)
                        .shadow(
                            14.dp,
                            RoundedCornerShape(12.dp),
                            spotColor = Color(0xFFDC2626).copy(alpha = 0.4f),
                        )
                        .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                        .clickable(enabled = state.activeSessionId != null && !state.isBusy) {
                            showEndConfirm = true
                        },
                )
            }
        }

        // Sonner toast (top-center so it never blocks the bottom chrome)
        val toastStyles = remember {
            object : com.komoui.themes.KomoStyles by com.agarthavision.ui.theme.AgarthaLightStyles {
                override val foreground = Color.White
                override val mutedForeground = Color.White.copy(alpha = 0.7f)
            }
        }
        com.komoui.themes.KomoTheme(
            isDarkTheme = false,
            komoLightColors = toastStyles,
            komoDarkColors = toastStyles,
            materialLightColors = MaterialTheme.colorScheme,
            materialDarkColors = MaterialTheme.colorScheme,
            komoRadius = com.agarthavision.ui.theme.AgarthaRadius,
            typography = com.agarthavision.ui.theme.AgarthaTypography,
        ) {
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
            blockedCount = state.flaggedFrames.size,
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
    blockedCount: Int,
    onConfirm: (notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    val isBlocked = blockedCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            KomoButton(
                onClick = { onConfirm(notes.takeIf { it.isNotBlank() }) },
                variant = ButtonVariant.Destructive,
                size = ButtonSize.Default,
                enabled = !isBusy && !isBlocked,
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
                if (isBlocked) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.capture_end_blocked_body,
                            blockedCount,
                            blockedCount,
                        ),
                        color = MaterialTheme.styles.mutedForeground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
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
