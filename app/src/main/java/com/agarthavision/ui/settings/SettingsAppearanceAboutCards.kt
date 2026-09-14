package com.agarthavision.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.BuildConfig
import com.agarthavision.R
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.DarkMode
import com.agarthavision.ui.icons.LightMode
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

@Composable
internal fun AppearanceCard(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val colors = AgarthaTheme.colors
    val toggleLabel = stringResource(
        if (isDarkMode) R.string.theme_toggle_to_light else R.string.theme_toggle_to_dark,
    )
    // The whole card is the switch, not just the glyph at its edge: a row-sized target is
    // what a thumb expects from a settings row, and one accessibility node describes it.
    SettingsCard(onClick = onToggleTheme) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    contentDescription = toggleLabel
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_dark_mode),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            // Same sun/moon glyph the dashboard header uses, so the two places you can
            // flip the theme look alike.
            Icon(
                imageVector = if (isDarkMode) AgarthaIcons.LightMode else AgarthaIcons.DarkMode,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun AboutCard() {
    val environmentRes = if (BuildConfig.DEBUG) {
        R.string.settings_about_environment_dev
    } else {
        R.string.settings_about_environment_prod
    }
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AboutLabel(stringResource(R.string.settings_about_version))
            AboutValue(BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AboutLabel(stringResource(R.string.settings_about_environment))
            AboutValue(stringResource(environmentRes))
        }
    }
}

@Composable
private fun AboutLabel(text: String) {
    Text(text = text, color = AgarthaTheme.colors.textSecondary, fontSize = 13.sp)
}

@Composable
private fun AboutValue(text: String) {
    Text(
        text = text,
        color = AgarthaTheme.colors.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
