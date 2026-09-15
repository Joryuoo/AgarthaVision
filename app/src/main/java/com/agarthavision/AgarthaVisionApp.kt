package com.agarthavision

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.agarthavision.data.local.psgc.PsgcSeeder
import com.agarthavision.domain.repository.SampleImageRepository
import com.agarthavision.ui.image.SampleImageFetcher
import com.agarthavision.ui.image.SampleImageKeyer
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AgarthaVisionApp : Application(), ImageLoaderFactory {
    @Inject
    lateinit var psgcSeeder: PsgcSeeder

    // Lazy so the Supabase-backed graph is built only when Coil first needs an image,
    // not during Application.onCreate. Field-injecting the repository eagerly forced the
    // Supabase client to construct at app creation, which throws under Robolectric and
    // broke every unit test that instantiates the real Application.
    @Inject
    lateinit var sampleImageRepository: Lazy<SampleImageRepository>

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

    /**
     * App-wide ImageLoader. Coil calls this once (lazily, before the first load), at which
     * point Hilt field injection is already complete, so [sampleImageRepository] is ready.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SampleImageKeyer())
                add(SampleImageFetcher.Factory(sampleImageRepository))
            }
            .build()
}
