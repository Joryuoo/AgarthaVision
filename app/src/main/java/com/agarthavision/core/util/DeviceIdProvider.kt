package com.agarthavision.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes a stable per-install device identifier.
 *
 * Uses [Settings.Secure.ANDROID_ID], which is stable across reboots and resets
 * only when the user wipes the device or reinstalls the app on a factory-reset
 * device. See docs/03_MOBILE_APP_PLAN.md §1.10.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("HardwareIds")
    val id: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
}
