@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.ui.components.glassChrome
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Design Tokens
private val Brand = Color(0xFF1E40AF)
private val BrandDeep = Color(0xFF1E3A8A)
private val BrandSecondary = Color(0xFF3B82F6)
private val Success = Color(0xFF34C759)
private val SuccessDeep = Color(0xFF248A3D)
private val Warning = Color(0xFFFF9F0A)
private val Danger = Color(0xFFFF3B30)
private val DangerDeep = Color(0xFFC9140B)
private val DangerTint = Color(0xFFFFE5E5)
private val Ink = Color(0xFF0F172A)
private val Body = Color(0xFF3C3C43)
private val Muted = Color(0xFF6E6E73)
private val Bg = Color(0xFFF2F2F7)
private val Surface = Color(0xFFFFFFFF)
private val Hairline = Color(0x1E3C3C43)
private val HairlineSoft = Color(0x143C3C43)

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onSampleClick: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.currentExportState.collectAsStateWithLifecycle()
    val session = state.session

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 103.dp, bottom = 40.dp)
            ) {
                item(span = { GridItemSpan(3) }) {
                    SessionHeroCard(
                        totalEpg = state.epg,
                        sampleCount = session.samples.size,
                        duration = "08:14", // Placeholder
                        syncStatus = "Synced" // Placeholder
                    )
                }

                item(span = { GridItemSpan(3) }) {
                    SessionMetadataCard(sessionData = session)
                }

                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = "SAMPLES (${session.samples.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                        modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                if (session.samples.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No samples found", color = Muted)
                        }
                    }
                } else {
                    items(session.samples, key = { it.sample.id }) { item ->
                        GalleryTile(item = item, onClick = { onSampleClick(item.sample.id) })
                    }
                }
            }
        }

        // Top Navigation Bar
        SessionDetailNavBar(
            title = session?.session?.label ?: "Unnamed Session",
            onBack = onBack,
            onExport = viewModel::exportCsv,
            isExporting = exportState.isExporting
        )
    }
}

@Composable
fun SessionDetailNavBar(
    title: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(103.dp)
            .glassChrome(
                backgroundColor = Color(255, 255, 255, (0.72f * 255).toInt()),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(top = 47.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button
        Row(
            modifier = Modifier.clickable { onBack() }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Brand, modifier = Modifier.size(28.dp))
            Text("Back", color = Brand, fontSize = 17.sp, modifier = Modifier.offset(x = (-4).dp))
        }

        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )

        Box(
            modifier = Modifier.clickable(enabled = !isExporting) { onExport() }.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Brand)
            } else {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "Export",
                    tint = Brand,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SessionHeroCard(totalEpg: Int, sampleCount: Int, duration: String, syncStatus: String) {
    val epgColor = when {
        totalEpg >= 250 -> DangerDeep
        totalEpg in 100..249 -> Warning
        totalEpg in 1..99 -> BrandDeep
        else -> SuccessDeep
    }
    
    val bgGradient = when {
        totalEpg >= 250 -> listOf(Color(255, 240, 240), Color(255, 250, 250))
        totalEpg in 100..249 -> listOf(Color(255, 248, 230), Color(255, 252, 240))
        totalEpg in 1..99 -> listOf(Color(240, 248, 255), Color(250, 252, 255))
        else -> listOf(Color(240, 255, 244), Color(250, 255, 252))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Ink.copy(alpha = 0.05f))
            .background(brush = Brush.verticalGradient(bgGradient), shape = RoundedCornerShape(18.dp))
            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "TOTAL EPG",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Muted
                    )
                    Text(
                        text = totalEpg.toString(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        color = epgColor,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                }

                // Severity Pill
                val (severityText, pillColor, textColor) = when {
                    totalEpg >= 250 -> Triple("Heavy", Danger, Color.White)
                    totalEpg in 100..249 -> Triple("Moderate", Warning, Color.White)
                    totalEpg in 1..99 -> Triple("Light", BrandSecondary, Color.White)
                    else -> Triple("Negative", Success, Color.White)
                }

                Box(
                    modifier = Modifier
                        .background(pillColor, RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = severityText,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("SAMPLES", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Text(sampleCount.toString(), fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("DURATION", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Text(duration, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SYNC", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(12.dp))
                        Text(syncStatus, fontSize = 15.sp, color = SuccessDeep, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionMetadataCard(sessionData: com.agarthavision.domain.usecase.records.SessionSamples) {
    val session = sessionData.session
    val startedAt = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
    ) {
        MetadataRow(label = "Patient ID", value = "Anon-01") // Placeholder
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = HairlineSoft)
        MetadataRow(label = "Started", value = startedAt)
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = HairlineSoft)
        MetadataRow(label = "Operator", value = "Dr. Smith") // Placeholder
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = HairlineSoft)
        
        val lat = sessionData.samples.firstNotNullOfOrNull { it.sample.latitude }
        val lon = sessionData.samples.firstNotNullOfOrNull { it.sample.longitude }
        val locString = if (lat != null && lon != null) {
            String.format("%.4f, %.4f", lat, lon)
        } else {
            "Unknown"
        }
        MetadataRow(label = "Location", value = locString)
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = Ink)
        Text(value, fontSize = 15.sp, color = Muted)
    }
}

@Composable
fun GalleryTile(item: SampleRecordItem, onClick: () -> Unit) {
    val hasAi = item.detections.isNotEmpty() // Simple logic: if detections exist, it's AI
    val tagText = if (hasAi) "AI ${item.primaryDetection?.confidence ?: ""}" else "MAN"

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(0.5.dp, HairlineSoft) // Grid lines between tiles
            .clickable { onClick() }
    ) {
        val storagePath = item.sample.storagePath
        if (storagePath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(storagePath))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop, // 220% background crop essentially
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.LightGray))
        }

        // Bbox hint overlay could go here if we do a small canvas pass
        
        // AI/MAN Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = tagText,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
