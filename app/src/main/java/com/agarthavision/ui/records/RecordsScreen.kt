package com.agarthavision.ui.records

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.SessionRecordItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SyncStatus { Synced, PendingSync }

@Composable
fun RecordsScreen(
    @Suppress("UNUSED_PARAMETER")
    onNavigate: (String) -> Unit = {},
    onSessionClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onBackClick: () -> Unit = {},
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    AgarthaTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var searchText by remember { mutableStateOf("") }

        val filteredRecords = remember(state.sessions, searchText) {
            state.sessions.filter { item ->
                searchText.isBlank() ||
                    item.session.id.contains(searchText, ignoreCase = true) ||
                    item.session.label?.contains(searchText, ignoreCase = true) == true ||
                    item.session.notes?.contains(searchText, ignoreCase = true) == true ||
                    item.speciesLabels.any { it.contains(searchText, ignoreCase = true) }
            }
        }

        val totalEggs = state.sessions.sumOf { it.totalEpg }
        val totalSamples = state.sessions.sumOf { it.sampleCount }

        Scaffold(
            topBar = {
                RecordsAppBar(
                    subtitle = if (state.startDate == null && state.endDate == null) {
                        "All sessions"
                    } else {
                        "Filtered date range"
                    },
                )
            },
            containerColor = AppColors.White,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { inner ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                item {
                    Spacer(Modifier.height(Spacing.xs))
                    SearchInput(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
                item {
                    Spacer(Modifier.height(Spacing.md))
                    StatsRow(
                        sessionsCount = state.sessions.size.toString(),
                        eggsCount = totalEggs.toString(),
                        samplesCount = totalSamples.toString(),
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
                item {
                    Spacer(Modifier.height(Spacing.md))
                    SpeciesFilterChips(
                        selected = state.selectedSpecies,
                        onSelect = viewModel::onSpeciesSelected,
                    )
                }
                item { Spacer(Modifier.height(Spacing.xs)) }

                when {
                    state.isLoading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AppColors.Blue)
                        }
                    }
                    filteredRecords.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .padding(horizontal = Spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.records_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.Gray500,
                            )
                        }
                    }
                    else -> items(filteredRecords, key = { it.session.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onSessionClick(record.session.id) },
                            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsAppBar(subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.White)
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Records",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.Gray900,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Gray500,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search sessions, notes, species...",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Gray400,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = AppColors.Gray400,
                modifier = Modifier.size(18.dp),
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = AppColors.Gray50,
            focusedContainerColor = AppColors.White,
            unfocusedBorderColor = AppColors.Gray200,
            focusedBorderColor = AppColors.Blue,
            cursorColor = AppColors.Blue,
            unfocusedTextColor = AppColors.Gray900,
            focusedTextColor = AppColors.Gray900,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StatsRow(
    sessionsCount: String,
    eggsCount: String,
    samplesCount: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatTile("Sessions", sessionsCount, Modifier.weight(1f))
        StatTile("Eggs found", eggsCount, Modifier.weight(1f))
        StatTile("Samples", samplesCount, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AppColors.Gray50, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Gray500,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Gray900,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum, cv11, ss01, ss03"),
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SpeciesFilterChips(
    selected: EggSpecies?,
    onSelect: (EggSpecies?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SpeciesChip("All species", selected == null) { onSelect(null) } }
        items(EggSpecies.entries.filterNot { it == EggSpecies.OTHER }) { species ->
            SpeciesChip(species.displayName, selected == species) { onSelect(species) }
        }
    }
}

@Composable
private fun SpeciesChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) AppColors.Gray900 else AppColors.White
    val border = if (selected) AppColors.Gray900 else AppColors.Gray200
    val text = if (selected) AppColors.White else AppColors.Gray700

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = text,
            fontStyle = if (label == EggSpecies.HOOKWORM.displayName) FontStyle.Normal else FontStyle.Italic,
        )
    }
}

@Composable
private fun RecordCard(
    record: SessionRecordItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val speciesCount = if (record.speciesLabels.isEmpty()) "-" else record.speciesLabels.size.toString()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.session.label ?: "Session ${record.session.id.take(4)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.Gray900,
                )
                Text(
                    metaText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Gray500,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            StatusPill(SyncStatus.Synced)
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = AppColors.Gray100, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

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
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = AppColors.Gray500,
        )
    }
}

@Composable
private fun StatusPill(status: SyncStatus) {
    val (bg, fg, text) = when (status) {
        SyncStatus.Synced -> Triple(AppColors.GreenTint, AppColors.GreenText, "Synced")
        SyncStatus.PendingSync -> Triple(AppColors.AmberTint, AppColors.AmberText, "Pending sync")
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}
