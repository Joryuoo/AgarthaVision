package com.agarthavision.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.components.SheetInput
import com.agarthavision.ui.components.SheetInputConfig
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Bottom sheet for renaming an existing session.
 * Prefills with the current session label and surfaces duplicate-label validation errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenameSessionSheet(
    initialLabel: String,
    state: SessionsState,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (label: String) -> Unit,
) {
    val colors = AgarthaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp)),
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        var label by remember(initialLabel) { mutableStateOf(initialLabel) }
        var showEmptyError by remember { mutableStateOf(false) }

        val activeErrorMessage = when {
            showEmptyError && label.isBlank() -> stringResource(R.string.session_new_label_required)
            !state.errorMessage.isNullOrBlank() -> state.errorMessage
            else -> null
        }
        val isError = activeErrorMessage != null

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        stringResource(R.string.session_rename_sheet_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.015).em,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.session_rename_sheet_subtitle),
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.surfaceMuted, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.session_new_sheet_close),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Body
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                SheetInput(
                    value = label,
                    onValueChange = {
                        label = it
                        showEmptyError = false
                        if (state.errorMessage != null) onClearError()
                    },
                    config = SheetInputConfig(
                        label = stringResource(R.string.session_label_field_label),
                        placeholder = stringResource(R.string.session_label_placeholder),
                        isError = isError,
                        maxLength = SESSION_LABEL_MAX_LENGTH,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (activeErrorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.dangerTint, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.danger.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = activeErrorMessage,
                            fontSize = 12.sp,
                            color = colors.dangerText,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceMuted,
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Text(
                        stringResource(R.string.session_picker_dialog_cancel),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = {
                        if (label.isBlank()) {
                            showEmptyError = true
                        } else {
                            onSubmit(label)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandFill,
                        contentColor = colors.onBrandFill,
                    ),
                ) {
                    Text(
                        stringResource(R.string.session_rename_save),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
