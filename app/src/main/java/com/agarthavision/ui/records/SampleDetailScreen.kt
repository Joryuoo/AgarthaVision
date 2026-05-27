@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleImageUnavailableReason
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.komoui.themes.styles
import java.io.File

/**
 * Shows a persisted sample image, detection overlay, and traceability metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleDetailScreen(
    onBack: () -> Unit,
    viewModel: SampleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sample_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.sample_detail_back),
                            tint = MaterialTheme.styles.foreground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground,
                ),
            )
        },
    ) { padding ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.sample_detail_missing),
                    color = MaterialTheme.styles.mutedForeground,
                )
            }
        } else {
            SampleDetailContent(
                item = item,
                imageSource = state.imageSource,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun SampleDetailContent(
    item: SampleRecordItem,
    imageSource: SampleImageSource,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.sample_detail_tab_image),
        stringResource(R.string.sample_detail_tab_detections),
        stringResource(R.string.sample_detail_tab_metadata),
    )

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.styles.background) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) },
                )
            }
        }

        when (selectedTab) {
            0 -> ImageTab(item = item, imageSource = imageSource)
            1 -> DetectionsTab(detections = item.detections)
            else -> MetadataTab(sample = item.sample)
        }
    }
}

@Composable
private fun ImageTab(item: SampleRecordItem, imageSource: SampleImageSource) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.card,
            contentColor = MaterialTheme.styles.cardForeground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (imageSource) {
                is SampleImageSource.Local,
                is SampleImageSource.RemoteSignedUrl,
                -> {
                    AsyncImage(
                        model = imageSource.toCoilModel(),
                        contentDescription = stringResource(R.string.sample_detail_image_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    NormalizedDetectionOverlay(
                        detections = item.detections,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is SampleImageSource.Unavailable -> {
                    Text(
                        text = imageSource.reason.toDisplayText(),
                        color = MaterialTheme.styles.mutedForeground,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleImageSource.toCoilModel(): Any =
    when (this) {
        is SampleImageSource.Local -> File(path)
        is SampleImageSource.RemoteSignedUrl -> ImageRequest.Builder(LocalContext.current)
            .data(url)
            .diskCacheKey(cacheKey)
            .memoryCacheKey(cacheKey)
            .build()
        is SampleImageSource.Unavailable -> error("Unavailable sample images cannot be converted to Coil models.")
    }

@Composable
private fun SampleImageUnavailableReason.toDisplayText(): String =
    when (this) {
        SampleImageUnavailableReason.SAMPLE_NOT_FOUND -> stringResource(R.string.sample_detail_missing)
        SampleImageUnavailableReason.NO_STORAGE_PATH -> stringResource(R.string.sample_detail_image_no_storage_path)
        SampleImageUnavailableReason.REMOTE_LOAD_FAILED -> stringResource(R.string.sample_detail_image_remote_failed)
    }

@Composable
private fun NormalizedDetectionOverlay(detections: List<Detection>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.styles.primary
    val destructive = MaterialTheme.styles.destructive
    Canvas(modifier = modifier) {
        detections.forEach { detection ->
            val left = detection.bboxX.coerceIn(0f, 1f) * size.width
            val top = detection.bboxY.coerceIn(0f, 1f) * size.height
            val width = detection.bboxW.coerceIn(0f, 1f) * size.width
            val height = detection.bboxH.coerceIn(0f, 1f) * size.height
            val color = if (detection.verifiedByUser) primary else destructive
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
private fun DetectionsTab(detections: List<Detection>) {
    if (detections.isEmpty()) {
        Text(
            text = stringResource(R.string.sample_detail_no_detections),
            color = MaterialTheme.styles.mutedForeground,
            modifier = Modifier.padding(20.dp),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        itemsIndexed(detections) { index, detection ->
            DetectionCard(index = index, detection = detection)
        }
    }
}

@Composable
private fun DetectionCard(index: Int, detection: Detection) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.secondary,
            contentColor = MaterialTheme.styles.secondaryForeground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.sample_detail_detection_title, index + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.styles.foreground,
            )
            Text(detection.expertClass ?: detection.classLabel, color = MaterialTheme.styles.foreground)
            Text(
                text = stringResource(R.string.session_detail_confidence, detection.confidence),
                color = MaterialTheme.styles.mutedForeground,
            )
            Text(
                text = stringResource(R.string.sample_detail_verdict, detection.verdict.value),
                color = MaterialTheme.styles.mutedForeground,
            )
            Text(
                text = stringResource(
                    R.string.sample_detail_box,
                    detection.bboxX,
                    detection.bboxY,
                    detection.bboxW,
                    detection.bboxH,
                ),
                color = MaterialTheme.styles.mutedForeground,
            )
        }
    }
}

@Composable
private fun MetadataTab(sample: Sample) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MetadataRow(stringResource(R.string.sample_detail_sample_id), sample.id) }
        item { MetadataRow(stringResource(R.string.sample_detail_session_id), sample.sessionId) }
        item { MetadataRow(stringResource(R.string.sample_detail_device_id), sample.deviceId) }
        item { MetadataRow(stringResource(R.string.sample_detail_status), sample.status.value) }
        item { MetadataRow(stringResource(R.string.sample_detail_captured_at), sample.timestamp.formatDateTime()) }
        item { MetadataRow(stringResource(R.string.sample_detail_verified_at), sample.verifiedAt.formatDateTime()) }
        item {
            MetadataRow(
                label = stringResource(R.string.sample_detail_gps),
                value = if (sample.latitude != null && sample.longitude != null) {
                    stringResource(R.string.records_gps, sample.latitude, sample.longitude)
                } else {
                    stringResource(R.string.records_no_gps)
                },
            )
        }
        item { MetadataRow(stringResource(R.string.sample_detail_storage_path), sample.storagePath.orEmpty()) }
        item { MetadataRow(stringResource(R.string.sample_detail_model_version), sample.inferenceModelVersion) }
        item {
            MetadataRow(
                stringResource(R.string.sample_detail_needs_reannotation),
                if (sample.needsReannotation) {
                    stringResource(R.string.sample_detail_yes)
                } else {
                    stringResource(R.string.sample_detail_no)
                },
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.styles.mutedForeground,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.styles.foreground,
        )
    }
}
