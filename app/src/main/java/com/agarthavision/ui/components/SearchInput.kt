package com.agarthavision.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Shared search field used on the Records and Sessions screens.
 * Styled with the AgarthaTheme surface/border colours; callers supply the
 * placeholder text so the field can contextualise the search hint.
 */
@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search sessions, notes, species...",
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = AgarthaTheme.colors.textTertiary,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = AgarthaTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = AgarthaTheme.colors.surfaceVariant,
            focusedContainerColor = AgarthaTheme.colors.surface,
            unfocusedBorderColor = AgarthaTheme.colors.borderStrong,
            focusedBorderColor = AgarthaTheme.colors.accent,
            cursorColor = AgarthaTheme.colors.accent,
            unfocusedTextColor = AgarthaTheme.colors.textPrimary,
            focusedTextColor = AgarthaTheme.colors.textPrimary,
        ),
        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
    )
}
