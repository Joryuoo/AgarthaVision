@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")

package com.agarthavision.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ArrowBackIosNew
import com.agarthavision.ui.icons.LabProfile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

/** Fill of the capture chrome's glass buttons: near-black at 55%, mode-independent like the feed. */
private val GlassFill = Color(28, 20, 18, (0.55f * 255).toInt())

private val ShutterSize = 100.dp
private val ShutterArcStroke = 3.dp
private const val SHUTTER_BUSY_ALPHA = 0.6f
private const val SHUTTER_ARC_SWEEP_DEGREES = 100f
private const val SHUTTER_ARC_ROTATION_MS = 1_100
private const val FULL_TURN_DEGREES = 360f

/**
 * The 100dp shutter. While a capture is in flight it shows that in its own bounds - dimmed,
 * with an indeterminate arc travelling its circumference - instead of the app floating a
 * spinner over the live field. The medtech keeps seeing the smear for the whole round
 * trip, which on a slow link can be the full inference timeout.
 */
@Composable
internal fun Shutter(
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(
        if (isBusy) R.string.capture_shutter_busy_desc else R.string.capture_shutter_desc,
    )
    val arcStart = if (isBusy) {
        rememberInfiniteTransition(label = "shutterArc").animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN_DEGREES,
            animationSpec = infiniteRepeatable(
                animation = tween(SHUTTER_ARC_ROTATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shutterArcStart",
        ).value
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .size(ShutterSize)
            .shadow(28.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
            // alpha only dims what follows it in the chain: keep it ahead of the fill and arc.
            .alpha(if (isBusy) SHUTTER_BUSY_ALPHA else 1f)
            .background(Color.White, CircleShape)
            .drawBehind {
                // Maroon reads on the white disc even dimmed; a white arc would vanish into it.
                if (isBusy) {
                    val stroke = ShutterArcStroke.toPx()
                    drawArc(
                        color = AppColors.Maroon,
                        startAngle = arcStart,
                        sweepAngle = SHUTTER_ARC_SWEEP_DEGREES,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** The 40dp translucent circle shared by every glass button on the capture chrome. */
private fun Modifier.glassCircle(enabled: Boolean, onClick: () -> Unit): Modifier = this
    .size(40.dp)
    .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
    .background(GlassFill, CircleShape)
    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
    .clickable(enabled = enabled) { onClick() }

@Composable
internal fun IconButtonGlass(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.glassCircle(enabled, onClick), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
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
            // Was a check stroke with the surrounding box hand-drawn through `drawExtras`.
            // FactCheck is that glyph, and it is already what Session Detail uses for the
            // same "go and review these frames" action.
            icon = Icons.AutoMirrored.Outlined.FactCheck,
            contentDescription = stringResource(R.string.capture_verify_action_desc),
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
    val frameCapturedNoModelMessage = stringResource(R.string.capture_frame_captured_no_model_message)
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
        val previousLight = controller?.isAppearanceLightStatusBars
        controller?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.isAppearanceLightStatusBars = false
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
            controller?.isAppearanceLightStatusBars = previousLight ?: false
        }
    }

    // One confirmation per successful tap, driven by the tap's own outcome rather than by
    // watching the queue: a Room-backed list moves on its own, and a toast derived from its head
    // announced frames the medtech had not just captured. Exactly one toast fires per capture —
    // a frame with detections does not get a second, competing message.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.FrameCaptured -> {
                    val outcome = event.outcome
                    toastState.show(
                        // Not Destructive, and deliberately: an unreachable container still
                        // recorded the field, and the medtech's next action is the same either
                        // way. It is named, because ten identical confirmations would otherwise
                        // be the only sign that no model ran on any of them.
                        message = when (outcome.source) {
                            FrameSource.MODEL -> frameCapturedMessage
                            FrameSource.MANUAL -> frameCapturedNoModelMessage
                        },
                        variant = AgarthaToastVariant.Default,
                        actionLabel = detectionView,
                        onAction = { viewModel.onCapturedFrameToastTap(outcome.sampleId) },
                    )
                }
            }
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

    // Collapsed offline banner state, hoisted so the compact pill can live in the header row
    // (level with the back button and session pill) while the full banner sits below. Resets
    // to expanded each time the connection is freshly lost.
    var bannerCollapsed by remember(state.isConnectionLost) { mutableStateOf(false) }

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
                // The house back glyph, same as BackArrow and the verification sheets —
                // C11 wants a new affordance to match its neighbours, and back already
                // has one.
                icon = AgarthaIcons.ArrowBackIosNew,
                contentDescription = stringResource(R.string.capture_back_desc),
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

            // Right slot: a dismissed "model unreachable" banner lives here as a compact pill,
            // level with the back button and session label. Otherwise a spacer balances the
            // back button so the session label stays centred.
            if (state.isConnectionLost && bannerCollapsed) {
                ConnectionLossPill(onExpand = { bannerCollapsed = false })
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
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
                visible = state.isConnectionLost && !bannerCollapsed,
                isProbing = state.isProbingConnection,
                onResume = viewModel::resumeConnection,
                onCollapse = { bannerCollapsed = true },
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
                // The inset goes on the controls, not on the screen: the viewport is meant to
                // run edge to edge behind the system bars, and insetting the whole Box would
                // letterbox the camera preview. Without it the system navigation bar sits over
                // the lower part of the shutter and swallows those taps.
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: the verification queue. The badge is a "needs review" number, so it counts
            // flagged frames only - the same set the queue itself lists.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                VerificationQueueButton(
                    count = state.flaggedFrames.size,
                    onClick = onVerifyQueueClick,
                )
            }

            // Center: shutter
            Shutter(
                isBusy = state.isBusy,
                enabled = state.activeSessionId != null && !state.isBusy,
                onClick = viewModel::onCapture,
            )

            // Right: records for this session. A lab-profile glyph, not the bar glyph, which
            // read as signal strength next to the connection-loss banner (86d4ayef8).
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val sessionId = state.activeSessionId
                IconButtonGlass(
                    icon = AgarthaIcons.LabProfile,
                    contentDescription = stringResource(R.string.capture_records_action_desc),
                    enabled = sessionId != null,
                    onClick = { sessionId?.let(onReportsClick) },
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
