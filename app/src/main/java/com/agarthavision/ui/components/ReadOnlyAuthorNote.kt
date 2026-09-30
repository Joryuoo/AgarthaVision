package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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

/** Test tag on [ReadOnlyAuthorNote]. */
const val READ_ONLY_AUTHOR_NOTE_TAG = "read_only_author_note"

/**
 * Says a record belongs to a colleague and why it cannot be changed (14zcqntjph6).
 *
 * Shown where the editing actions would have been, so a medtech who finds them missing reads
 * the reason in the same place rather than hunting for a button that is not there.
 */
@Composable
fun ReadOnlyAuthorNote(authorName: String?, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
            .testTag(READ_ONLY_AUTHOR_NOTE_TAG)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = recordedByText(authorName),
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.record_read_only_body),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

/** "Recorded by Maria Santos", or "Recorded by another medtech" when no name is known. */
@Composable
fun recordedByText(authorName: String?): String =
    if (authorName.isNullOrBlank()) {
        stringResource(R.string.record_recorded_by_unnamed)
    } else {
        stringResource(R.string.record_recorded_by, authorName)
    }
