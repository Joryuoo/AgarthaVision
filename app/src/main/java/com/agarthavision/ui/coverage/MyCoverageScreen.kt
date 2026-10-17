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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.geo.ViewTransform
import com.agarthavision.domain.geo.clampedToBounds
import com.agarthavision.domain.geo.fitBounds
import com.agarthavision.domain.model.AreaStat
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.dashboard.PeriodToggle
import com.agarthavision.ui.dashboard.coverage.CoverageLegend
import com.agarthavision.ui.dashboard.coverage.drawProvinces
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ArrowBackIosNew
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

private const val MAP_PADDING_PX = 16f
private const val MIN_SCALE_FACTOR = 0.8f
private const val MAX_SCALE_FACTOR = 40f
private const val CAMERA_ANIM_MS = 350
private const val PERCENT_FACTOR = 100

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
                    contentDescription = stringResource(R.string.nav_back),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.coverage_title),
                fontSize = 20.sp,
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
                    title = stringResource(R.string.coverage_empty_title),
                    body = stringResource(R.string.coverage_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                    illustrationLight = R.drawable.ill_empty_smears_light,
                    illustrationDark = R.drawable.ill_empty_smears_dark,
                )
            }
        } else {
            val provinces = state.provinces
            if (provinces != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.End,
                ) {
                    CoverageLegend(label = stringResource(R.string.coverage_positive_rate))
                }
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
        }

        Text(
            text = stringResource(R.string.coverage_attribution),
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

/** Camera as scale + translation, kept as [Animatable]s so [LaunchedEffect]s can animate them. */
private class MapCamera(
    val scale: Animatable<Float, AnimationVector1D>,
    val tx: Animatable<Float, AnimationVector1D>,
    val ty: Animatable<Float, AnimationVector1D>,
) {
    val transform: ViewTransform get() = ViewTransform(scale.value, tx.value, ty.value)
}

// Camera setup, gesture wiring, and the callout/controls overlay are one cohesive unit.
@Suppress("CyclomaticComplexMethod")
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

    // A single launch per call (instead of one per animated property) so that gesture-driven and
    // button-driven camera updates don't flood the main-thread coroutine queue; the three
    // Animatables still animate concurrently via the inner asyncs, just under one parent job.
    fun animateTo(transform: ViewTransform) {
        val anim = tween<Float>(CAMERA_ANIM_MS, easing = FastOutSlowInEasing)
        coroutineScope.launch {
            awaitAll(
                async { camera.scale.animateTo(transform.scale, anim) },
                async { camera.tx.animateTo(transform.tx, anim) },
                async { camera.ty.animateTo(transform.ty, anim) },
            )
        }
    }

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
        animateTo(fitBounds(target, w, h, MAP_PADDING_PX))
        onCameraTargetConsumed()
    }

    val coverageByCode = remember(state.coverage) {
        state.coverage?.provinces?.associateBy { it.code } ?: emptyMap()
    }
    val pathCache = remember { mutableMapOf<String, Path>() }
    var lastTransform by remember { mutableStateOf<ViewTransform?>(null) }
    val mapShape = RoundedCornerShape(8.dp)
    val mapBg = colors.mapBackground

    Box(
        modifier = modifier
            .clip(mapShape)
            .background(mapBg, mapShape),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> canvasSize = size.width.toFloat() to size.height.toFloat() }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val (w, h) = canvasSize
                        val raw = nextCameraTransform(
                            current = camera.transform,
                            centroid = centroid,
                            pan = pan,
                            zoom = zoom,
                            minScale = minScale,
                            maxScale = maxScale,
                        )
                        val next = if (w > 0f && h > 0f) {
                            raw.clampedToBounds(provinces.bounds, w, h)
                        } else {
                            raw
                        }
                        // snapTo is immediate (no animation frames), so applying all three inside
                        // one coroutine — instead of one launch per property per pointer-move
                        // frame — produces the same visible result while cutting per-frame
                        // coroutine churn ~3x during an active pinch/pan.
                        coroutineScope.launch {
                            camera.scale.snapTo(next.scale)
                            camera.tx.snapTo(next.tx)
                            camera.ty.snapTo(next.ty)
                        }
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
                highlightColor = colors.textPrimary,
                pathCache = pathCache,
            )
        }

        val selected = state.selected
        val selectedArea = selected?.let { provinces.byCode[it.code] }
        if (selected != null && selectedArea != null) {
            val (canvasWidth, canvasHeight) = canvasSize
            SelectedCallout(
                name = selected.name,
                stat = selected.coverage?.count?.stat,
                labelX = selectedArea.labelX,
                labelY = selectedArea.labelY,
                transformProvider = { camera.transform },
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
            )
        }

        val canZoomIn = camera.scale.value < maxScale - ZOOM_EPSILON
        val canZoomOut = camera.scale.value > minScale + ZOOM_EPSILON
        MapControls(
            onZoomIn = {
                val (w, h) = canvasSize
                if (w > 0f && h > 0f) {
                    val next = zoomAroundCenter(camera.transform, w, h, zoomInFactor(), minScale, maxScale)
                    animateTo(next.clampedToBounds(provinces.bounds, w, h))
                }
            },
            onZoomOut = {
                val (w, h) = canvasSize
                if (w > 0f && h > 0f) {
                    val next = zoomAroundCenter(camera.transform, w, h, zoomOutFactor(), minScale, maxScale)
                    animateTo(next.clampedToBounds(provinces.bounds, w, h))
                }
            },
            onRecenter = {
                val (w, h) = canvasSize
                if (w > 0f && h > 0f) {
                    val target = selected?.let { provinces.byCode[it.code]?.bounds }
                        ?: state.initialFit
                        ?: provinces.bounds
                    animateTo(fitBounds(target, w, h, MAP_PADDING_PX))
                }
            },
            canZoomIn = canZoomIn,
            canZoomOut = canZoomOut,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.sm),
        )
    }
}

private const val ZOOM_EPSILON = 0.0001f

/**
 * A small pill near the selected province's label point showing its name and rate. Reads
 * [transformProvider] inside [Modifier.layout] so camera animation only triggers relayout, not
 * recomposition of this composable.
 */
@Suppress("LongParameterList") // Every parameter is a distinct, independent callout-placement input.
@Composable
private fun BoxScope.SelectedCallout(
    name: String,
    stat: AreaStat?,
    labelX: Float,
    labelY: Float,
    transformProvider: () -> ViewTransform,
    canvasWidth: Float,
    canvasHeight: Float,
) {
    val colors = AgarthaTheme.colors
    val text = buildAnnotatedString {
        append("$name ")
        if (stat is AreaStat.Reported) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("${(stat.positiveRate * PERCENT_FACTOR).toInt()}%")
            }
        } else {
            append("· ${stringResource(R.string.coverage_too_few)}")
        }
    }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val (sx, sy) = transformProvider().toScreen(labelX, labelY)
                val maxX = (canvasWidth - placeable.width).coerceAtLeast(0f)
                val maxY = (canvasHeight - placeable.height).coerceAtLeast(0f)
                val x = (sx - placeable.width / 2f).coerceIn(0f, maxX)
                val y = (sy - placeable.height - CALLOUT_OFFSET_PX).coerceIn(0f, maxY)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(x.roundToInt(), y.roundToInt())
                }
            }
            .clip(RoundedCornerShape(999.dp))
            .background(colors.brandFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = colors.onBrandFill)
    }
}

private const val CALLOUT_OFFSET_PX = 8f

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
