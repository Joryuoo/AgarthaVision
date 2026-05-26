@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.components.Button
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.themes.styles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSheet(
    frame: FlaggedFrame,
    onDismiss: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(frame) {
        viewModel.setFrame(frame)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VerificationEvent.Dismiss -> onDismiss()
                is VerificationEvent.ShowError -> { /* error shown inline */ }
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
        VerificationSheetContent(
            state = state,
            actions = VerificationSheetActions(
                onQ1Selected = viewModel::onQ1Selected,
                onQ2Selected = viewModel::onQ2Selected,
                onSpeciesSelected = viewModel::onSpeciesSelected,
                onOtherSpeciesChanged = viewModel::onOtherSpeciesChanged,
                onQ4Selected = viewModel::onQ4Selected,
                onDetectionPrev = viewModel::onDetectionPrev,
                onDetectionNext = viewModel::onDetectionNext,
                onToggleBoundingBoxes = viewModel::onToggleBoundingBoxes,
                onSubmit = viewModel::onSubmit,
                onCancel = viewModel::onCancel,
            ),
            modifier = Modifier
                .heightIn(max = screenHeightDp * 0.95f)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun VerificationSheetContent(
    state: VerificationUiState,
    actions: VerificationSheetActions,
    modifier: Modifier = Modifier,
) {
    val frame = state.frame ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgarthaSpacing.screenEdge, vertical = AgarthaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.md),
    ) {
        // Header
        Text(
            text = stringResource(R.string.verify_title, state.frameIndexInQueue, state.queueSize),
            color = MaterialTheme.styles.foreground,
            style = MaterialTheme.typography.titleMedium,
        )

        // Frame image with boxes
        FrameWithBoxes(
            jpegBytes = frame.jpegBytes,
            predictions = frame.predictions,
            highlightedIndex = state.currentDetectionIndex,
            showBoxes = state.showBoundingBoxes,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )

        // Detection navigation header
        if (frame.predictions.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = actions.onDetectionPrev,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Ghost,
                    enabled = state.currentDetectionIndex > 0,
                ) {
                    Text(stringResource(R.string.verify_prev_detection))
                }
                Text(
                    text = stringResource(
                        R.string.verify_detection_header,
                        state.currentDetectionIndex + 1,
                        frame.predictions.size,
                    ),
                    color = MaterialTheme.styles.mutedForeground,
                    style = MaterialTheme.typography.labelMedium,
                )
                Button(
                    onClick = actions.onDetectionNext,
                    size = ButtonSize.Sm,
                    variant = ButtonVariant.Ghost,
                    enabled = state.currentDetectionIndex < frame.predictions.size - 1,
                ) {
                    Text(stringResource(R.string.verify_next_detection))
                }
            }
        }

        // Confidence label
        val prediction = frame.predictions.getOrNull(state.currentDetectionIndex)
        if (prediction != null) {
            Text(
                text = stringResource(
                    R.string.verify_predicted,
                    prediction.classLabel,
                    "%.0f%%".format(prediction.confidence * 100),
                ),
                color = MaterialTheme.styles.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Per-detection questions
        val currentAnswers = state.answers.getOrNull(state.currentDetectionIndex)
        if (currentAnswers != null) {
            YesNoQuestion(
                label = stringResource(R.string.verify_q1),
                selected = currentAnswers.isEgg,
                onSelected = actions.onQ1Selected,
            )

            if (currentAnswers.isEgg == true) {
                YesNoQuestion(
                    label = stringResource(R.string.verify_q2),
                    selected = currentAnswers.isBoxCorrect,
                    onSelected = actions.onQ2Selected,
                )
            }

            if (currentAnswers.isEgg == true && currentAnswers.isBoxCorrect == true) {
                SpeciesDropdown(
                    selected = currentAnswers.species,
                    otherText = currentAnswers.otherSpeciesText,
                    onSpeciesSelected = actions.onSpeciesSelected,
                    onOtherTextChanged = actions.onOtherSpeciesChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Q4 — frame-level
        YesNoQuestion(
            label = stringResource(R.string.verify_q4),
            selected = state.missedEgg,
            onSelected = actions.onQ4Selected,
        )

        state.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.styles.destructive,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(AgarthaSpacing.xs))

        // Submit + Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgarthaSpacing.clusterGap),
        ) {
            Button(
                onClick = actions.onCancel,
                size = ButtonSize.Lg,
                variant = ButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.verify_cancel))
            }
            Button(
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
    }
}

@Composable
private fun YesNoQuestion(
    label: String,
    selected: Boolean?,
    onSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.xs)) {
        Text(
            text = label,
            color = MaterialTheme.styles.foreground,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AgarthaSpacing.clusterGap)) {
            Button(
                onClick = { onSelected(true) },
                size = ButtonSize.Sm,
                variant = if (selected == true) ButtonVariant.Default else ButtonVariant.Ghost,
            ) {
                Text(stringResource(R.string.verify_yes))
            }
            Button(
                onClick = { onSelected(false) },
                size = ButtonSize.Sm,
                variant = if (selected == false) ButtonVariant.Default else ButtonVariant.Ghost,
            ) {
                Text(stringResource(R.string.verify_no))
            }
        }
    }
}
