@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.components.Input
import com.komoui.themes.styles
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: ManualCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(frame) {
        viewModel.setFrame(frame)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ManualCaptureEvent.Dismiss -> onDismiss()
                is ManualCaptureEvent.ShowError -> { /* inline errors only */ }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = viewModel::onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.styles.card,
    ) {
        ManualSheetContent(
            state = state,
            actions = ManualSheetActions(
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onUserNoteChanged = viewModel::onUserNoteChanged,
                onToggleRepeat = viewModel::onToggleRepeat,
                onDeleteFrame = viewModel::onDeleteFrame,
                onSubmit = viewModel::onSubmit,
                onCancel = viewModel::onCancel,
            ),
            modifier = Modifier
                .heightIn(max = screenHeightDp * 0.95f)
                .navigationBarsPadding(),
        )
    }
}

private data class ManualSheetActions(
    val onSpeciesSelected: (EggSpecies) -> Unit,
    val onOtherSpeciesChanged: (String) -> Unit,
    val onUserNoteChanged: (String) -> Unit,
    val onToggleRepeat: () -> Unit,
    val onDeleteFrame: () -> Unit,
    val onSubmit: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
private fun ManualSheetContent(
    state: ManualCaptureUiState,
    actions: ManualSheetActions,
    modifier: Modifier = Modifier,
) {
    val frame = state.frame ?: return
    val timeLabel = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgarthaSpacing.screenEdge, vertical = AgarthaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manual_sheet_title, timeLabel),
                color = MaterialTheme.styles.foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isRepeat) {
                    KomoBadge(
                        variant = BadgeVariant.Outline,
                        modifier = Modifier.padding(end = AgarthaSpacing.xs),
                    ) {
                        Text(stringResource(R.string.verify_repeat_badge))
                    }
                }
                SheetKebab(
                    isRepeat = state.isRepeat,
                    onToggleRepeat = actions.onToggleRepeat,
                )
                /*IconButton(onClick = actions.onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.manual_sheet_close_desc),
                        tint = MaterialTheme.styles.foreground,
                    )
                }*/
            }
        }

        FrameWithBoxes(
            jpegBytes = frame.jpegBytes,
            predictions = emptyList(),
            highlightedIndex = 0,
            showBoxes = false,
            inferenceImageWidth = frame.imageWidth,
            inferenceImageHeight = frame.imageHeight,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )

        SpeciesDropdown(
            selected = state.selectedSpecies,
            otherText = state.otherSpeciesText,
            onSpeciesSelected = actions.onSpeciesSelected,
            onOtherTextChanged = actions.onOtherSpeciesChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs)) {
            Text(
                text = stringResource(R.string.verify_user_note_label),
                color = MaterialTheme.styles.foreground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Input(
                value = state.userNote,
                onValueChange = actions.onUserNoteChanged,
                placeholder = stringResource(R.string.verify_user_note_placeholder),
                singleLine = false,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.styles.destructive,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(AgarthaSpacing.xs))

        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgarthaSpacing.clusterGap),
        ) {
            KomoButton(
                onClick = { showDeleteConfirm = true },
                size = ButtonSize.Lg,
                variant = ButtonVariant.Destructive,
                enabled = !state.isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.verify_delete_frame))
            }
            /*KomoButton(
                onClick = actions.onCancel,
                size = ButtonSize.Lg,
                variant = ButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.verify_cancel))
            }*/
            KomoButton(
                onClick = actions.onSubmit,
                size = ButtonSize.Lg,
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.isSubmitting) {
                        stringResource(R.string.verify_submitting)
                    } else {
                        stringResource(R.string.verify_submit)
                    },
                )
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.verify_delete_confirm_title)) },
                text = { Text(stringResource(R.string.verify_delete_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            actions.onDeleteFrame()
                        },
                    ) {
                        Text(
                            stringResource(R.string.verify_delete_frame),
                            color = MaterialTheme.styles.destructive,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.verify_cancel))
                    }
                },
                containerColor = MaterialTheme.styles.card,
                titleContentColor = MaterialTheme.styles.foreground,
                textContentColor = MaterialTheme.styles.mutedForeground,
            )
        }
    }
}

@Composable
private fun SheetKebab(
    isRepeat: Boolean,
    onToggleRepeat: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.verify_sheet_kebab_desc),
                tint = MaterialTheme.styles.foreground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isRepeat) {
                            stringResource(R.string.verify_unmark_repeat)
                        } else {
                            stringResource(R.string.verify_mark_repeat)
                        },
                    )
                },
                onClick = {
                    expanded = false
                    onToggleRepeat()
                },
            )
        }
    }
}
