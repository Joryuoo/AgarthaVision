package com.agarthavision.ui.coverage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.ui.graphics.Path
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
import com.agarthavision.ui.components.EmptyState
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

        val hasData = (state.coverage?.totals?.smears ?: 0) > 0
        if (!state.isLoading && !hasData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Outlined.Map,
                    title = "No smears in this period",
                    body = "Smears you examine will appear here by province.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
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
        }

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
        val bins = listOf("0-10%", "10-20%", "20-30%", "30-40%", "40%+")
        bins.forEachIndexed { index, label ->
            val fill = colors.coverageBinColor(index)
            LegendSwatch(fill, label)
        }
        LegendSwatch(colors.coverageTooFew, "Too few")
        LegendSwatch(colors.coverageNoData, "No data")
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
                .background(color)
                .border(0.5.dp, colors.borderStrong, CircleShape),
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
    var minScale by remember { mutableStateOf(0f) }
    var maxScale by remember { mutableStateOf(Float.MAX_VALUE) }
    var canvasSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(state.initialFit, canvasSize, provinces) {
        val fit = state.initialFit ?: return@LaunchedEffect
        val (w, h) = canvasSize
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        val transform = fitBounds(fit, w, h, MAP_PADDING_PX)
        val countryScale = fitBounds(provinces.bounds, w, h, MAP_PADDING_PX).scale
        minScale = minOf(countryScale, transform.scale) * MIN_SCALE_FACTOR
        maxScale = transform.scale * MAX_SCALE_FACTOR
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

    val coverageByCode = remember(state.coverage) {
        state.coverage?.provinces?.associateBy { it.code } ?: emptyMap()
    }
    val pathCache = remember { mutableMapOf<String, Path>() }
    var lastTransform by remember { mutableStateOf<ViewTransform?>(null) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceMuted)
            .onSizeChanged { size -> canvasSize = size.width.toFloat() to size.height.toFloat() }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = nextCameraTransform(
                        current = camera.transform,
                        centroid = centroid,
                        pan = pan,
                        zoom = zoom,
                        minScale = minScale,
                        maxScale = maxScale,
                    )
                    coroutineScope.launch { camera.scale.snapTo(next.scale) }
                    coroutineScope.launch { camera.tx.snapTo(next.tx) }
                    coroutineScope.launch { camera.ty.snapTo(next.ty) }
                }
            }
            .pointerInput(provinces) {
                detectTapGestures { offset ->
                    val (mapX, mapY) = camera.transform.toMap(offset.x, offset.y)
                    onTap(mapX, mapY)
                }
            },
    ) {
        val currentTransform = camera.transform
        if (lastTransform != currentTransform) {
            pathCache.clear()
            lastTransform = currentTransform
        }
        drawProvinces(
            provinces = provinces,
            coverageByCode = coverageByCode,
            transform = currentTransform,
            colors = colors,
            highlightCode = state.selected?.code,
            highlightColor = colors.accent,
            pathCache = pathCache,
        )
    }
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
    // A zoom-out gesture must never increase scale when the camera already sits below the floor
    // (e.g. right after "All" resets to a fit narrower than the previous zoomed-in view).
    val lower = minOf(minScale, current.scale)
    val newScale = (current.scale * zoom).coerceIn(lower, maxScale)
    val appliedZoom = if (current.scale == 0f) 1f else newScale / current.scale
    val newTx = centroid.x * (1 - appliedZoom) + current.tx * appliedZoom + pan.x
    val newTy = centroid.y * (1 - appliedZoom) + current.ty * appliedZoom + pan.y
    return ViewTransform(newScale, newTx, newTy)
}
