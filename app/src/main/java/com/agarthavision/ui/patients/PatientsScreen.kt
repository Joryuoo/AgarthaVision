package com.agarthavision.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.patients.PatientListItem
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.ScreenHeader
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import java.time.Instant

/**
 * Bottom clearance so the last row is not stranded under the floating New Patient button.
 *
 * A literal rather than a [Spacing] token: the compact scale this file uses tops out at 32dp
 * and this has to clear a ~48dp button plus its own 16dp inset. It is a layout clearance for
 * one specific control, not a spacing step.
 */
private val FloatingActionClearance = 64.dp

/**
 * The patient list: everyone the signed-in medtech is linked to.
 *
 * Every read behind this screen is local, so it renders and searches with the radio off.
 *
 * **There is no delete affordance, and none should be added.** Removing a patient is an
 * admin-side action.
 */
@Composable
fun PatientsScreen(
    onPatientSelected: (String) -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AgarthaTheme.colors

    var showPatientSheet by remember { mutableStateOf(false) }
    var activePatientId by remember { mutableStateOf<String?>(null) }

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
            ScreenHeader(
                title = stringResource(R.string.patients_title),
                purpose = stringResource(R.string.patients_subtitle_purpose),
                status = stringResource(R.string.patients_status_counts, state.total),
            )

            SearchInput(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = stringResource(R.string.patients_search_placeholder),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            PatientsFiltersSection(
                state = state,
                actions = PatientsFiltersActions(
                    onSortSelected = viewModel::onSortSelected,
                    onSexSelected = viewModel::onSexSelected,
                    onBarangayQueryChange = viewModel::onBarangayQueryChanged,
                    onBarangaySelected = viewModel::onBarangaySelected,
                    onBarangayCleared = viewModel::onBarangayCleared,
                    onMinAgeChanged = viewModel::onMinAgeChanged,
                    onMaxAgeChanged = viewModel::onMaxAgeChanged,
                    onClearFilters = viewModel::onClearFilters,
                ),
            )

            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.onLoadMore()
            }

            if (!state.isLoading && state.patients.isEmpty()) {
                val narrowed = state.isNarrowed
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
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = FloatingActionClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(state.patients, key = { it.patient.id }) { item ->
                        PatientRow(
                            item = item,
                            now = state.now,
                            onClick = { viewModel.onPatientSelected(item.patient.id) },
                            onEdit = { viewModel.onEditPatient(item.patient.id) },
                        )
                    }
                }
            }
        }

        if (state.patients.isNotEmpty()) {
            NewPatientButton(
                onClick = viewModel::onCreatePatient,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.lg),
            )
        }
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
private fun NewPatientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
    ) {
        Text(text = stringResource(R.string.patients_new), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * One patient.
 *
 * Age is computed from the birthdate against [now] rather than stored, which is the whole
 * reason the birthdate is the column. [now] is fixed for the screen so a list open across
 * midnight cannot show two ages for one birthday.
 */
@Composable
private fun PatientRow(
    item: PatientListItem,
    now: Instant,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val patient = item.patient

    val sexLabel = when (patient.sex) {
        Sex.MALE -> stringResource(R.string.patients_sex_male)
        Sex.FEMALE -> stringResource(R.string.patients_sex_female)
        null -> stringResource(R.string.patients_sex_unknown)
    }
    val meta = stringResource(
        R.string.patients_row_meta,
        sexLabel,
        stringResource(R.string.patients_age_years, patient.ageYears(now)),
        // A code that resolves to nothing renders as itself: a PSGC vintage change can
        // retire one, and a blank where a barangay should be reads as missing data.
        item.barangayName ?: patient.psgcBarangayCode,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = patient.displayName,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The row itself opens this patient's smears, which is the common action. Editing
        // their details is rarer and deliberate, so it gets its own target rather than
        // displacing the tap. `material-icons-extended` per C11: this is an in-screen
        // affordance, not house identity.
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.patients_edit_desc, patient.displayName),
                tint = colors.textSecondary,
            )
        }
    }
}

