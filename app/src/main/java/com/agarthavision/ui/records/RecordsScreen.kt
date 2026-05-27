@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.records.SessionRecordItem
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.themes.styles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Session-first records browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onSessionClick: (String) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.records_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            FilterBar(
                state = state,
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onDateRangeSelected = viewModel::onDateRangeSelected,
            )

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.sessions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.records_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.styles.mutedForeground,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.sessions, key = { it.session.id }) { item ->
                        SessionCard(item = item, onClick = { onSessionClick(item.session.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    state: RecordsState,
    onSpeciesSelected: (EggSpecies?) -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
) {
    var startText by remember(state.startDate) { mutableStateOf(state.startDate?.toString().orEmpty()) }
    var endText by remember(state.endDate) { mutableStateOf(state.endDate?.toString().orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedSpecies == null,
                onClick = { onSpeciesSelected(null) },
                label = { Text(stringResource(R.string.records_species_all)) },
            )
            EggSpecies.entries.forEach { species ->
                val label = species.canonicalClass ?: stringResource(R.string.records_species_other)
                FilterChip(
                    selected = state.selectedSpecies == species,
                    onClick = { onSpeciesSelected(species) },
                    label = { Text(label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text(stringResource(R.string.records_filter_start)) },
                placeholder = { Text(stringResource(R.string.records_filter_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text(stringResource(R.string.records_filter_end)) },
                placeholder = { Text(stringResource(R.string.records_filter_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KomoButton(
                onClick = {
                    onDateRangeSelected(startText.toLocalDateOrNull(), endText.toLocalDateOrNull())
                },
                size = ButtonSize.Default,
            ) {
                Text(stringResource(R.string.records_filter_apply))
            }
            KomoButton(
                onClick = {
                    startText = ""
                    endText = ""
                    onDateRangeSelected(null, null)
                },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Default,
            ) {
                Text(stringResource(R.string.records_filter_clear))
            }
        }
    }
}

@Composable
private fun SessionCard(item: SessionRecordItem, onClick: () -> Unit) {
    val speciesText = item.speciesLabels.joinToString(", ").ifBlank {
        stringResource(R.string.records_session_no_species)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.secondary,
            contentColor = MaterialTheme.styles.secondaryForeground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.records_session_title,
                            item.session.startedAt.formatRecordDate(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.styles.foreground,
                    )
                    Text(
                        text = stringResource(
                            R.string.records_session_summary,
                            item.sampleCount,
                            speciesText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.styles.mutedForeground,
                    )
                }
                KomoBadge(variant = BadgeVariant.Secondary) {
                    Text(stringResource(R.string.records_session_open))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(
                    R.string.records_session_time,
                    item.session.startedAt.formatRecordDateTime(),
                    item.session.endedAt?.formatRecordDateTime() ?: stringResource(R.string.records_session_active),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )
            Text(
                text = if (item.latitude != null && item.longitude != null) {
                    stringResource(R.string.records_gps, item.latitude, item.longitude)
                } else {
                    stringResource(R.string.records_no_gps)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.styles.mutedForeground,
            )
        }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun Long.formatRecordDate(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun Long.formatRecordDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
