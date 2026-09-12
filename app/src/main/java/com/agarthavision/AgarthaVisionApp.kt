package com.agarthavision

import android.app.Application
import com.agarthavision.data.local.psgc.PsgcSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AgarthaVisionApp : Application() {
    @Inject
    lateinit var psgcSeeder: PsgcSeeder

    /**
     * Scope for work that outlives any screen. Seeding the PSGC reference data belongs
     * here rather than in a ViewModel: it is a data-layer concern, and routing it through
     * one would breach C1 for no benefit — nothing on screen waits for it.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget: capture and verification do not depend on the barangay picker,
        // so a failed or slow seed must never delay launch. PsgcSeeder is re-entrant and
        // no-ops once the device holds the current vintage.
        applicationScope.launch { psgcSeeder.seedIfNeeded() }
    }
}
