@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.records.SampleImageSource
import com.agarthavision.domain.usecase.records.SampleRecordItem
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SampleDetailScreen(
    onBack: () -> Unit,
    viewModel: SampleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Gray50)) {
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Blue)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp) // Leave space for nav bar
            ) {
                // Segmented Control (Tabs)
                SampleSegmentedControl(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                // Tab Content
                when (selectedTab) {
                    0 -> ImageTab(item = item, imageSource = state.imageSource)
                    1 -> DetectionsTab(detections = item.detections)
                    else -> MetadataTab(sample = item.sample)
                }
            }
        }

        // Top Navigation Bar
        if (item != null) {
            SampleDetailNavBar(
                title = "Sample #${item.sample.id.take(4)}",
                onBack = onBack
            )
        }
    }
}

@Composable
fun SampleDetailNavBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(AppColors.Gray50)
            .padding(top = 32.dp, start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        Row(
            modifier = Modifier.clickable { onBack() }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = AppColors.Blue, modifier = Modifier.size(28.dp))
            Text("Back", color = AppColors.Blue, fontSize = 17.sp, modifier = Modifier.offset(x = (-4).dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = title,
            style = AppTypography.titleLarge,
            color = AppColors.Gray900,
            modifier = Modifier.offset(x = (-24).dp)
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SampleSegmentedControl(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("IMAGE", "DETECTIONS", "METADATA")

    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = AppColors.Gray50,
        contentColor = AppColors.Blue,
        indicator = { tabPositions ->
            if (selectedTab < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AppColors.Blue,
                    height = 2.dp
                )
            }
        },
        divider = {
            HorizontalDivider(color = AppColors.Gray200, thickness = 0.5.dp)
        }
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedTab == index) AppColors.Blue else AppColors.Gray500,
                        letterSpacing = 0.8.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun ImageTab(item: SampleRecordItem, imageSource: SampleImageSource) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .aspectRatio(1f) // Changed to 1f based on screenshot
                .background(Color.Black, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            when (imageSource) {
                is SampleImageSource.Local -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(imageSource.path))
                            .build(),
                        contentDescription = "Sample Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    NormalizedDetectionOverlay(
                        detections = item.detections,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is SampleImageSource.RemoteSignedUrl -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageSource.url)
                            .build(),
                        contentDescription = "Sample Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    NormalizedDetectionOverlay(
                        detections = item.detections,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is SampleImageSource.Unavailable -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Image Unavailable",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capture Metadata Strip
        val timeStr = Instant.ofEpochMilli(item.sample.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm:ss"))

        val provenanceText = if (item.detections.isNotEmpty()) "AI-Captured" else "Manually Captured"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppColors.Gray200, RoundedCornerShape(12.dp))
                .background(AppColors.White)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Capture Type", fontSize = 15.sp, color = AppColors.Gray900)
                Text(provenanceText, fontSize = 15.sp, color = AppColors.Gray500)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = AppColors.Gray200)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Timestamp", fontSize = 15.sp, color = AppColors.Gray900)
                Text(timeStr, fontSize = 15.sp, color = AppColors.Gray500, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
private fun NormalizedDetectionOverlay(detections: List<Detection>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        detections.forEach { detection ->
            val bx = detection.bboxX ?: return@forEach
            val by = detection.bboxY ?: return@forEach
            val bw = detection.bboxW ?: return@forEach
            val bh = detection.bboxH ?: return@forEach
            val left = bx.coerceIn(0f, 1f) * size.width
            val top = by.coerceIn(0f, 1f) * size.height
            val width = bw.coerceIn(0f, 1f) * size.width
            val height = bh.coerceIn(0f, 1f) * size.height
            val color = if (detection.verifiedByUser) AppColors.Blue else AppColors.Red
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
        Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "No detections on this sample.",
                color = AppColors.Gray500,
                fontSize = 15.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(detections) { index, detection ->
            DetectionCard(index = index, detection = detection)
        }
    }
}

@Composable
private fun DetectionCard(index: Int, detection: Detection) {
    val isVerified = detection.verifiedByUser
    val aiGenerated = detection.bboxX != null
    val provenanceText = if (aiGenerated) "AI DETECTION" else "MANUAL ANNOTATION"
    val speciesLabel = detection.expertClass ?: detection.classLabel
    val isItalic = speciesLabel.contains("Ascaris") || speciesLabel.contains("Trichuris") || speciesLabel.contains("Necator") || speciesLabel.contains("Hymenolepis") || speciesLabel.contains(".")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.White)
            .border(0.5.dp, AppColors.Gray200, RoundedCornerShape(14.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(24.dp).background(if (aiGenerated) AppColors.Blue else AppColors.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text((index + 1).toString(), color = AppColors.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = speciesLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Gray900,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                )
            }
            Box(
                modifier = Modifier
                    .background(AppColors.Gray100, RoundedCornerShape(100.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = provenanceText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Gray500,
                    letterSpacing = 0.4.sp
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Gray200)

        // Fields
        Column(modifier = Modifier.padding(start = 16.dp)) {
            val confidenceStr = if (aiGenerated) "${(detection.confidence * 100).toInt()}%" else "—"
            DetailRow(label = "Confidence", value = confidenceStr, isLast = false, valueFontFamily = FontFamily.Monospace)
            DetailRow(label = "Verdict", value = if (isVerified) "Verified" else "Rejected", valueColor = if (isVerified) AppColors.Green else AppColors.Red, isLast = false)

            val bboxStr = if (detection.bboxX != null) {
                String.format("[%.3f, %.3f, %.3f, %.3f]", detection.bboxX, detection.bboxY, detection.bboxW, detection.bboxH)
            } else {
                "None"
            }
            DetailRow(label = "Bounding Box", value = bboxStr, isLast = true, valueFontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MetadataTab(sample: Sample) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            GroupedList(title = "IDENTITY") {
                DetailRow(label = "Sample ID", value = sample.id, isLast = false, valueFontFamily = FontFamily.Monospace)
                DetailRow(label = "Session ID", value = sample.sessionId, isLast = false, valueFontFamily = FontFamily.Monospace)
                DetailRow(label = "Device ID", value = sample.deviceId, isLast = true, valueFontFamily = FontFamily.Monospace)
            }
        }

        item {
            GroupedList(title = "STATUS & TIMING") {
                val statusStr = sample.status.name.uppercase()
                val isSynced = sample.status == SampleStatus.SYNCED
                DetailRow(label = "Sync Status", value = statusStr, isLast = false, valueColor = if (isSynced) AppColors.Green else AppColors.Gray500)

                val capturedAt = Instant.ofEpochMilli(sample.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss"))
                DetailRow(label = "Captured At", value = capturedAt, isLast = false, valueFontFamily = FontFamily.Monospace)

                val verifiedAt = Instant.ofEpochMilli(sample.verifiedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss"))
                DetailRow(label = "Verified At", value = verifiedAt, isLast = true, valueFontFamily = FontFamily.Monospace)
            }
        }

        item {
            GroupedList(title = "CAPTURE DATA") {
                val locString = if (sample.latitude != null && sample.longitude != null) {
                    String.format("%.4f, %.4f", sample.latitude, sample.longitude)
                } else {
                    "None"
                }
                DetailRow(label = "Location", value = locString, isLast = false)
                DetailRow(label = "Model Version", value = sample.inferenceModelVersion, isLast = false)
                DetailRow(label = "Needs Reannotation", value = if (sample.needsReannotation) "Yes" else "No", isLast = true)
            }
        }
    }
}

@Composable
fun GroupedList(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray500,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppColors.Gray200, RoundedCornerShape(12.dp))
                .background(AppColors.White)
        ) {
            content()
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isLast: Boolean,
    valueColor: Color = AppColors.Gray500,
    valueFontFamily: FontFamily = FontFamily.Default
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = AppColors.Gray900)

        // Handle long monospace strings like UUIDs by breaking them
        val textModifier = if (valueFontFamily == FontFamily.Monospace) {
            Modifier.fillMaxWidth(0.6f)
        } else {
            Modifier
        }

        Text(
            text = value,
            fontSize = 15.sp,
            color = valueColor,
            fontFamily = valueFontFamily,
            textAlign = TextAlign.End,
            modifier = textModifier,
            style = if (valueFontFamily == FontFamily.Monospace) TextStyle(fontFeatureSettings = "tnum") else TextStyle.Default
        )
    }
    if (!isLast) {
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Gray200)
    }
}
