package com.agarthavision.ui.coverage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.ViewTransform
import com.agarthavision.domain.geo.fitBounds
import com.agarthavision.ui.dashboard.PeriodToggle
import com.agarthavision.ui.dashboard.coverage.drawProvinces
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ArrowBackIosNew
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val MAP_PADDING_PX = 16f
private const val MIN_SCALE_FACTOR = 0.8f
private const val MAX_SCALE_FACTOR = 40f
private const val CAMERA_ANIM_MS = 350
private const val POSITIVE_RATE_BIN_COUNT = 4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCoverageScreen(
    onBack: () -> Unit,
    viewModel: MyCoverageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = AgarthaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = AgarthaIcons.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "My coverage",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            PeriodToggle(selected = state.period, onSelect = viewModel::onPeriodChange)
        }

        Spacer(Modifier.height(Spacing.xs))

        IslandGroupChips(
            counts = state.coverage?.islandGroupCounts ?: emptyMap(),
            total = state.coverage?.provinces?.size ?: 0,
            selected = state.islandFilter,
            onSelect = viewModel::onIslandFilter,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )

        Spacer(Modifier.height(Spacing.sm))

        val provinces = state.provinces
        if (provinces != null) {
            CoverageMap(
                provinces = provinces,
                state = state,
                onTap = viewModel::onMapTap,
                onCameraTargetConsumed = viewModel::onCameraTargetConsumed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f))
        }

        CoverageLegend(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))

        Text(
            text = "Administrative boundaries: OCHA Philippines Common Operational Datasets " +
                "(COD-AB), CC BY-IGO 3.0.",
            fontSize = 9.sp,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }

    val selected = state.selected
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ProvinceSheet(
            selected = selected,
            showAllTowns = state.showAllTowns,
            onDismiss = viewModel::onDismissSheet,
            onShowAllTowns = viewModel::onShowAllTowns,
            sheetState = sheetState,
        )
    }
}

@Composable
private fun CoverageLegend(modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val bins = listOf("0%", "<10%", "<20%", "<40%", "40%+")
        bins.forEachIndexed { index, label ->
            val fill = lerp(colors.accentTint, colors.accent, index / POSITIVE_RATE_BIN_COUNT)
            LegendSwatch(fill, label)
        }
        LegendSwatch(colors.surfaceMuted, "Too few")
        LegendSwatch(colors.surfaceMuted, "No data")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    val colors = AgarthaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = colors.textSecondary)
    }
}

/** Camera as scale + translation, kept as [Animatable]s so [LaunchedEffect]s can animate them. */
private class MapCamera(
    val scale: Animatable<Float, AnimationVector1D>,
    val tx: Animatable<Float, AnimationVector1D>,
    val ty: Animatable<Float, AnimationVector1D>,
) {
    val transform: ViewTransform get() = ViewTransform(scale.value, tx.value, ty.value)
}

@Composable
private fun CoverageMap(
    provinces: BoundarySet,
    state: MyCoverageUiState,
    onTap: (Float, Float) -> Unit,
    onCameraTargetConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val coroutineScope = rememberCoroutineScope()

    val camera = remember {
        MapCamera(
            scale = Animatable(1f),
            tx = Animatable(0f),
            ty = Animatable(0f),
        )
    }
    var baseScale by remember { mutableStateOf(1f) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(state.initialFit, canvasSize) {
        val fit = state.initialFit ?: return@LaunchedEffect
        val (w, h) = canvasSize
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        val transform = fitBounds(fit, w, h, MAP_PADDING_PX)
        baseScale = transform.scale
        if (!initialized) {
            camera.scale.snapTo(transform.scale)
            camera.tx.snapTo(transform.tx)
            camera.ty.snapTo(transform.ty)
            initialized = true
        }
    }

    LaunchedEffect(state.cameraTarget, canvasSize) {
        val target = state.cameraTarget ?: return@LaunchedEffect
        val (w, h) = canvasSize
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        val transform = fitBounds(target, w, h, MAP_PADDING_PX)
        val anim = tween<Float>(CAMERA_ANIM_MS, easing = FastOutSlowInEasing)
        coroutineScope.launch { camera.scale.animateTo(transform.scale, anim) }
        coroutineScope.launch { camera.tx.animateTo(transform.tx, anim) }
        coroutineScope.launch { camera.ty.animateTo(transform.ty, anim) }
        onCameraTargetConsumed()
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceMuted)
            .onSizeChanged { size -> canvasSize = size.width.toFloat() to size.height.toFloat() }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val minScale = baseScale * MIN_SCALE_FACTOR
                    val maxScale = baseScale * MAX_SCALE_FACTOR
                    val next = nextCameraTransform(
                        current = camera.transform,
                        centroid = centroid,
                        pan = pan,
                        zoom = zoom,
                        minScale = minScale,
                        maxScale = maxScale,
                    )
                    val newTx = clampTranslation(next.tx, canvasSize.first)
                    val newTy = clampTranslation(next.ty, canvasSize.second)
                    coroutineScope.launch { camera.scale.snapTo(next.scale) }
                    coroutineScope.launch { camera.tx.snapTo(newTx) }
                    coroutineScope.launch { camera.ty.snapTo(newTy) }
                }
            }
            .pointerInput(provinces) {
                detectTapGestures { offset ->
                    val (mapX, mapY) = camera.transform.toMap(offset.x, offset.y)
                    onTap(mapX, mapY)
                }
            },
    ) {
        drawProvinces(
            provinces = provinces,
            coverageByCode = state.coverage?.provinces?.associateBy { it.code } ?: emptyMap(),
            transform = camera.transform,
            colors = colors,
            highlightCode = state.selected?.code,
            highlightColor = colors.gold,
        )
    }
}

/**
 * Keeps the country bbox from fully leaving the viewport — a simple clamp against the current
 * span, not elastic/bounce behavior (out of scope per the plan).
 */
private fun clampTranslation(value: Float, viewportSpan: Float): Float {
    if (viewportSpan <= 0f) return value
    val slack = viewportSpan
    return value.coerceIn(-slack, slack)
}

/**
 * Given the [current] camera transform and one [detectTransformGestures] callback's [centroid],
 * [pan] and [zoom], returns the transform that keeps the map point under [centroid] fixed on
 * screen as it scales — i.e. zooms toward the pinch center rather than the transform's origin.
 *
 * `screen = map * scale + t` ([ViewTransform]), so holding `(centroid - t) / scale` constant
 * across a scale change from `oldScale` to `newScale` requires
 * `t' = centroid * (1 - appliedZoom) + t * appliedZoom`, using the *applied* zoom ratio
 * (`newScale / oldScale`) rather than the raw gesture [zoom] — those differ once [minScale]/
 * [maxScale] clamps the requested scale, and using the raw value there would still drag the
 * translation as if the clamped-away zoom had happened, drifting the map on every frame the
 * pinch is held past a scale limit. [pan] (the centroid's own screen-space movement) is added
 * on top, unscaled, since [ViewTransform]'s translation is already in screen pixels.
 */
@Suppress("LongParameterList") // Every parameter is a distinct, independent gesture-math input.
internal fun nextCameraTransform(
    current: ViewTransform,
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    minScale: Float,
    maxScale: Float,
): ViewTransform {
    val newScale = (current.scale * zoom).coerceIn(minScale, maxScale)
    val appliedZoom = if (current.scale == 0f) 1f else newScale / current.scale
    val newTx = centroid.x * (1 - appliedZoom) + current.tx * appliedZoom + pan.x
    val newTy = centroid.y * (1 - appliedZoom) + current.ty * appliedZoom + pan.y
    return ViewTransform(newScale, newTx, newTy)
}
