@file:Suppress("FunctionNaming")

package com.agarthavision.ui.verify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape

/**
 * Confirms a delete, and says plainly what kind of delete it is.
 *
 * Mandatory, not a nicety: *"Delete MUST always have a confirmation dialog since it will not be
 * possible to retrieve it back."*
 *
 * It states the split rather than giving one count, because the two halves are irreversible in
 * genuinely different ways and a medtech deserves to know which they are about to do. An
 * unverified frame is erased. A verified sample is hidden everywhere they will ever look, while
 * its labelled detections stay in the training corpus — a distinction they cannot infer from the
 * word "delete", and one they might reasonably care about.
 *
 * A single-row delete routes through this same dialog with a selection of one, so there is
 * exactly one delete path in the app and exactly one place this warning can go stale.
 */
@Composable
internal fun DeleteSamplesConfirmDialog(
    verifiedCount: Int,
    unverifiedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = verifiedCount + unverifiedCount
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = AgarthaTheme.colors.surface,
        titleContentColor = AgarthaTheme.colors.textPrimary,
        textContentColor = AgarthaTheme.colors.textPrimary,
        title = {
            Text(pluralStringResource(R.plurals.delete_samples_title, total, total))
        },
        text = {
            Column {
                if (unverifiedCount > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.delete_samples_unverified,
                            unverifiedCount,
                            unverifiedCount,
                        ),
                    )
                }
                if (verifiedCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.delete_samples_verified,
                            verifiedCount,
                            verifiedCount,
                        ),
                        modifier = Modifier.padding(top = if (unverifiedCount > 0) 8.dp else 0.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_samples_confirm),
                    color = AgarthaTheme.colors.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.verify_cancel))
            }
        },
    )
}
