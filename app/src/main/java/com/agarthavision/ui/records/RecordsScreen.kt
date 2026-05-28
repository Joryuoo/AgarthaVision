@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onBackClick: () -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.records_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.records_back),
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    state: RecordsState,
    onSpeciesSelected: (EggSpecies?) -> Unit,
    onDateRangeSelected: (LocalDate?, LocalDate?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFiltersSheet by remember { mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                com.komoui.components.Input(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search records...",
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.styles.mutedForeground
                        )
                    }
                )
            }
            
            androidx.compose.material3.IconButton(
                onClick = { showFiltersSheet = true },
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = MaterialTheme.styles.foreground
                )
            }
        }
    }

    if (showFiltersSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.styles.background,
            contentColor = MaterialTheme.styles.foreground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.styles.foreground
                )

                // Species Filter
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Species",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.styles.foreground
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            KomoButton(
                                onClick = { onSpeciesSelected(null) },
                                variant = if (state.selectedSpecies == null) ButtonVariant.Default else ButtonVariant.Secondary,
                                size = ButtonSize.Sm,
                            ) {
                                Text(stringResource(R.string.records_species_all))
                            }
                        }
                        items(EggSpecies.entries.toTypedArray()) { species ->
                            val label = species.displayName
                            KomoButton(
                                onClick = { onSpeciesSelected(species) },
                                variant = if (state.selectedSpecies == species) ButtonVariant.Default else ButtonVariant.Secondary,
                                size = ButtonSize.Sm,
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                // Date Range Filter
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Date Range",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.styles.foreground
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KomoButton(
                            onClick = { showDatePicker = true },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Sm,
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp).size(16.dp)
                            )
                            val dateText = if (state.startDate != null && state.endDate != null) {
                                "${state.startDate} → ${state.endDate}"
                            } else if (state.startDate != null) {
                                "${state.startDate} → Present"
                            } else if (state.endDate != null) {
                                "Any → ${state.endDate}"
                            } else {
                                "All time"
                            }
                            Text(dateText)
                        }
                        
                        if (state.startDate != null || state.endDate != null) {
                            KomoButton(
                                onClick = { onDateRangeSelected(null, null) },
                                variant = ButtonVariant.Ghost,
                                size = ButtonSize.Sm,
                            ) {
                                Text(stringResource(R.string.records_filter_clear))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = state.endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                KomoButton(
                    onClick = {
                        showDatePicker = false
                        val start = dateRangePickerState.selectedStartDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        val end = dateRangePickerState.selectedEndDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        onDateRangeSelected(start, end)
                    },
                    size = ButtonSize.Sm
                ) {
                    Text(stringResource(R.string.records_filter_apply))
                }
            },
            dismissButton = {
                KomoButton(
                    onClick = { showDatePicker = false },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Sm
                ) {
                    Text(stringResource(R.string.verify_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Select Date Range",
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                    )
                },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
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
                        text = item.session.label?.takeIf { it.isNotBlank() }
                            ?: stringResource(
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
