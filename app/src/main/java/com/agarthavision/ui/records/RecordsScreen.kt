package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.SessionRecordItem
import com.agarthavision.ui.navigation.Screen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SyncStatus { Synced, PendingSync }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onNavigate: (String) -> Unit = {},
    onSessionClick: (String) -> Unit,
    onBackClick: () -> Unit = {},
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    AgarthaTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var searchText by remember { mutableStateOf("") }

        val filteredRecords = remember(state.sessions, searchText, state.selectedSpecies) {
            state.sessions.filter { item ->
                val matchesSearch = searchText.isEmpty() ||
                        item.session.id.contains(searchText, ignoreCase = true) ||
                        (item.session.label?.contains(searchText, ignoreCase = true) == true)
                val matchesSpecies = state.selectedSpecies == null || 
                        item.speciesLabels.any { label -> 
                            val speciesName = when (state.selectedSpecies) {
                                EggSpecies.ASCARIS -> "Ascaris"
                                EggSpecies.TRICHURIS -> "Trichuris"
                                EggSpecies.HOOKWORM -> "Hookworm"
                                else -> ""
                            }
                            label.contains(speciesName, ignoreCase = true)
                        }
                matchesSearch && matchesSpecies
            }
        }

        val totalEggs = state.sessions.sumOf { it.totalEpg }
        val accuracy = if (state.sessions.isNotEmpty()) "94%" else "0%" // Placeholder for accuracy

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    RecordsAppBar(onExport = { /* trigger export flow */ })
                },
                containerColor = AppColors.White,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { inner ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentPadding = PaddingValues(bottom = Spacing.xxl)
                ) {
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SearchInput(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.padding(horizontal = Spacing.xl)
                        )
                    }
                    item {
                        Spacer(Modifier.height(Spacing.md))
                        StatsRow(
                            sessionsCount = state.sessions.size.toString(),
                            eggsCount = totalEggs.toString(),
                            accuracy = accuracy,
                            modifier = Modifier.padding(horizontal = Spacing.xl)
                        )
                    }
                    item {
                        Spacer(Modifier.height(Spacing.md))
                        SpeciesFilterChips(
                            selected = state.selectedSpecies,
                            onSelect = viewModel::onSpeciesSelected
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.xs)) }
                    items(filteredRecords, key = { it.session.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onSessionClick(record.session.id) },
                            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordsAppBar(onExport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.White)
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Records",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.Gray900
            )
            Text(
                "Past 30 days",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Gray500,
                modifier = Modifier.padding(top = 2.dp)
            )
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
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search sessions, patient ID, species…",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Gray400
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = AppColors.Gray400,
                modifier = Modifier.size(18.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = AppColors.Gray50,
            focusedContainerColor   = AppColors.White,
            unfocusedBorderColor    = AppColors.Gray200,
            focusedBorderColor      = AppColors.Blue,
            cursorColor             = AppColors.Blue,
            unfocusedTextColor      = AppColors.Gray900,
            focusedTextColor        = AppColors.Gray900
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun StatsRow(
    sessionsCount: String,
    eggsCount: String,
    accuracy: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StatTile("Sessions",   sessionsCount,   Modifier.weight(1f))
        StatTile("Eggs found", eggsCount,  Modifier.weight(1f))
        StatTile("Accuracy",   accuracy,  Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AppColors.Gray50, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray500,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Gray900,
            letterSpacing = (-0.4).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03"),
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun SpeciesFilterChips(
    selected: EggSpecies?,
    onSelect: (EggSpecies?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { SpeciesChip("All species", null, selected == null, { onSelect(null) }) }
        item { SpeciesChip(null, "Ascaris", selected == EggSpecies.ASCARIS, { onSelect(EggSpecies.ASCARIS) }) }
        item { SpeciesChip(null, "Trichuris", selected == EggSpecies.TRICHURIS, { onSelect(EggSpecies.TRICHURIS) }) }
        item { SpeciesChip("Hookworm", null, selected == EggSpecies.HOOKWORM, { onSelect(EggSpecies.HOOKWORM) }) }
    }
}

@Composable
private fun SpeciesChip(
    plainLabel: String?,
    italicLabel: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg     = if (selected) AppColors.Gray900 else AppColors.White
    val border = if (selected) AppColors.Gray900 else AppColors.Gray200
    val text   = if (selected) AppColors.White   else AppColors.Gray700

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = plainLabel ?: italicLabel.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = text,
            fontStyle = if (italicLabel != null) FontStyle.Italic else FontStyle.Normal
        )
    }
}

@Composable
private fun RecordCard(
    record: SessionRecordItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateLabel = Instant.ofEpochMilli(record.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
    val timeLabel = Instant.ofEpochMilli(record.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
        
    val metaText = if (record.session.notes.isNullOrBlank()) {
        "$dateLabel · $timeLabel"
    } else {
        "$dateLabel · $timeLabel · ${record.session.notes}"
    }
    
    val syncStatus = SyncStatus.Synced // Mocked for now
    
    val speciesCount = if (record.speciesLabels.isEmpty()) "—" else record.speciesLabels.size.toString()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Header row: ID + time + status pill
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.session.label ?: "Session ${record.session.id.take(4)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.Gray900
                )
                Text(
                    metaText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Gray500,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            StatusPill(syncStatus)
        }

        // Divider
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AppColors.Gray100, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RecordStat(record.totalEpg.toString(), "eggs")
            RecordStat(speciesCount, "species")
            RecordStat(record.sampleCount.toString(), "samples")
        }
    }
}

@Composable
private fun RecordStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Gray900,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = AppColors.Gray500
        )
    }
}

@Composable
private fun StatusPill(status: SyncStatus) {
    val (bg, fg, text) = when (status) {
        SyncStatus.Synced       -> Triple(AppColors.GreenTint, AppColors.GreenText, "Synced")
        SyncStatus.PendingSync  -> Triple(AppColors.AmberTint, AppColors.AmberText, "Pending sync")
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            letterSpacing = 0.1.sp
        )
    }
}
