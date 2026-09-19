@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppTypography
import com.agarthavision.ui.theme.Spacing
import com.agarthavision.ui.theme.detectionBoxColor
import com.agarthavision.ui.verify.VerificationSheet
import com.agarthavision.ui.verify.frameTransform
import com.agarthavision.ui.verify.toCanvasX
import com.agarthavision.ui.verify.toCanvasY
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Sample Data Screen: where a **verified** sample is reviewed and corrected.
 *
 * This is where the capability PB-12 removed from the Verification Queue lands. The queue means
 * one thing again — work still to do — and re-opening something already checked happens here.
 *
 * **The medtech does not see the sample's metadata.** Storage path, sync status, model version,
 * ids: all gone with the tab that held them. That is the admin's concern. All the medtech does
 * here is annotate and correct the sample they took, so what is left is the frame, when it was
 * captured, and one row per detection.
 */
@Composable
fun SampleDetailScreen(
    onBack: () -> Unit,
    viewModel: SampleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val item = state.item
    val unavailable = state.unavailable

    Box(modifier = Modifier.fillMaxSize().background(AgarthaTheme.colors.background)) {
        when {
            !state.itemResolved -> SampleDetailSkeleton(onBack = onBack)
            unavailable != null -> SampleDetailUnavailableScreen(
                unavailable = unavailable,
                onBack = onBack,
            )
            item != null -> SampleDetailContent(
                item = item,
                imageSource = state.imageSource,
                onBack = onBack,
                onViewDetection = viewModel::onViewDetection,
            )
            else -> SampleDetailSkeleton(onBack = onBack)
        }
    }

    // Edit mode is the Verification Screen, seeded with the medtech's own previous answers -
    // the same screen and the same submit path, because an edit is a correction rather than a
    // second kind of review. SubmitVerificationUseCase is idempotent per sample, with detection
    // ids derived rather than random: a random id would append a second full set of detections
    // on every re-save and silently double every egg count.
    val target = editState.target
    if (target != null) {
        VerificationSheet(
            frame = target.frame,
            onDismiss = viewModel::onEditDismissed,
            prior = target,
        )
    }
}

@Composable
private fun SampleDetailContent(
    item: SampleRecordItem,
    imageSource: SampleImageSource,
    onBack: () -> Unit,
    onViewDetection: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val capturedAt = remember(item.sample.timestamp) {
        Instant.ofEpochMilli(item.sample.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(CAPTURED_AT_FORMAT)
    }

    // Which boxes are drawn, by detection index. Held here rather than in the ViewModel because
    // it is a way of looking at the frame, not a fact about the sample: it has no business
    // surviving the screen, and nothing else in the app can read it.
    var hidden by remember(item.sample.id) { mutableStateOf(emptySet<Int>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        SampleDetailNavBar(title = capturedAt, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Square, because every frame the capture pipeline emits is square.
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant),
                ) {
                    SampleFrame(imageSource = imageSource)
                    DetectionOverlay(
                        detections = item.detections,
                        hiddenIndices = hidden,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(SampleDetailTestTags.DETECTION_OVERLAY),
                    )
                }
            }

            if (item.detections.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sample_detail_no_detections),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.sample_detail_detections_heading),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                itemsIndexed(item.detections) { index, detection ->
                    DetectionRow(
                        index = index,
                        detection = detection,
                        visible = index !in hidden,
                        onToggle = {
                            hidden = if (index in hidden) hidden - index else hidden + index
                        },
                    )
                }
            }

            item {
                ViewDetectionButton(
                    onClick = onViewDetection,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SampleFrame(imageSource: SampleImageSource) {
    when (imageSource) {
        is SampleImageSource.Local -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(imageSource.path))
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.sample_detail_image_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Local file first, then a 15-minute signed Storage URL - kept, because a synced sample
        // on a re-installed device has no local file. Keyed on the stable storage path rather
        // than the URL, which carries a fresh token every time it is minted, so the disk cache
        // outlives the signature instead of missing on every open.
        is SampleImageSource.RemoteSignedUrl -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageSource.url)
                .memoryCacheKey(imageSource.cacheKey)
                .diskCacheKey(imageSource.cacheKey)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.sample_detail_image_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Says which kind of unavailable, because the two need different things from the
        // medtech: one waits for a sync, the other for a connection. A blank frame they might
        // annotate into the void is the bug underneath both.
        is SampleImageSource.Unavailable -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    when (imageSource.reason) {
                        SampleImageUnavailableReason.NO_STORAGE_PATH ->
                            R.string.sample_detail_image_no_storage_path
                        SampleImageUnavailableReason.REMOTE_LOAD_FAILED ->
                            R.string.sample_detail_image_remote_failed
                        SampleImageUnavailableReason.SAMPLE_NOT_FOUND ->
                            R.string.sample_detail_image_unavailable
                    },
                ),
                color = AgarthaTheme.colors.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The detection boxes, each in its own colour, over the frame.
 *
 * ## This was drawing every box in the wrong place
 *
 * The overlay this replaces treated `bbox_*` as **normalised 0–1 with a top-left origin**:
 *
 * ```
 * val left = bx.coerceIn(0f, 1f) * size.width
 * ```
 *
 * What is stored is **centre-based pixels in the source image's space**.
 * `VerificationMapper` copies `Prediction.x/y/width/height` straight through, and
 * `Prediction`'s own KDoc says so. For a real detection at `x = 320f`, `coerceIn(0f, 1f)`
 * clamped it to `1.0` and the box landed in the bottom-right corner of the frame. Every box.
 *
 * **The `coerceIn` is what hid it** — it turned an out-of-range number into a plausible-looking
 * rectangle instead of anything visible as wrong. It is gone, not widened: a box outside the
 * frame means the data is wrong and should look wrong.
 *
 * The arithmetic below is the same `frameTransform` the Verification Screen draws with, imported
 * rather than copied. Two screens computing the same letterbox two ways is how the boxes drift
 * apart again.
 *
 * ## Dimensions
 *
 * The domain `Sample` carries no image dimensions at all, so this defaults to
 * [CAPTURE_FRAME_SIZE_PX]. That is safe by construction rather than a guess: `toJpegBytes()`
 * centre-crops and downscales every frame to a 640 square before it is ever posted, so 640 is
 * the only value a stored dimension holds in practice.
 *
 * **One exception, and it is the reason to read this twice:** a device whose camera cannot supply
 * a 640 stream encodes at its own smaller native square rather than upscaling
 * (`ImageExtensions.kt`). A sample from such a device renders its boxes slightly off here.
 * Carrying the real dimensions down would mean adding them to the domain `Sample` and to
 * `SampleRemoteDataSource.toEntity()`, which sets `imageWidth = null` on **every** sample pulled
 * from Supabase. That is the fix; this comment is the placeholder for it. Silently assuming 640
 * for every sample is how the bug above happened the first time.
 */
@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    hiddenIndices: Set<Int>,
    modifier: Modifier = Modifier,
) {
    // Capture colours at composition time — DrawScope inside Canvas is not @Composable.
    val colors = remember(detections.size) { List(detections.size) { detectionBoxColor(it) } }
    Canvas(modifier = modifier) {
        val transform = frameTransform(
            canvasWidth = size.width,
            canvasHeight = size.height,
            sourceWidth = CAPTURE_FRAME_SIZE_PX.toFloat(),
            sourceHeight = CAPTURE_FRAME_SIZE_PX.toFloat(),
        ) ?: return@Canvas

        detections.forEachIndexed { index, detection ->
            if (index in hiddenIndices) return@forEachIndexed
            // A detection with no box is a valid finding - an egg the medtech added and did not
            // draw - and simply has nothing to render.
            val cx = detection.bboxX ?: return@forEachIndexed
            val cy = detection.bboxY ?: return@forEachIndexed
            val bw = detection.bboxW ?: return@forEachIndexed
            val bh = detection.bboxH ?: return@forEachIndexed
            drawRect(
                color = colors[index],
                topLeft = Offset(
                    transform.toCanvasX(cx - bw / 2f),
                    transform.toCanvasY(cy - bh / 2f),
                ),
                size = Size(bw * transform.scale, bh * transform.scale),
                style = Stroke(width = 3f),
            )
        }
    }
}

/**
 * One detection: its colour, what it was called, and whether its box is drawn.
 *
 * The swatch is what ties the row to the rectangle on the frame, which is the whole reason no
 * two boxes share a colour. There is no verdict, no confidence and no coordinates here — those
 * are the admin's concern, and for the clinical read a box is a box.
 */
@Composable
private fun DetectionRow(
    index: Int,
    detection: Detection,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val label = detection.expertClass ?: detection.classLabel
    val isBinomial = label.contains(' ') || label.contains('.')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(0.5.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .testTag(SampleDetailTestTags.detectionRow(index))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(detectionBoxColor(index))
                .border(0.5.dp, colors.border, CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            fontStyle = if (isBinomial) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = stringResource(
                if (visible) R.string.sample_detail_hide_box else R.string.sample_detail_show_box,
                label,
            ),
            tint = if (visible) colors.accent else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Opens the Verification Screen on this sample, seeded with what the medtech said last time.
 *
 * Offered whether or not the sample has any detections: a frame the model called clean is still
 * a frame a medtech may want to add an egg to, and a frame captured with the container
 * unreachable has never had a detection to begin with.
 */
@Composable
private fun ViewDetectionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accent)
            .clickable(onClick = onClick)
            .testTag(SampleDetailTestTags.VIEW_DETECTION)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sample_detail_view_detection),
            color = colors.onAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SampleDetailUnavailableScreen(
    unavailable: SampleUnavailable,
    onBack: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
            ) {
                BackArrow(onBack = onBack)
            }
        },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = Alignment.Center,
        ) {
            when (unavailable) {
                SampleUnavailable.NOT_FOUND -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.sample_detail_not_found_title),
                    body = stringResource(R.string.sample_detail_not_found_body),
                )
                SampleUnavailable.NOT_VISIBLE -> EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.sample_detail_not_visible_title),
                    body = stringResource(R.string.sample_detail_not_visible_body),
                )
            }
        }
    }
}

/**
 * Back, and the sample's label.
 *
 * The label is the time the frame was captured — the same one the verification queue row and the
 * Verification Screen lead with, rather than the "Sample #a1b2" id fragment this used to show.
 * An id fragment is metadata, and it is also not something a medtech can recognise.
 */
@Composable
fun SampleDetailNavBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgarthaTheme.colors.background)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackArrow(onBack = onBack)
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = AppTypography.titleLarge,
            color = AgarthaTheme.colors.textPrimary,
        )
    }
}

/** Stable handles for this screen's UI tests. */
internal object SampleDetailTestTags {
    const val DETECTION_OVERLAY = "sample_detection_overlay"

    /** Opens the Verification Screen in edit mode. */
    const val VIEW_DETECTION = "sample_view_detection"

    fun detectionRow(index: Int): String = "sample_detection_row_$index"
}

private val CAPTURED_AT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss")
