package com.agarthavision.ui.patients

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.core.util.DateBucket
import com.agarthavision.core.util.classifyDateBucket
import com.agarthavision.core.util.sevenDaysAgoMillis
import com.agarthavision.core.util.startOfTodayMillis
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.patients.PatientListItem
import com.agarthavision.domain.usecase.patients.PatientSort
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom clearance so the last row is not stranded under the floating New Patient button.
 */
private val FloatingActionClearance = 88.dp

/**
 * The patient list screen:
 * - Reduced padding/margins around the header, title, and search bar.
 * - Global name privacy toggle ("Names hidden").
 * - Search bar with filter action button.
 * - Sort dropdown supporting "Recent activity", "Today", "This week", "Earlier", "Last name", "First name".
 * - Smooth retraction of sort dropdown button on scroll down.
 * - Grouped sections: "TODAY", "THIS WEEK", "EARLIER".
 * - Patient rows with avatar, status badges, compact relative time, and menu.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
fun PatientsScreen(
    onPatientSelected: (String) -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AgarthaTheme.colors

    val listState = rememberLazyListState()
    var isSortVisible by rememberSaveable { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -8f) {
                    isSortVisible = false
                } else if (delta > 8f) {
                    isSortVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val isSortRetracted by remember {
        derivedStateOf {
            !isSortVisible && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }

    LaunchedEffect(state.sort, state.searchQuery) {
        isSortVisible = true
    }

    var showPatientSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activePatientId by remember { mutableStateOf<String?>(null) }
    var hideNames by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PatientsEvent.OpenPatient -> onPatientSelected(event.patientId)
                is PatientsEvent.EditPatient -> {
                    activePatientId = event.patientId
                    showPatientSheet = true
                }
                PatientsEvent.CreatePatient -> {
                    activePatientId = null
                    showPatientSheet = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.TopCenter),
        ) {
            // 1. Screen Header (tight top and bottom padding)
            PatientsScreenHeader(
                total = state.total,
                hideNames = hideNames,
                onToggleHideNames = { hideNames = !hideNames },
            )

            // 2. Search Bar + Filter Button (tight vertical padding)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.patients_search_placeholder),
                                    fontSize = 15.sp,
                                    color = colors.textTertiary,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onSearchQueryChanged("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceVariant)
                            .clickable { showFilterSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.patients_filters_title),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (state.activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(colors.brandFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.activeFilterCount.toString(),
                                color = colors.onBrandFill,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // 3. Sort Filter Dropdown (retracts when scrolling down)
            AnimatedVisibility(
                visible = !isSortRetracted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortDropdownChip(
                        selectedSort = state.sort,
                        onSortSelected = viewModel::onSortSelected,
                    )
                }
            }

            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.onLoadMore()
            }

            // Single source of "now" for this screen: the ViewModel-provided clock reading,
            // not a Composable-local System.currentTimeMillis() snapshot (that was a second,
            // independently-frozen clock feeding the same "time ago" / bucketing math).
            val nowMillis = remember(state.now) { state.now.toEpochMilli() }
            val todayStartMillis = remember(state.now) { startOfTodayMillis(state.now) }
            val sevenDaysAgoMillis = remember(state.now) { sevenDaysAgoMillis(state.now) }

            val groupedPatients = remember(state.patients, state.sort, todayStartMillis, sevenDaysAgoMillis) {
                fun bucketOf(item: PatientListItem): DateBucket {
                    val activity = item.lastActivityAt ?: item.patient.updatedAt.toEpochMilli()
                    return classifyDateBucket(activity, todayStartMillis, sevenDaysAgoMillis)
                }
                when (state.sort) {
                    PatientSort.RECENT -> {
                        val today = mutableListOf<PatientListItem>()
                        val thisWeek = mutableListOf<PatientListItem>()
                        for (item in state.patients) {
                            when (bucketOf(item)) {
                                DateBucket.TODAY -> today.add(item)
                                DateBucket.THIS_WEEK -> thisWeek.add(item)
                                DateBucket.EARLIER -> Unit
                            }
                        }
                        buildList {
                            if (today.isNotEmpty()) add("TODAY" to today)
                            if (thisWeek.isNotEmpty()) add("THIS WEEK" to thisWeek)
                        }
                    }
                    PatientSort.TODAY -> {
                        val today = state.patients.filter { bucketOf(it) == DateBucket.TODAY }
                        if (today.isNotEmpty()) listOf("TODAY" to today) else emptyList()
                    }
                    PatientSort.THIS_WEEK -> {
                        val thisWeek = state.patients.filter { bucketOf(it) == DateBucket.THIS_WEEK }
                        if (thisWeek.isNotEmpty()) listOf("THIS WEEK" to thisWeek) else emptyList()
                    }
                    PatientSort.EARLIER -> {
                        val earlier = state.patients.filter { bucketOf(it) == DateBucket.EARLIER }
                        if (earlier.isNotEmpty()) listOf("EARLIER" to earlier) else emptyList()
                    }
                    PatientSort.LAST_NAME, PatientSort.FIRST_NAME -> {
                        if (state.patients.isNotEmpty()) listOf("ALL PATIENTS" to state.patients) else emptyList()
                    }
                }
            }

            when {
                state.isLoading && state.patients.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    repeat(3) {
                        PatientCardSkeleton()
                    }
                }
                !state.isLoading && groupedPatients.isEmpty() -> {
                    val narrowed = state.isNarrowed
                    val hasOlderRecords = state.sort == PatientSort.RECENT && state.patients.isNotEmpty()

                    if (hasOlderRecords) {
                        EmptyState(
                            icon = Icons.Outlined.Inbox,
                            title = "No recent activity",
                            body = "No patient activity found for today or this week. " +
                                "Switch to Earlier to view older records.",
                            modifier = Modifier.padding(top = Spacing.xxl),
                            action = {
                                Button(
                                    onClick = { viewModel.onSortSelected(PatientSort.EARLIER) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.brandFill,
                                        contentColor = colors.onBrandFill,
                                    ),
                                ) {
                                    Text("View earlier records")
                                }
                            },
                        )
                    } else {
                        val emptyAction: (@Composable () -> Unit)? = if (narrowed) {
                            null
                        } else {
                            { NewPatientButton(onClick = viewModel::onCreatePatient) }
                        }
                        EmptyState(
                            icon = Icons.Outlined.Inbox,
                            title = stringResource(
                                if (narrowed) R.string.patients_empty_filtered_title
                                else R.string.patients_empty_title,
                            ),
                            body = stringResource(
                                if (narrowed) R.string.patients_empty_filtered_body
                                else R.string.patients_empty_body,
                            ),
                            modifier = Modifier.padding(top = Spacing.xxl),
                            action = emptyAction,
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = FloatingActionClearance),
                ) {
                    groupedPatients.forEach { (sectionTitle, items) ->
                        item(key = "section_$sectionTitle") {
                            Text(
                                text = sectionTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
                            )
                        }
                        itemsIndexed(items, key = { _, item -> item.patient.id }) { index, item ->
                            PatientRow(
                                item = item,
                                now = state.now,
                                nowMillis = nowMillis,
                                hideNames = hideNames,
                                onClick = { viewModel.onPatientSelected(item.patient.id) },
                                onEdit = { viewModel.onEditPatient(item.patient.id) },
                            )
                            if (index < items.lastIndex) {
                                HorizontalDivider(
                                    color = colors.border,
                                    thickness = 0.8.dp,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.patients.isNotEmpty()) {
            NewPatientButton(
                onClick = viewModel::onCreatePatient,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = Spacing.lg),
            )
        }
    }

    if (showFilterSheet) {
        PatientsFilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onApply = viewModel::onApplyFilters,
            onBarangayQueryChange = viewModel::onBarangayQueryChanged,
        )
    }

    if (showPatientSheet) {
        PatientFormSheet(
            patientId = activePatientId,
            onDismiss = {
                showPatientSheet = false
                activePatientId = null
            },
            onOpenPatient = { id ->
                showPatientSheet = false
                activePatientId = null
                onPatientSelected(id)
            },
        )
    }
}

@Composable
private fun PatientsScreenHeader(
    total: Int,
    hideNames: Boolean,
    onToggleHideNames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.patients_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (colors.isDark) Color.White else Color.Black,
            letterSpacing = (-0.5).sp,
            lineHeight = 30.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 1.dp),
        ) {
            Text(
                text = "$total patients",
                fontSize = 14.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = " · ",
                fontSize = 14.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleHideNames)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hideNames) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (hideNames) "Names hidden" else "Names visible",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SortDropdownChip(
    selectedSort: PatientSort,
    onSortSelected: (PatientSort) -> Unit,
) {
    val colors = AgarthaTheme.colors
    var expanded by remember { mutableStateOf(false) }

    val sortLabel = when (selectedSort) {
        PatientSort.RECENT -> stringResource(R.string.patients_sort_recent)
        PatientSort.TODAY -> stringResource(R.string.patients_sort_today)
        PatientSort.THIS_WEEK -> stringResource(R.string.patients_sort_this_week)
        PatientSort.EARLIER -> stringResource(R.string.patients_sort_earlier)
        PatientSort.LAST_NAME -> stringResource(R.string.patients_sort_lastname)
        PatientSort.FIRST_NAME -> stringResource(R.string.patients_sort_firstname)
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderStrong, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = sortLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_recent)) },
                onClick = {
                    onSortSelected(PatientSort.RECENT)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_today)) },
                onClick = {
                    onSortSelected(PatientSort.TODAY)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_this_week)) },
                onClick = {
                    onSortSelected(PatientSort.THIS_WEEK)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_earlier)) },
                onClick = {
                    onSortSelected(PatientSort.EARLIER)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_lastname)) },
                onClick = {
                    onSortSelected(PatientSort.LAST_NAME)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.patients_sort_firstname)) },
                onClick = {
                    onSortSelected(PatientSort.FIRST_NAME)
                    expanded = false
                },
            )
        }
    }
}

/**
 * One patient item rendered in accordance with the requested design.
 */
@Suppress("LongParameterList")
@Composable
private fun PatientRow(
    item: PatientListItem,
    now: Instant,
    nowMillis: Long,
    hideNames: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val patient = item.patient
    var isRevealed by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val sexLabel = when (patient.sex) {
        Sex.MALE -> stringResource(R.string.patients_sex_male)
        Sex.FEMALE -> stringResource(R.string.patients_sex_female)
        null -> stringResource(R.string.patients_sex_unknown)
    }
    val meta = stringResource(
        R.string.patients_row_meta,
        sexLabel,
        stringResource(R.string.patients_age_years, patient.ageYears(now)),
        item.barangayName ?: patient.psgcBarangayCode,
    )

    val timeText = formatCompactTimeAgo(
        epochMillis = item.lastActivityAt ?: patient.updatedAt.toEpochMilli(),
        nowMillis = nowMillis,
    )

    val displayName = if (isRevealed || !hideNames) patient.displayName else patient.maskedDisplayName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center column: Name + Time in top row, meta, badge
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    color = if (colors.isDark) Color.White else Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                )
            }
            Text(
                text = meta,
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.unverifiedCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.goldTint)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AssignmentLate,
                            contentDescription = null,
                            tint = colors.goldText,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${item.unverifiedCount} to review",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.goldText,
                        )
                    }
                }
            } else if (!item.positiveSpecies.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentTint)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Positive · ${formatSpeciesShortName(item.positiveSpecies)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onAccentTint,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3 dots menu button at far right, vertically centered in the row
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.patients_menu_desc),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (!patient.isCodename) {
                    val revealLabel = if (isRevealed) {
                        R.string.patients_mask_name_desc
                    } else {
                        R.string.patients_reveal_name_desc
                    }
                    val revealIcon = if (isRevealed) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(revealLabel)) },
                        leadingIcon = {
                            Icon(
                                imageVector = revealIcon,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isRevealed = !isRevealed
                            menuExpanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.patients_edit_action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
            }
        }
    }
}

/** Loading placeholder for [PatientRow]. */
@Composable
private fun PatientCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(modifier = Modifier.size(46.dp).clip(CircleShape))
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(modifier = Modifier.height(16.dp).width(140.dp))
                SkeletonBox(modifier = Modifier.height(13.dp).width(36.dp))
            }
            SkeletonBox(modifier = Modifier.height(13.dp).width(180.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        SkeletonBox(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NewPatientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(49.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandFill,
            contentColor = colors.onBrandFill,
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.onBrandFill,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.patients_new),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatSpeciesShortName(raw: String): String {
    val trimmed = raw.trim()
    val match = EggSpecies.fromClassLabel(trimmed)
    if (match != null && match != EggSpecies.OTHER) {
        return match.name.lowercase().replaceFirstChar { it.uppercase() }
    }
    return trimmed.split(' ', '_').firstOrNull()?.replaceFirstChar { it.uppercase() } ?: trimmed
}

@Suppress("MagicNumber")
private fun formatCompactTimeAgo(epochMillis: Long, nowMillis: Long): String {
    val diff = (nowMillis - epochMillis).coerceAtLeast(0L)
    val minutes = diff / (60 * 1000L)
    val hours = diff / (60 * 60 * 1000L)
    val days = diff / (24 * 60 * 60 * 1000L)
    return when {
        minutes < 1 -> "Just now"
        hours < 1 -> "${minutes} m"
        days < 1 -> "${hours} h"
        days < 7 -> "${days} d"
        else -> {
            val instant = Instant.ofEpochMilli(epochMillis)
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                .withZone(CLINICAL_ZONE)
                .format(instant)
        }
    }
}
