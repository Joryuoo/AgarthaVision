@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.SessionRecordItem
import com.agarthavision.ui.components.AgarthaBottomBar
import com.agarthavision.ui.components.glassChrome
import com.agarthavision.ui.navigation.Screen
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
private val Ink = Color(0xFF0F172A)
private val Body = Color(0xFF3C3C43)
private val Muted = Color(0xFF6E6E73)
private val Bg = Color(0xFFF2F2F7)
private val Surface = Color(0xFFFFFFFF)
private val Hairline = Color(0x1E3C3C43)
private val HairlineSoft = Color(0x143C3C43)
private val IosFillThin = Color(0x1E787880) // rgba(120, 120, 128, 0.12)

@Composable
fun RecordsScreen(
    onNavigate: (String) -> Unit = {},
    onSessionClick: (String) -> Unit,
    onBackClick: () -> Unit = {},
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Scaffold(
            bottomBar = { AgarthaBottomBar(activeRoute = Screen.Records.route, onNavigate = onNavigate) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 103.dp) // Leave space for nav bar
            ) {
                // Overview Cards
                RecordsOverviewRow(
                    thisWeekSessions = state.sessions.size, // Placeholder logic
                    totalSamples = state.sessions.sumOf { it.sampleCount },
                    avgEpg = if (state.sessions.isNotEmpty()) state.sessions.sumOf { it.totalEpg } / state.sessions.size else 0
                )
                
                // Search Bar
                RecordsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                
                // Filter Chips
                RecordsFilterChips(
                    selectedSpecies = state.selectedSpecies,
                    onSpeciesSelected = viewModel::onSpeciesSelected
                )
                
                // Records List
                val filteredSessions = state.sessions.filter {
                    (searchQuery.isEmpty() || it.session.id.contains(searchQuery, ignoreCase = true) || (it.session.label?.contains(searchQuery, ignoreCase = true) == true))
                }
                
                if (filteredSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No sessions found", color = Muted, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        // Grouping logic (Placeholder logic: just groups by day string)
                        val grouped = filteredSessions.groupBy {
                            Instant.ofEpochMilli(it.session.startedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM dd"))
                        }
                        
                        grouped.forEach { (day, sessions) ->
                            item {
                                Text(
                                    text = day.uppercase(), // e.g., TODAY · MAY 28
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 8.dp)
                                )
                            }
                            
                            items(sessions, key = { it.session.id }) { sessionItem ->
                                RecordRow(item = sessionItem, onClick = { onSessionClick(sessionItem.session.id) })
                            }
                        }
                    }
                }
            }
        }
        
        RecordsNavBar(onHomeClick = { onNavigate(Screen.Dashboard.route) })
    }
}

@Composable
fun RecordsNavBar(onHomeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 47.dp)
            .height(56.dp)
            .glassChrome(
                backgroundColor = Color(255, 255, 255, (0.72f * 255).toInt()),
                shape = RoundedCornerShape(0.dp) // Flat bottom
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left - Home
        Box(
            modifier = Modifier.size(32.dp).clickable { onHomeClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Home, contentDescription = "Home", tint = Brand, modifier = Modifier.size(24.dp))
        }
        
        // Center - Title
        Text(
            text = "Records",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            color = Ink
        )
        
        // Right - Export
        Box(
            modifier = Modifier.size(32.dp).clickable { /* Export logic */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Download, contentDescription = "Export", tint = Brand, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun RecordsOverviewRow(thisWeekSessions: Int, totalSamples: Int, avgEpg: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Brand card
        Box(
            modifier = Modifier
                .weight(1.5f)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Brand.copy(alpha = 0.22f),
                    ambientColor = Brand.copy(alpha = 0.14f)
                )
                .background(
                    brush = Brush.linearGradient(listOf(Brand, BrandSecondary)),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text("THIS WEEK", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(thisWeekSessions.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = Color.White, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Sessions completed", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
            }
        }
        
        // Samples Card
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Ink.copy(alpha = 0.05f))
                .background(Surface, RoundedCornerShape(16.dp))
                .border(0.5.dp, HairlineSoft, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text("SAMPLES", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Muted)
                Spacer(modifier = Modifier.height(6.dp))
                Text(totalSamples.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = Ink, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("+12%", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = SuccessDeep) // Placeholder
            }
        }
        
        // Avg EPG Card
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Ink.copy(alpha = 0.05f))
                .background(Surface, RoundedCornerShape(16.dp))
                .border(0.5.dp, HairlineSoft, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text("AVG EPG", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Muted)
                Spacer(modifier = Modifier.height(6.dp))
                Text(avgEpg.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = Ink, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("7-day", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Muted)
            }
        }
    }
}

@Composable
fun RecordsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 12.dp)
            .background(IosFillThin, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search by ID, patient, or species", color = Muted, fontSize = 15.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Ink, fontFamily = FontFamily.Default),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = SolidColor(Brand),
                singleLine = true
            )
        }
    }
}

@Composable
fun RecordsFilterChips(selectedSpecies: EggSpecies?, onSpeciesSelected: (EggSpecies?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All chip
        Box(
            modifier = Modifier
                .background(if (selectedSpecies == null) Ink else IosFillThin, RoundedCornerShape(100.dp))
                .clickable { onSpeciesSelected(null) }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text("All", color = if (selectedSpecies == null) Color.White else Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        
        val quickSpecies = listOf(
            EggSpecies.ASCARIS to "A. lumbricoides",
            EggSpecies.TRICHURIS to "T. trichiura",
            EggSpecies.HOOKWORM to "Hookworm"
        )
        
        quickSpecies.forEach { (species, label) ->
            val isSelected = selectedSpecies == species
            val isItalic = species != EggSpecies.HOOKWORM
            Box(
                modifier = Modifier
                    .background(if (isSelected) Ink else IosFillThin, RoundedCornerShape(100.dp))
                    .clickable { onSpeciesSelected(species) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label, 
                    color = if (isSelected) Color.White else Body, 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
    }
}

@Composable
fun RecordRow(item: SessionRecordItem, onClick: () -> Unit) {
    val session = item.session
    val timeStr = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
        
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Ink.copy(alpha = 0.05f))
            .background(Surface, RoundedCornerShape(16.dp))
            .border(0.5.dp, HairlineSoft, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Body (Left)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${session.id.take(4)}", // Mock ID format
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = (-0.3).sp,
                color = Ink,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            
            Text(
                text = "$timeStr · ${item.sampleCount} samples",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
                lineHeight = 16.sp
            )
            
            // Species tags
            if (item.speciesLabels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.speciesLabels.forEach { speciesLabel ->
                        val isItalic = speciesLabel.contains("Ascaris") || speciesLabel.contains("Trichuris") || speciesLabel.contains(".")
                        Box(
                            modifier = Modifier
                                .background(Color(120, 120, 128, (0.1f * 255).toInt()), RoundedCornerShape(100.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = speciesLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Body,
                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                letterSpacing = (-0.1).sp
                            )
                        }
                    }
                }
            } else if (item.totalEpg == 0) {
                 Spacer(modifier = Modifier.height(6.dp))
                 Box(
                    modifier = Modifier
                        .background(Color(120, 120, 128, (0.1f * 255).toInt()), RoundedCornerShape(100.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Negative",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Body,
                        fontStyle = FontStyle.Normal,
                        letterSpacing = (-0.1).sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // EPG Block (Middle)
        Column(
            modifier = Modifier.width(64.dp),
            horizontalAlignment = Alignment.End
        ) {
            val epgColor = when {
                item.totalEpg >= 250 -> Danger
                item.totalEpg in 100..249 -> Warning
                item.totalEpg in 1..99 -> Ink
                else -> SuccessDeep
            }
            Text(
                text = item.totalEpg.toString(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.6).sp,
                color = epgColor,
                lineHeight = 22.sp
            )
            Text(
                text = "EPG",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                color = Muted,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Sync Status (Right)
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder: All synced
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Synced",
                tint = Success,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
