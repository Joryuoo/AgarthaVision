@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.components.Input
import com.komoui.themes.styles
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SvgIcon(
    pathData: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Float = 1.6f,
    drawExtras: (DrawScope.(Color) -> Unit)? = null
) {
    val path = remember(pathData) {
        PathParser().parsePathString(pathData).toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f // assuming 24x24 viewBox
        scale(scale, scale, pivot = Offset.Zero) {
            if (pathData.isNotEmpty()) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            drawExtras?.invoke(this, color)
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
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
                        radius = (size.width / 2) + haloSize.dp.toPx()
                    )
                }
            }
    )
}

@Composable
private fun IconButtonGlass(
    pathData: String,
    onClick: () -> Unit,
    drawExtras: (DrawScope.(Color) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color(20, 28, 42, (0.55f * 255).toInt()), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        SvgIcon(pathData, color = Color.White, strokeWidth = 1.8f, modifier = Modifier.size(22.dp), drawExtras = drawExtras)
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
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var showEndConfirm by rememberSaveable { mutableStateOf(false) }

    // Hide the system navigation bar while on Capture screen (immersive sticky)
    DisposableEffect(Unit) {
        val window = (context as? androidx.activity.ComponentActivity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            // Restore nav bar when leaving Capture screen
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
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

    // Animation states
    val isRecording = state.isInferenceRunning

    // Full screen container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full-bleed Viewfinder (z-1)
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

        // 5. Focus Reticle (z-3)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val alpha = 0.25f
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Z-5: AI Detection Box
        val latestModelFrame = state.flaggedFrames.firstOrNull { it.source == FrameSource.MODEL }
        if (latestModelFrame != null) {
            val confidence = latestModelFrame.predictions.firstOrNull()?.confidence ?: 0f
            val species = latestModelFrame.predictions.firstOrNull()?.classLabel ?: "Unknown"
            
            // Dummy coordinates for the visual placeholder as described in prompt
            // top: 40%; left: 36%; width: 96px; height: 80px;
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = 130.dp, y = 280.dp) // Approximate for 36% and 40% on standard phone
                        .size(96.dp, 80.dp)
                        .border(2.dp, Color(0xFF1E3FD9), RoundedCornerShape(4.dp))
                        .shadow(20.dp, RoundedCornerShape(4.dp), spotColor = Color(0xFF1E3FD9).copy(alpha = 0.15f))
                ) {
                    // Corner Brackets
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bracketSize = 14.dp.toPx()
                        val stroke = 2.dp.toPx()
                        val color = Color.White
                        
                        // TL
                        drawLine(color, Offset(0f, 0f), Offset(bracketSize, 0f), stroke)
                        drawLine(color, Offset(0f, 0f), Offset(0f, bracketSize), stroke)
                        // TR
                        drawLine(color, Offset(size.width, 0f), Offset(size.width - bracketSize, 0f), stroke)
                        drawLine(color, Offset(size.width, 0f), Offset(size.width, bracketSize), stroke)
                        // BL
                        drawLine(color, Offset(0f, size.height), Offset(bracketSize, size.height), stroke)
                        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - bracketSize), stroke)
                        // BR
                        drawLine(color, Offset(size.width, size.height), Offset(size.width - bracketSize, size.height), stroke)
                        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - bracketSize), stroke)
                    }

                    // Label Tag
                    Text(
                        text = "$species · ${(confidence * 100).toInt()}%",
                        modifier = Modifier
                            .offset(x = (-2).dp, y = (-24).dp)
                            .background(Color(0xFF1E3FD9), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 0.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.02.sp
                    )
                }
            }

            // Z-9: Live detection toast
            AnimatedVisibility(
                visible = true, // Shows when latestModelFrame exists
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 108.dp).padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(36.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                        .background(Color(15, 23, 42, (0.78f * 255).toInt()), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Toast Icon
                    Box(
                        modifier = Modifier.size(32.dp).background(Color(0xFF1E3FD9), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon("M9 12l2 2 4-4", drawExtras = { c -> drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(1.8f)) }, color = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Toast Text
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$species detected", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
                        Text("${(confidence * 100).toInt()}% confidence · just now", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    }

                    // Toast Action
                    Text("Review", color = Color(0xFF93B0FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { viewModel.onDetectionToastTap(latestModelFrame) }.padding(4.dp))
                }
            }
        }

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

        // Z-10: Floating Top Chrome
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButtonGlass("M 15 18 L 9 12 L 15 6", onClick = onNavigateBack)

            // Session Pill
            Row(
                modifier = Modifier
                    .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(Color(20, 28, 42, (0.55f * 255).toInt()), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(start = 11.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingDot()
                Text("SESSION", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
                val sessionName = state.activeSessionLabel ?: state.activeSessionId?.take(3) ?: "---"
                Text(sessionName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.15).sp)
            }

            // Top Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Stats
                IconButtonGlass("", drawExtras = { c -> 
                    drawRoundRect(c, Offset(3f, 11f), Size(4f, 10f), CornerRadius(0.5f, 0.5f))
                    drawRoundRect(c, Offset(10f, 6f), Size(4f, 15f), CornerRadius(0.5f, 0.5f))
                    drawRoundRect(c, Offset(17f, 3f), Size(4f, 18f), CornerRadius(0.5f, 0.5f))
                }, onClick = { /* Stats overlay */ })
                
                // History
                IconButtonGlass("M 12 7 L 12 12 L 15 14", drawExtras = { c -> drawCircle(c, 9f, Offset(12f,12f), style = Stroke(1.6f)) }, onClick = onRecordsClick)
                
                // Verify Queue
                Box {
                    IconButtonGlass("M9 12l2 2 4-4", drawExtras = { c -> drawRoundRect(c, Offset(3f,3f), Size(18f,18f), CornerRadius(2f,2f), style = Stroke(1.6f)) }, onClick = onVerifyQueueClick)
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
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$verifyCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Z-10: Floating Bottom Chrome
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Frame Count Pill (weight=1, aligned to start)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                        .background(Color(20, 28, 42, (0.55f * 255).toInt()), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .defaultMinSize(minWidth = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("FRAMES", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f), letterSpacing = 1.2.sp)
                    Text("${state.flaggedFrames.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.15).sp)
                }
            }

            // Center: Shutter (no weight — stays exactly centered)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(28.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                    .background(Color.White, CircleShape)
                    .clickable(enabled = state.activeSessionId != null) { viewModel.onManualCapture() }
            )

            // Right: End Session (weight=1, aligned to end)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-30).dp)
                        .shadow(14.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFFDC2626).copy(alpha = 0.4f))
                        .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                        .clickable { showEndConfirm = true }
                )
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
