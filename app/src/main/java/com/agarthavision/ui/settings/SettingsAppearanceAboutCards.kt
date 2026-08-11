package com.agarthavision.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agarthavision.BuildConfig
import com.agarthavision.R
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

@Composable
internal fun AppearanceCard(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val colors = AgarthaTheme.colors
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_dark_mode),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Switch(
                checked = isDarkMode,
                onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    uncheckedTrackColor = colors.surfaceMuted,
                ),
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
