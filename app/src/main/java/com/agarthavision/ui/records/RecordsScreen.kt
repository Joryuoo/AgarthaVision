package com.agarthavision.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.records.RecordItem
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.themes.styles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        topBar = {
            TopAppBar(
                title = { Text("Records") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            FilterBar(
                selectedSpecies = state.selectedSpecies,
                onSpeciesSelected = viewModel::onSpeciesSelected
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.records) { item ->
                        RecordCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterBar(
    selectedSpecies: EggSpecies?,
    onSpeciesSelected: (EggSpecies?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EggSpecies.entries.forEach { species ->
            val isSelected = selectedSpecies == species
            FilterChip(
                selected = isSelected,
                onClick = { onSpeciesSelected(if (isSelected) null else species) },
                label = { Text(species.canonicalClass ?: "Other") }
            )
        }
    }
}

@Composable
fun RecordCard(item: RecordItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.card,
            contentColor = MaterialTheme.styles.cardForeground
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.primaryDetection?.expertClass ?: item.primaryDetection?.classLabel ?: "No detection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.styles.foreground
                    )
                    Text(
                        text = "Confidence: ${"%.2f".format(item.primaryDetection?.confidence ?: 0f)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.styles.mutedForeground
                    )
                }
                
                SyncStatusBadge(status = item.sample.status)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val dateTime = Instant.ofEpochMilli(item.sample.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            
            Text(
                text = "Captured at: $dateTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground
            )
        }
    }
}

@Composable
fun SyncStatusBadge(status: SampleStatus) {
    val variant = when (status) {
        SampleStatus.SYNCED -> BadgeVariant.Default
        SampleStatus.SYNC_FAILED -> BadgeVariant.Destructive
        else -> BadgeVariant.Secondary
    }

    KomoBadge(
        variant = variant
    ) {
        Text(status.value.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}
