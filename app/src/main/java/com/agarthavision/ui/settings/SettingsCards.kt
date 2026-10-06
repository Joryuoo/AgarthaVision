package com.agarthavision.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.ui.components.AgarthaButton
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Science
import com.agarthavision.ui.icons.Sync
import com.agarthavision.ui.icons.Warning
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = title.uppercase(),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
internal fun AccountCard(
    identity: LocalIdentity?,
    isOffline: Boolean,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    SettingsCard(modifier = modifier) {
        if (identity != null) {
            Text(
                text = stringResource(R.string.settings_signed_in_as_label),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = identity.email,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isOnline = !isOffline
                val statusBg = if (isOnline) colors.successTint else colors.surfaceVariant
                val statusFg = if (isOnline) colors.successText else colors.textSecondary
                val statusText = if (isOnline) {
                    stringResource(R.string.settings_connectivity_online)
                } else {
                    stringResource(R.string.settings_connectivity_offline)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.Close,
                            contentDescription = null,
                            tint = statusFg,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = statusText,
                            color = statusFg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_role_medtech),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.settings_not_signed_in),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_not_signed_in_body),
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(Spacing.md))
            AgarthaButton(
                onClick = onSignInClick,
                enabled = !isOffline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sign_in))
            }
        }
    }
}

/** Snapshot [SyncCard] renders from — bundled to keep the composable's parameter list small. */
internal data class SyncCardState(
    val isSignedIn: Boolean,
    val isOffline: Boolean,
    val counts: PendingSyncCounts,
    val isSyncing: Boolean,
    val canSyncNow: Boolean,
    val initialFetchDone: Boolean = true,
    val isFetching: Boolean = false,
    val lastFetchIncomplete: Boolean = false,
    val lastSyncError: String? = null,
)

internal enum class SyncBadge {
    FAILED, PENDING, FETCHING, NOT_YET_SYNCED, INCOMPLETE, ALL_SYNCED, NOTHING_TO_SYNC
}

/**
 * Pure function that maps sign-in state + sync counts + fetch state to a [SyncBadge] variant.
 */
internal fun syncBadgeState(
    isSignedIn: Boolean,
    counts: PendingSyncCounts,
    initialFetchDone: Boolean = true,
    isFetching: Boolean = false,
    lastFetchIncomplete: Boolean = false,
): SyncBadge = when {
    isSignedIn && counts.failed > 0 -> SyncBadge.FAILED
    isSignedIn && counts.totalPending > 0 -> SyncBadge.PENDING
    isSignedIn && isFetching -> SyncBadge.FETCHING
    isSignedIn && !initialFetchDone -> SyncBadge.NOT_YET_SYNCED
    isSignedIn && lastFetchIncomplete -> SyncBadge.INCOMPLETE
    isSignedIn -> SyncBadge.ALL_SYNCED
    else -> SyncBadge.NOTHING_TO_SYNC
}

@Composable
internal fun SyncCard(
    state: SyncCardState,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier,
    lastSyncMillis: Long? = null,
) {
    val colors = AgarthaTheme.colors
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.goldTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = colors.goldText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                val totalUnsynced = state.counts.totalUnsynced
                val title = when {
                    !state.isSignedIn -> stringResource(R.string.settings_sync_nothing_to_sync)
                    totalUnsynced == 0 -> stringResource(R.string.settings_sync_all_synced)
                    totalUnsynced == 1 -> stringResource(R.string.settings_one_item_not_uploaded)
                    else -> stringResource(R.string.settings_items_not_uploaded, totalUnsynced)
                }
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = if (state.isSignedIn) {
                    formatLastSync(lastSyncMillis)
                } else {
                    stringResource(R.string.settings_not_signed_in_body)
                }
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        if (state.isSignedIn) {
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onSyncNowClick,
                enabled = state.canSyncNow,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandFill,
                    contentColor = colors.onBrandFill,
                    disabledContainerColor = colors.brandFill.copy(alpha = 0.5f),
                    disabledContentColor = colors.onBrandFill.copy(alpha = 0.6f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = AgarthaIcons.Sync,
                        contentDescription = null,
                        tint = colors.onBrandFill,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isSyncing) {
                            stringResource(R.string.settings_sync_syncing)
                        } else {
                            stringResource(R.string.settings_sync_now)
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBrandFill,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                color = colors.border,
            )

            EntitySyncRow(
                icon = Icons.Outlined.Group,
                label = stringResource(R.string.settings_sync_patients),
                pendingCount = state.counts.pendingPatients,
                failedCount = state.counts.failedPatients,
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

            EntitySyncRow(
                icon = AgarthaIcons.Science,
                label = stringResource(R.string.settings_sync_sessions),
                pendingCount = state.counts.pendingSessions,
                failedCount = state.counts.failedSessions,
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

            EntitySyncRow(
                icon = Icons.Outlined.Image,
                label = stringResource(R.string.settings_sync_samples),
                pendingCount = state.counts.pendingSamples,
                failedCount = state.counts.failedSamples,
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

            EntitySyncRow(
                icon = Icons.Outlined.Description,
                label = stringResource(R.string.settings_sync_reports),
                pendingCount = state.counts.pendingReports,
                failedCount = state.counts.failedReports,
            )

            if (state.counts.failed > 0 && !state.lastSyncError.isNullOrBlank()) {
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                Text(
                    text = state.lastSyncError,
                    color = colors.dangerText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

        }
    }
}

@Composable
private fun EntitySyncRow(
    icon: ImageVector,
    label: String,
    pendingCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        when {
            failedCount > 0 -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.dangerTint)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.dangerText,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_failed, failedCount),
                            color = colors.dangerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            pendingCount > 0 -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.goldTint)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.goldText,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(R.string.settings_status_pending, pendingCount),
                            color = colors.goldText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.successTint)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.successText,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(R.string.settings_status_synced),
                            color = colors.successText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens Change password (14zcqntjph9). Tappable offline too: the screen it opens says why it
 * needs a connection, which is clearer than a row that silently does nothing.
 */
@Composable
internal fun ChangePasswordRow(
    isOffline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    SettingsCard(modifier = modifier.testTag(SETTINGS_CHANGE_PASSWORD_TAG), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_change_password),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isOffline) {
                    Text(
                        text = stringResource(R.string.settings_change_password_offline),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Test tag on [ChangePasswordRow]. */
const val SETTINGS_CHANGE_PASSWORD_TAG = "settings_change_password"

@Composable
internal fun BiometricLockCard(
    isBiometricLockEnabled: Boolean,
    isBiometricAvailable: Boolean,
    onToggleBiometricLock: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isBiometricAvailable) return
    val colors = AgarthaTheme.colors
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.settings_biometric_lock_title),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_biometric_lock_subtitle),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            androidx.compose.material3.Switch(
                checked = isBiometricLockEnabled,
                onCheckedChange = onToggleBiometricLock,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = colors.onBrandFill,
                    checkedTrackColor = colors.brandFill,
                    checkedBorderColor = colors.brandFill,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surfaceVariant,
                    uncheckedBorderColor = colors.borderStrong,
                ),
                modifier = Modifier.testTag("biometricLockSwitch"),
            )
        }
    }
}

@Composable
internal fun SignOutSection(
    unsyncedCount: Int,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        SettingsCard(onClick = onSignOutClick) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.settings_sign_out),
                    color = colors.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = AgarthaIcons.Warning,
                contentDescription = null,
                tint = colors.goldText,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp),
            )
            val warningText = when {
                unsyncedCount > 1 -> stringResource(R.string.settings_sign_out_warning_items, unsyncedCount)
                unsyncedCount == 1 -> stringResource(R.string.settings_sign_out_warning_one)
                else -> stringResource(R.string.settings_sign_out_warning_none)
            }
            Text(
                text = warningText,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun formatLastSync(lastSyncMillis: Long?): String {
    if (lastSyncMillis == null) return stringResource(R.string.settings_not_synced_yet)
    val instant = Instant.ofEpochMilli(lastSyncMillis)
    val now = Instant.now()
    val zone = CLINICAL_ZONE
    val syncDate = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)

    return when {
        syncDate == today -> {
            stringResource(R.string.settings_last_synced_today, timeFormatter.format(instant))
        }
        syncDate == today.minusDays(1) -> {
            stringResource(R.string.settings_last_synced_yesterday, timeFormatter.format(instant))
        }
        else -> {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()).withZone(zone)
            stringResource(R.string.settings_last_synced_at, dateFormatter.format(instant))
        }
    }
}
