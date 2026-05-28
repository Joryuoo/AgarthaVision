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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agarthavision.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Data Model Mapping
data class SessionDetail(
    val id: String,                  
    val label: String?,
    val dateLabel: String,           
    val timeLabel: String,           
    val patientIdOrNote: String?,     
    val epg: Int,                    
    val confirmedEggs: Int,          
    val speciesCount: Int,           
    val samplesTotal: Int,           
    val syncedSamples: Int?,         
    val syncedAt: String?,           
    val verifiedSamples: List<Sample>
)

data class Sample(
    val id: String,
    val source: Source,           
    val species: String,          
    val confidence: Int?,
    val filePath: String?
)

enum class Source { AI, Manual }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onSampleClick: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.currentExportState.collectAsStateWithLifecycle()
    
    val sessionDetail = mapToUiModel(state)

    if (sessionDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Blue)
        }
        return
    }

    val isEmpty = sessionDetail.verifiedSamples.isEmpty()

    Scaffold(
        topBar = {
            SessionDetailAppBar(
                title = sessionDetail.label ?: "Session ${sessionDetail.id}",
                subtitle = if (sessionDetail.patientIdOrNote.isNullOrBlank()) "${sessionDetail.dateLabel} · ${sessionDetail.timeLabel}" else "${sessionDetail.dateLabel} · ${sessionDetail.timeLabel} · ${sessionDetail.patientIdOrNote}",
                onBack = onBack,
                onExport = viewModel::exportCsv
            )
        },
        containerColor = AppColors.White
    ) { inner ->
        if (isEmpty) {
            SessionDetailEmpty(
                session = sessionDetail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            )
        } else {
            SessionDetailPopulated(
                session = sessionDetail,
                onSampleClick = { sample -> onSampleClick(sample.id) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            )
        }
    }
}

private fun mapToUiModel(state: SessionDetailState): SessionDetail? {
    val sessionData = state.session ?: return null
    val sessionRecord = sessionData.session
    val startedAt = Instant.ofEpochMilli(sessionRecord.startedAt)
        .atZone(ZoneId.systemDefault())
        
    val dateLabel = startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val timeLabel = startedAt.format(DateTimeFormatter.ofPattern("HH:mm"))
    
    val samples = sessionData.samples.map { item ->
        val hasAi = item.detections.isNotEmpty()
        val source = if (hasAi) Source.AI else Source.Manual
        val species = if (hasAi) {
            item.primaryDetection?.classLabel ?: "Unknown"
        } else {
            "Manual"
        }
        val confidence = if (hasAi) {
            ((item.primaryDetection?.confidence ?: 0f) * 100).toInt()
        } else {
            null
        }
        
        Sample(
            id = item.sample.id,
            source = source,
            species = species,
            confidence = confidence,
            filePath = item.sample.storagePath
        )
    }
    
    // Calculate confirmed eggs. A simple proxy for now could be state.totalEggCount
    val confirmedEggs = state.totalEggCount
    val speciesCount = state.eggCounts.size
    
    return SessionDetail(
        id = sessionRecord.id.takeLast(4),
        label = sessionRecord.label,
        dateLabel = dateLabel,
        timeLabel = timeLabel,
        patientIdOrNote = sessionRecord.notes,
        epg = state.epg,
        confirmedEggs = confirmedEggs,
        speciesCount = speciesCount,
        samplesTotal = sessionData.samples.size,
        syncedSamples = null, // placeholder
        syncedAt = null,      // placeholder
        verifiedSamples = samples
    )
}

// Sub-composables

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailAppBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.White)
            .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = AppColors.Gray700,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(title,
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.Gray900)
            Text(subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Gray500,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = onExport) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = "Export",
                tint = AppColors.Gray700,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EpgHeroCard(
    epg: Int,
    confirmedEggs: Int,
    speciesCount: Int,
    samplesTotal: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Gray50, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Decorative blue radial accent (top-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppColors.BlueTint.copy(alpha = 0.6f),
                                Color.Transparent
                            ),
                            center = Offset(size.width, 0f),
                            radius = 220.dp.toPx()
                        )
                    )
                }
        )

        // Foreground content
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "EGGS PER GRAM",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Gray500,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                epg.toString(),
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Gray900,
                letterSpacing = (-2.2).sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03")
            )
            Spacer(Modifier.height(12.dp))
            EpgMeta(confirmedEggs, speciesCount, samplesTotal)
        }
    }
}

@Composable
private fun EpgMeta(confirmedEggs: Int, speciesCount: Int, samplesTotal: Int) {
    if (confirmedEggs == 0) {
        Text(
            "No confirmed eggs yet",
            fontSize = 13.sp,
            color = AppColors.Gray500
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaItem(confirmedEggs.toString(), "confirmed")
            DotSeparator()
            MetaItem(speciesCount.toString(), "species")
            DotSeparator()
            MetaItem(samplesTotal.toString(), "samples")
        }
    }
}

@Composable
private fun MetaItem(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray900,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = AppColors.Gray500
        )
    }
}

@Composable
private fun DotSeparator() {
    Text(
        "·",
        fontSize = 13.sp,
        color = AppColors.Gray300,
        modifier = Modifier.padding(horizontal = 7.dp)
    )
}

@Composable
private fun SyncBanner(
    syncedSamples: Int,
    syncedAt: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.GreenTint, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = AppColors.Green,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Synced to cloud",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14532D)
            )
            Text(
                "$syncedAt · $syncedSamples samples uploaded",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF166534),
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray900)
        Text(count,
            fontSize = 12.sp,
            color = AppColors.Gray500,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun SampleTile(
    sample: Sample,
    onClick: () -> Unit
) {
    val bboxColor = when (sample.source) {
        Source.AI -> AppColors.Blue
        Source.Manual -> AppColors.Amber
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.MicroscopeBrush)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Sample ${sample.id}, ${sample.species}" +
                    (sample.confidence?.let { ", $it percent confidence" } ?: ", manual capture")
            }
    ) {
        // Render the actual image underneath if we have it
        if (sample.filePath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(sample.filePath))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }



        // Species badge bottom-left
        SpeciesBadge(
            text = sample.species,
            isManual = sample.source == Source.Manual,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )
    }
}

@Composable
private fun ConfidenceChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.White.copy(alpha = 0.9f),
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun SpeciesBadge(text: String, isManual: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isManual) AppColors.Amber else AppColors.Blue
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.White,
            letterSpacing = 0.1.sp
        )
    }
}

@Composable
private fun SessionDetailPopulated(
    session: SessionDetail,
    onSampleClick: (Sample) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = Spacing.xs,
            bottom = Spacing.xxl
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                EpgHeroCard(
                    epg = session.epg,
                    confirmedEggs = session.confirmedEggs,
                    speciesCount = session.speciesCount,
                    samplesTotal = session.samplesTotal,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Eggs per gram: ${session.epg}, ${session.confirmedEggs} confirmed, ${session.speciesCount} species, ${session.samplesTotal} samples"
                    }
                )
                Spacer(Modifier.height(Spacing.lg))
                if (session.syncedSamples != null && session.syncedAt != null) {
                    SyncBanner(
                        syncedSamples = session.syncedSamples,
                        syncedAt = session.syncedAt
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
                SectionHeader(
                    title = "Verified samples",
                    count = "${session.verifiedSamples.size} of ${session.samplesTotal}"
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        items(session.verifiedSamples, key = { it.id }) { sample ->
            SampleTile(sample = sample, onClick = { onSampleClick(sample) })
        }
    }
}

@Composable
private fun SessionDetailEmpty(
    session: SessionDetail,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xs)
            .verticalScroll(rememberScrollState())
    ) {
        EpgHeroCard(
            epg = session.epg,
            confirmedEggs = session.confirmedEggs,
            speciesCount = session.speciesCount,
            samplesTotal = session.samplesTotal,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Eggs per gram: ${session.epg}, ${session.confirmedEggs} confirmed, ${session.speciesCount} species, ${session.samplesTotal} samples"
            }
        )

        Spacer(Modifier.height(60.dp))

        EmptyStateGraphic()
    }
}

@Composable
private fun EmptyStateGraphic() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AppColors.Gray50, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_minus_circle),
                contentDescription = null,
                tint = AppColors.Gray300,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "No verified samples yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray700
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Captured frames will appear here once verified.",
            fontSize = 13.sp,
            color = AppColors.Gray500,
            textAlign = TextAlign.Center
        )
    }
}
