package com.agarthavision

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.agarthavision.core.session.SessionManager
import com.agarthavision.data.local.psgc.PsgcSeeder
import com.agarthavision.data.local.species.SpeciesSuggestionSeeder
import com.agarthavision.domain.repository.SampleImageRepository
import com.agarthavision.domain.sync.SyncScheduler
import com.agarthavision.ui.image.SampleImageFetcher
import com.agarthavision.ui.image.SampleImageKeyer
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
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
     * Lazy so restoring the active session runs off the main thread in [onCreate] rather
     * than forcing the Supabase-backed session graph to construct synchronously.
     */
    @Inject
    lateinit var sessionManager: Lazy<SessionManager>

    /**
     * Lazy so the client is warmed off the main thread in [onCreate] instead of being built
     * on first use by whichever ViewModel or repository asks for it first.
     */
    @Inject
    lateinit var supabaseClient: Lazy<SupabaseClient>

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

        // Sessions no longer end, so one can outlive the process that created it. Without
        // this the app would come back idle with a smear still open: the dashboard card would
        // be gone and the verification queue would render empty, because FlaggedFrameStore
        // emits an empty list when there is no active session. Nothing lost, but it would look
        // like everything was. Run as its own launch so a failure here never blocks the
        // Supabase warm-up below.
        applicationScope.launch {
            runCatching { sessionManager.get().restoreActiveSession() }
                .onFailure { Log.w(TAG, "Failed to restore active session", it) }
        }

        // Pre-creates the Supabase client off the main thread so the first real network call
        // (e.g. tapping "start session") does not pay that construction cost synchronously on
        // the UI thread. Under Robolectric this may throw, which is caught intentionally so it
        // never breaks tests that instantiate the real Application.
        applicationScope.launch {
            runCatching { supabaseClient.get() }
                .onFailure { Log.w(TAG, "Failed to warm up Supabase client", it) }
        }
    }

    /**
     * App-wide ImageLoader. Coil calls this once (lazily, before the first load), at which
     * point Hilt field injection is already complete, so [sampleImageRepository] is ready.
     *
     * Disk cache is fixed at 100MB (vs Coil's default of ~2% of disk space, clamped between
     * 10MB and 250MB) and the memory cache is sized at 25% of the memory class (vs Coil's
     * default 20%), both sized up because the records and verification queue screens are
     * image-heavy.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SampleImageKeyer())
                add(SampleImageFetcher.Factory(sampleImageRepository))
            }
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(MEMORY_CACHE_PERCENT).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            // Sample images are content-stable under their storage-path key: uploads use
            // upsert = true to the same path, so a disk cache hit never needs revalidation.
            .respectCacheHeaders(false)
            // Intentionally not reusing the app's other OkHttpClient (used for the inference
            // API): it carries a bearer-token interceptor that must never be sent to the
            // Supabase Storage host. Coil's own default lazy OkHttpClient is correct here.
            .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private companion object {
        private const val TAG = "AgarthaVisionApp"
        private const val MEMORY_CACHE_PERCENT = 0.25
        private const val DISK_CACHE_MAX_BYTES = 100L * 1024 * 1024
        private const val IMAGE_CACHE_DIR = "image_cache"
    }
}
