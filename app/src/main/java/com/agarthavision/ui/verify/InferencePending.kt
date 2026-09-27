package com.agarthavision.ui.verify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * The Model Output section while the frame waits for its model output: a spinner, what it is
 * waiting on, and, for a queued frame, the way out of waiting.
 */
@Composable
internal fun InferencePending(onCancelInference: (() -> Unit)?) {
    val colors = AgarthaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .testTag(VerifyTestTags.MODEL_OUTPUT_SPINNER)
                    .size(20.dp),
            )
            if (onCancelInference != null) {
                Text(
                    text = stringResource(R.string.verify_model_in_inference),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(VerifyTestTags.MODEL_OUTPUT_PENDING),
                )
            }
        }
        if (onCancelInference != null) {
            Text(
                text = stringResource(R.string.verify_annotation_locked),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = stringResource(R.string.verify_cancel_inference),
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .testTag(VerifyTestTags.CANCEL_INFERENCE)
                    .clickable(onClick = onCancelInference)
                    .padding(vertical = 6.dp),
            )
        }
    }
}
