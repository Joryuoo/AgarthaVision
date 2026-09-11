@file:Suppress("FunctionNaming")

package com.agarthavision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

/**
 * One selectable row in a [SearchableDropdown].
 *
 * Flattening the caller's domain type to this shape keeps the component non-generic and
 * keeps its parameter list short, matching how [com.agarthavision.ui.sessions] already
 * bundles a composable's arguments.
 */
data class SearchableOption(
    val key: String,
    val title: String,
    val subtitle: String,
)

/**
 * What [SearchableDropdown] is currently showing: the committed choice, the text being typed,
 * and the matches for it.
 *
 * Bundled for the same reason [SearchableDropdownConfig] and [SearchableDropdownActions] are —
 * the six loose parameters this replaces tripped detekt's `LongParameterList`.
 */
data class SearchableDropdownState(
    val selected: SearchableOption?,
    val query: String,
    val options: List<SearchableOption>,
)

/** Static, already-resolved text for [SearchableDropdown]. Strings come from `strings.xml`. */
data class SearchableDropdownConfig(
    val label: String,
    val placeholder: String,
    /** Shown while the query is still shorter than [minQueryLength]. */
    val hint: String,
    /** Shown once the query is long enough but nothing matched. */
    val noMatches: String,
    val clearLabel: String,
    val minQueryLength: Int,
    /** Small chip beside the label, e.g. "REQUIRED". Omitted when null. */
    val badge: String? = null,
    val isError: Boolean = false,
)

/** Callbacks [SearchableDropdown] dispatches back to the caller. */
data class SearchableDropdownActions(
    val onQueryChange: (String) -> Unit,
    val onSelect: (SearchableOption) -> Unit,
    val onClear: () -> Unit,
)

/**
 * A type-to-filter selector for lists far too long to scroll — the PSGC barangay dataset is
 * 42,001 entries.
 *
 * Results render in a height-bounded [LazyColumn], so only the visible rows compose. This is
 * why the component does not use `ExposedDropdownMenu`: that lays its children out in a
 * plain `Column`, which composes every row eagerly. It also renders the results inline
 * rather than in a popup, because a popup menu inside a `ModalBottomSheet` competes with
 * the sheet for the IME.
 *
 * Once something is selected the search field is replaced by the selection, so the field
 * never shows text that could be read as either a query or an answer.
 */
@Composable
fun SearchableDropdown(
    state: SearchableDropdownState,
    config: SearchableDropdownConfig,
    actions: SearchableDropdownActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldLabel(label = config.label, badge = config.badge)
        val selected = state.selected
        if (selected != null) {
            SelectionRow(selected = selected, clearLabel = config.clearLabel, onClear = actions.onClear)
        } else {
            SearchField(query = state.query, config = config, onQueryChange = actions.onQueryChange)
            if (state.query.isNotBlank()) {
                ResultsPanel(
                    query = state.query,
                    options = state.options,
                    config = config,
                    onSelect = actions.onSelect,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(label: String, badge: String?) {
    val colors = AgarthaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                color = colors.dangerText,
                modifier = Modifier
                    .background(colors.dangerTint, RoundedCornerShape(Spacing.xs))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    config: SearchableDropdownConfig,
    onQueryChange: (String) -> Unit,
) {
    val colors = AgarthaTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        config.isError -> colors.danger
        isFocused -> colors.accent
        else -> colors.borderStrong
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        textStyle = TextStyle(fontSize = 15.sp, color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (config.isError) colors.dangerTint.copy(alpha = 0.5f) else colors.surface,
                        RoundedCornerShape(Spacing.md),
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(Spacing.md))
                    .padding(horizontal = Spacing.lg, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SvgIcon(
                    "M21 21l-4.35-4.35M11 18a7 7 0 100-14 7 7 0 000 14z",
                    color = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(config.placeholder, fontSize = 15.sp, color = colors.textTertiary)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun SelectionRow(
    selected: SearchableOption,
    clearLabel: String,
    onClear: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accentTint2, RoundedCornerShape(Spacing.md))
            .border(1.dp, colors.accentTint, RoundedCornerShape(Spacing.md))
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OptionText(option = selected, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(colors.surfaceMuted, CircleShape)
                .clickable(onClick = onClear)
                .semantics { contentDescription = clearLabel },
            contentAlignment = Alignment.Center,
        ) {
            SvgIcon(
                "M18 6L6 18M6 6l12 12",
                color = colors.textSecondary,
                strokeWidth = 2f,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ResultsPanel(
    query: String,
    options: List<SearchableOption>,
    config: SearchableDropdownConfig,
    onSelect: (SearchableOption) -> Unit,
) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(Spacing.md))
            .border(1.dp, colors.border, RoundedCornerShape(Spacing.md)),
    ) {
        if (options.isEmpty()) {
            Text(
                text = if (query.trim().length < config.minQueryLength) config.hint else config.noMatches,
                fontSize = 13.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                items(options, key = { it.key }) { option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        OptionText(option = option)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionText(option: SearchableOption, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Column(modifier = modifier) {
        Text(
            text = option.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            letterSpacing = (-0.01).em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = option.subtitle,
            fontSize = 12.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Roughly five rows — enough to choose from without the sheet outgrowing a small screen. */
private val RESULTS_MAX_HEIGHT = 232.dp
