@file:Suppress("FunctionNaming")

package com.agarthavision.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.InfectivityLevel
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Pill badge for the [InfectivityLevel.LOW] / [InfectivityLevel.MODERATE] tiers — a quiet,
 * non-alarming indicator. [InfectivityLevel.EXTREME] uses [ExtremeParasitismAlert] instead.
 *
 * All copy comes from string resources — zero-diagnostic-terminology wording lives there, not
 * here — and there is no hardcoded color; only [AgarthaTheme] palette tokens are used.
 */
@Composable
fun InfectivityTierBadge(
    level: InfectivityLevel,
    speciesLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val (dot, tint, text) = when (level) {
        InfectivityLevel.MODERATE -> Triple(colors.warning, colors.warningTint, colors.warningText)
        // LOW and any unexpected value both fall back to the calm/success styling — this
        // composable is never invoked for EXTREME (that renders ExtremeParasitismAlert).
        else -> Triple(colors.success, colors.successTint, colors.successText)
    }
    val titleRes = if (level == InfectivityLevel.MODERATE) {
        R.string.session_detail_infectivity_moderate
    } else {
        R.string.session_detail_infectivity_low
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
            text = if (speciesLabel != null) {
                "${stringResource(titleRes)} · $speciesLabel"
            } else {
                stringResource(titleRes)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = text,
        )
    }
}

/**
 * Full-width alert banner for the [InfectivityLevel.EXTREME] tier — an analytical
 * physician-consult prompt, never diagnostic language. Reuses [R.drawable.ic_warning] (already
 * used on the dashboard).
 */
@Composable
fun ExtremeParasitismAlert(
    speciesLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.dangerTint, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.session_detail_infectivity_extreme_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.dangerText,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = stringResource(R.string.session_detail_infectivity_extreme_body, speciesLabel),
                fontSize = 12.sp,
                color = colors.dangerText,
            )
        }
    }
}
