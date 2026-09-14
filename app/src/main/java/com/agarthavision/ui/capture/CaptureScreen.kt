@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agarthavision.ui.components.SvgIcon
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.core.camera.CameraManager
import com.agarthavision.core.camera.FrameSampler
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.AgarthaButtonSize
import com.agarthavision.ui.components.AgarthaButtonVariant
import com.agarthavision.ui.components.AgarthaToastHost
import com.agarthavision.ui.components.AgarthaToastVariant
import com.agarthavision.ui.components.MicroscopyViewport
import com.agarthavision.ui.components.rememberAgarthaToastState
import com.agarthavision.ui.theme.AgarthaSpacing
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.verify.VerificationSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    drawExtras: (DrawScope.() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color(28, 20, 18, (0.55f * 255).toInt()), CircleShape)
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

/**
 * Shortcut into the verification queue, badged with the frames still awaiting review.
 *
 * Sits bottom-right, where End Session used to be. The badge deliberately counts only the
 * unverified frames: verified samples live in the queue too now, and including them would turn
 * a "needs review" number into a "how much is in here" number.
 */
@Composable
private fun VerificationQueueButton(
    count: Int,
    onClick: () -> Unit,
) {
    Box {
        IconButtonGlass(
            "M9 12l2 2 4-4",
            drawExtras = {
                drawRoundRect(
                    Color.White,
                    Offset(3f, 3f),
                    Size(18f, 18f),
                    CornerRadius(2f, 2f),
                    style = Stroke(1.6f),
                )
            },
            onClick = onClick,
        )

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(AppColors.MaroonBright, CircleShape)
                    .border(2.dp, Color(28, 18, 16, (0.85f * 255).toInt()), CircleShape)
                    .padding(horizontal = 5.dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// Top-level screen composable wired directly from AgarthaNavGraph's single Capture destination
// (viewModel, camera deps, and 5 distinct navigation callbacks); each param is independently
// meaningful and bundling would only wrap a single-call-site composable, not simplify anything.
@Suppress("LongParameterList")
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = hiltViewModel(),
    cameraManager: CameraManager,
    frameSampler: FrameSampler,
    onReportsClick: (String) -> Unit,
    onVerifyQueueClick: () -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberAgarthaToastState()
    val context = LocalContext.current
    val view = LocalView.current
    val detectionView = stringResource(R.string.capture_detection_view)
    val frameCapturedMessage = stringResource(R.string.capture_frame_captured_message)
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

    LaunchedEffect(viewModel) {
        viewModel.state
            // Keyed on the id, not capturedAt: two frames sharing a millisecond used to
            // look like one arrival to distinctUntilChanged, and neither got a toast.
            .map { it.flaggedFrames.firstOrNull()?.sampleId }
            .distinctUntilChanged()
            .collect { sampleId ->
                if (sampleId == null) return@collect
                val frame = viewModel.state.value.flaggedFrames.firstOrNull() ?: return@collect

                toastState.show(
                    message = frameCapturedMessage,
                    variant = AgarthaToastVariant.Default,
                    actionLabel = detectionView,
                    onAction = { viewModel.onDetectionToastTap(frame) },
                )
            }
    }

    // Surface capture errors ("No active session", "Waiting for a live frame", or an
    // unexpected inference/persist failure) as a destructive toast, then clear the latch
    // so the same error can fire again on the next tap.
    LaunchedEffect(viewModel) {
        viewModel.state
            .map { it.errorMessage }
            .distinctUntilChanged()
            .collect { errorMessage ->
                if (errorMessage == null) return@collect
                toastState.show(
                    message = errorMessage,
                    variant = AgarthaToastVariant.Destructive,
                )
                viewModel.clearErrorMessage()
            }
    }

    // A capture runs on viewModelScope, so leaving the screen mid-inference would cancel it
    // and drop the frame before it is persisted. Swallow system back while a tap is in flight;
    // the back button and shutter are already disabled via isBusy.
    BackHandler(enabled = state.isBusy) { /* intentionally consume back during capture */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.White),
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
                enabled = !state.isBusy,
            )

            // Session pill
            Row(
                modifier = Modifier
                    .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                    .background(Color(28, 20, 18, (0.55f * 255).toInt()), CircleShape)
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

            // Balances the back button so the session label stays centred. Records moved
            // to the bottom row, where the three actions now sit together.
            Spacer(modifier = Modifier.width(40.dp))
        }

        // The row beneath the top chrome. Banner and toast are stacked in one column rather
        // than both being pinned to the same offset - they could previously occupy the same
        // band at once, because a shutter tap still records a frame while the connection-loss
        // banner is latched. That was acknowledged in a comment and deferred; this is it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 108.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionLossBanner(
                visible = state.isConnectionLost,
                isProbing = state.isProbingConnection,
                onResume = viewModel::resumeConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
            )
            AgarthaToastHost(
                state = toastState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }

        // Bottom chrome (77b5 style)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: records for this session.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val sessionId = state.activeSessionId
                IconButtonGlass(
                    pathData = "M3,11 H7 V21 H3 Z M10,6 H14 V21 H10 Z M17,3 H21 V21 H17 Z",
                    enabled = sessionId != null,
                    onClick = { sessionId?.let(onReportsClick) },
                )
            }

            // Center: shutter
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(28.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                    .background(Color.White, CircleShape)
                    .clickable(enabled = state.activeSessionId != null && !state.isBusy) {
                        viewModel.onCapture()
                    },
            )

            // Right: the verification queue, where End Session used to be. Its badge counts
            // unverified frames only - verified samples live in the queue too now, and
            // including them would inflate a "needs review" number into a "how much is in
            // here" number.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                VerificationQueueButton(
                    count = state.flaggedFrames.size,
                    onClick = onVerifyQueueClick,
                )
            }
        }

    }

    val target = state.verificationTarget
    if (target != null) {
        // One screen for both sources. What makes a sample "AI" is simply that it has model
        // output, which the sheet reads off the frame itself.
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
            .background(AppColors.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.capture_permission_required),
                color = AppColors.Gray900,
                style = MaterialTheme.typography.bodyMedium,
            )
            AgarthaButton(
                onClick = onRequestPermission,
                size = AgarthaButtonSize.Default,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.capture_allow_camera))
            }
        }
    }
}
