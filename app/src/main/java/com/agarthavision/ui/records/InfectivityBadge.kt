@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.InfectivityLevel
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Compact pill badge for the session's infectivity tier, sized to sit in the hero card's
 * top-right corner. Covers all three tiers — Low (calm/success), Moderate (warning), and
 * Extreme (danger) — as a self-contained light chip so it stays legible on the maroon card.
 *
 * Copy comes from string resources — zero-diagnostic-terminology wording lives there, not
 * here — and there is no hardcoded color; only [AgarthaTheme] palette tokens are used. The
 * Extreme physician-consult message is shown separately near the card's disclaimer.
 */
@Composable
fun InfectivityTierBadge(
    level: InfectivityLevel,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val (dot, tint, text) = when (level) {
        InfectivityLevel.MODERATE -> Triple(colors.warning, colors.warningTint, colors.warningText)
        InfectivityLevel.EXTREME -> Triple(colors.danger, colors.dangerTint, colors.dangerText)
        InfectivityLevel.LOW -> Triple(colors.success, colors.successTint, colors.successText)
    }
    val titleRes = when (level) {
        InfectivityLevel.MODERATE -> R.string.session_detail_infectivity_moderate
        InfectivityLevel.EXTREME -> R.string.session_detail_infectivity_extreme
        InfectivityLevel.LOW -> R.string.session_detail_infectivity_low
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(tint, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(titleRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = text,
        )
    }
}
