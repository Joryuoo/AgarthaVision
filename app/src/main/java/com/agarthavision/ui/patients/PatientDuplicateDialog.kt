@file:Suppress("FunctionNaming")

package com.agarthavision.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.components.AgarthaButtonVariant
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape

/**
 * Confirms that the medtech wants to navigate away from the form without saving.
 *
 * Shown whenever [PatientFormState.showDiscardConfirm] is true — triggered by tapping
 * Cancel or the system back button on a dirty form.
 */
@Composable
internal fun DiscardConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = AgarthaTheme.colors.surface,
        titleContentColor = AgarthaTheme.colors.textPrimary,
        textContentColor = AgarthaTheme.colors.textPrimary,
        title = { Text(stringResource(R.string.patient_form_discard_title)) },
        text = { Text(stringResource(R.string.patient_form_discard_body)) },
        confirmButton = {
            AgarthaButton(
                onClick = onConfirm,
                variant = AgarthaButtonVariant.Destructive,
            ) {
                Text(stringResource(R.string.patient_form_discard_confirm))
            }
        },
        dismissButton = {
            AgarthaButton(
                onClick = onDismiss,
                variant = AgarthaButtonVariant.Secondary,
            ) {
                Text(stringResource(R.string.patient_form_discard_keep))
            }
        },
    )
}

/**
 * Shown when the incoming patient is an exact identity match with one already registered in
 * the **same** barangay.  Uses stronger wording than the different-barangay dialog because
 * this is a true duplicate, not merely a probable one.
 *
 * Three options: go to the existing record, add the duplicate anyway (there may be a
 * legitimate clinical reason), or cancel and return to the form.
 */
@Composable
internal fun SameBarangayDuplicateDialog(
    duplicate: PatientDuplicate,
    onProceed: () -> Unit,
    onOpenExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = AgarthaTheme.colors.surface,
        titleContentColor = AgarthaTheme.colors.textPrimary,
        textContentColor = AgarthaTheme.colors.textPrimary,
        title = { Text(stringResource(R.string.patient_form_same_duplicate_title)) },
        text = {
            Text(
                stringResource(
                    R.string.patient_form_same_duplicate_body,
                    duplicate.patient.displayName,
                ),
            )
        },
        // All three actions in the confirmButton slot — AlertDialog only supports two slots
        // and a third action is intentionally offered here.
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.patient_form_same_duplicate_cancel),
                        color = AgarthaTheme.colors.textSecondary,
                    )
                }
                TextButton(onClick = onOpenExisting) {
                    Text(stringResource(R.string.patient_form_same_duplicate_open))
                }
                TextButton(onClick = onProceed) {
                    Text(
                        text = stringResource(R.string.patient_form_same_duplicate_proceed),
                        color = AgarthaTheme.colors.danger,
                    )
                }
            }
        },
    )
}

/**
 * Shown when the incoming patient is an exact identity match with one registered in a
 * **different** barangay.  Uses softer "possible duplicate" wording, because the same name,
 * sex and birthdate in a different barangay could be a coincidence, a transfer, or a
 * patient who gave a different address on a previous visit.
 *
 * Three options: add as a new patient, go to the existing record, or cancel.
 */
@Composable
internal fun DifferentBarangayDuplicateDialog(
    duplicate: PatientDuplicate,
    onProceed: () -> Unit,
    onOpenExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = AgarthaTheme.colors.surface,
        titleContentColor = AgarthaTheme.colors.textPrimary,
        textContentColor = AgarthaTheme.colors.textPrimary,
        title = { Text(stringResource(R.string.patient_form_duplicate_title)) },
        text = {
            Text(
                stringResource(
                    R.string.patient_form_duplicate_body,
                    duplicate.patient.displayName,
                    duplicate.barangayName,
                ),
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.patient_form_duplicate_cancel),
                        color = AgarthaTheme.colors.textSecondary,
                    )
                }
                TextButton(onClick = onOpenExisting) {
                    Text(stringResource(R.string.patient_form_duplicate_open))
                }
                TextButton(onClick = onProceed) {
                    Text(stringResource(R.string.patient_form_duplicate_proceed))
                }
            }
        },
    )
}
