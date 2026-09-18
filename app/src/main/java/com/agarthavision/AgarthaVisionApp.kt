package com.agarthavision

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.agarthavision.data.local.psgc.PsgcSeeder
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.domain.repository.SampleImageRepository
import com.agarthavision.domain.sync.SyncScheduler
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
class AgarthaVisionApp : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var psgcSeeder: PsgcSeeder

    @Inject
    lateinit var speciesSuggestionSeeder: SpeciesSuggestionSeeder

    @Inject
    lateinit var syncScheduler: SyncScheduler

    /**
     * Lets WorkManager construct `@HiltWorker` workers. The default initializer is removed in
     * the manifest so this configuration is the one that takes effect.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
        // Same shape and the same reasoning: the species index is offline reference data,
        // nothing on screen waits for it, and a failure degrades the "Other species" field
        // to plain free text rather than blocking a submission. Gated on the table being
        // empty, so it re-seeds after the destructive migration a version bump causes.
        applicationScope.launch { speciesSuggestionSeeder.seedIfNeeded() }

        // App start is a sync trigger, and was the one ADR-007 and the docs claimed existed
        // without anything implementing it. Enqueued rather than run here: it must not hold
        // up onCreate, it has to outlive whatever screen the medtech lands on, and it no-ops
        // when the device is signed out. The network constraint decides when it actually runs.
        syncScheduler.requestSync()
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
