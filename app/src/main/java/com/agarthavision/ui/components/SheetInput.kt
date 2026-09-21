package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.ui.sessions.limitInput
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * The labelled form field the New Session sheet and the New / Edit Patient form share.
 *
 * It lived in `SessionsScreen.kt` until the patient form needed it, which left a patients
 * screen importing a form control out of a sessions screen. Nothing in this package
 * duplicates it: `SearchInput` is a search box with a leading icon and no label, and
 * `SearchableDropdown` is bound to option selection rather than free text.
 *
 * `limitInput` stays in `ui/sessions/SessionInputLimits.kt`, where `LimitInputTest` pins its
 * mid-string edit behaviour and PB-07c's reuse list names it.
 */
/** Static config for [SheetInput], separate from its stateful (value, onValueChange) pair. */
internal data class SheetInputConfig(
    val label: String,
    val placeholder: String,
    val isError: Boolean,
    val isTextArea: Boolean = false,
    val isRequired: Boolean = true,
    val maxLength: Int = Int.MAX_VALUE,
    /**
     * Whether to show the character-count row beneath the field.
     *
     * Set to `false` for patient name fields: the length is enforced by the ViewModel's
     * input transform, and a counter showing "0 / 2147483647" would be meaningless noise.
     */
    val showCounter: Boolean = true,
)

@Composable
internal fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    config: SheetInputConfig
) {
    val colors = AgarthaTheme.colors
    val (label, placeholder, isError) = config
    val isTextArea = config.isTextArea
    val isRequired = config.isRequired
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            if (isRequired) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "REQUIRED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.em,
                    color = colors.dangerText,
                    modifier = Modifier
                        .background(colors.dangerTint, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "OPTIONAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.em,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .background(colors.surfaceMuted, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isError) colors.danger else if (isFocused) colors.accent else colors.borderStrong
        val bgColor = if (isError) colors.dangerTint.copy(alpha = 0.5f) else colors.surface

        BasicTextField(
            value = value,
            onValueChange = { onValueChange(limitInput(value, it, config.maxLength)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(fontSize = 15.sp, color = colors.textPrimary),
            singleLine = !isTextArea,
            minLines = if (isTextArea) 3 else 1,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTextArea) Modifier.heightIn(min = 88.dp) else Modifier)
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = if (isTextArea) Alignment.TopStart else Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = colors.textTertiary, lineHeight = 21.75.sp)
                    }
                    innerTextField()
                }
            }
        )
        if (config.showCounter) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = stringResource(R.string.session_new_char_counter, value.length, config.maxLength),
                    fontSize = 11.sp,
                    color = if (value.length >= config.maxLength) colors.danger else colors.textTertiary
                )
            }
        }
    }
}
