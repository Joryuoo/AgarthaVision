package com.agarthavision.domain.usecase.sync

import android.util.Log
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.supabase.SampleRemoteDataSource
import javax.inject.Inject

/**
 * Outcome of one image-caching pass.
 *
 * @property downloaded frames newly brought onto the device.
 * @property missing frames the device still does not hold when the pass ends — a download that
 *   failed, and anything the per-pass ceiling deferred. **This is what the sync card reads.**
 *   A pass that fetched every row and no image is not a synced device: the medtech finds that
 *   out in a barangay with no signal, holding a sample they cannot open and therefore cannot
 *   correct.
 * @property evicted frames dropped to stay inside the storage budget.
 */
data class ImageCacheSummary(
    val downloaded: Int = 0,
    val missing: Int = 0,
    val evicted: Int = 0,
) {
    val isComplete: Boolean get() = missing == 0
}

/**
 * Brings verified sample frames onto the device during sync, and keeps the cache bounded.
 *
 * **Why this exists at all.** PB-15b routes the Sample Data Screen through a local file, then a
 * signed Storage URL, so a synced sample opens *if there is signal at that moment*. Editing
 * depends on seeing the frame: with no image there is nothing to place a box against, so an
 * uncached sample is not a sample missing a picture, it is a sample that cannot be corrected.
 * Fetching on open is the wrong moment for an app built for barangays with no signal. This
 * pulls the frames while the radio is already on.
 *
 * ### The retention policy, stated rather than emergent
 *
 * **A byte budget, newest verification first.** [MAX_CACHE_BYTES] of frames are kept, filled
 * from the most recently verified sample downwards; anything past the line is evicted. The
 * alternatives were considered and rejected: "every verified sample ever" bounds nothing, and a
 * device that fills up in the field is its own outage; a last-N-days or per-patient rule bounds
 * nothing either on a device that works one barangay all quarter.
 *
 * Two rules the budget must never break:
 *
 * 1. **An unpushed sample is never evicted.** Its local JPEG is the only copy in existence
 *    until it reaches Storage, so evicting it is not cache management, it is deleting the
 *    clinical record (C8). Only samples the server already holds are candidates, which is why
 *    `SampleDao.getCacheableSamples` filters on a non-empty `storage_path`.
 * 2. **Eviction removes a copy, never a record.** The row, its detections and the Storage
 *    object all survive; only this device's duplicate goes. Re-syncing brings it back.
 *
 * [MAX_IMAGES_PER_PASS] caps one pass so a device signing in against a year of history does not
 * spend an hour and a battery on its first sync. The cache warms over successive passes, and
 * [ImageCacheSummary.missing] keeps the UI honest about it in the meantime rather than
 * reporting a completeness the device does not have.
 */
class CacheSampleImagesUseCase @Inject constructor(
    private val sampleDao: SampleDao,
    private val sampleImageStore: SampleImageStore,
    private val sampleRemoteDataSource: SampleRemoteDataSource,
) {
    /**
     * Fills and trims the cache for [userId]. Never throws: a frame that cannot be fetched is
     * counted, not raised, because one unreachable object must not cost the pass every other
     * image it could still have brought down.
     */
    suspend operator fun invoke(userId: String): ImageCacheSummary {
        val samples = sampleDao.getCacheableSamples(userId)
        if (samples.isEmpty()) return ImageCacheSummary()

        // Newest first, so the budget is spent on the work most likely to be reopened, and the
        // fill order matches the eviction order rather than fighting it.
        val (withinBudget, beyondBudget) = samples.partitionByBudget(userId)

        var downloaded = 0
        var missing = 0
        for (sample in withinBudget) {
            val cached = sampleImageStore.cachedPathOrNull(userId, sample.sampleId)
            if (cached != null) {
                // Already held. Re-pointing the row is not redundant: a pulled row arrives with
                // image_path empty even when the file is right there, so without this the
                // device owns the frame and cannot find it.
                if (sample.imagePath != cached) {
                    sampleDao.updateImagePath(sample.sampleId, cached)
                }
                continue
            }
            if (downloaded >= MAX_IMAGES_PER_PASS) {
                missing++
                continue
            }
            val storagePath = sample.storagePath.orEmpty()
            val bytes = runCatching { sampleRemoteDataSource.downloadSampleImage(storagePath) }
                .onFailure { error -> Log.w(TAG, "Image for ${sample.sampleId} unavailable", error) }
                .getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                missing++
                continue
            }
            val path = sampleImageStore.persistJpeg(userId, sample.sampleId, bytes)
            sampleDao.updateImagePath(sample.sampleId, path)
            downloaded++
        }

        var evicted = 0
        for (sample in beyondBudget) {
            if (sampleImageStore.evict(userId, sample.sampleId)) {
                // The row stops claiming a file that is gone. Reopening it falls back to the
                // signed Storage URL, which is PB-15b's path and says so plainly when offline.
                sampleDao.updateImagePath(sample.sampleId, "")
                evicted++
            }
        }

        return ImageCacheSummary(downloaded = downloaded, missing = missing, evicted = evicted)
    }

    /**
     * Splits the newest-first list at the point the budget runs out.
     *
     * A frame not yet held is charged at [ASSUMED_IMAGE_BYTES], because its real size is only
     * knowable after downloading it — and a budget that admits everything until it is already
     * over is not a budget. The estimate is deliberately generous: overestimating caches
     * slightly less than it could, underestimating overruns the disk, and only one of those is
     * an outage in the field.
     */
    private suspend fun List<SampleEntity>.partitionByBudget(
        userId: String,
    ): Pair<List<SampleEntity>, List<SampleEntity>> {
        var spent = 0L
        val keep = mutableListOf<SampleEntity>()
        val drop = mutableListOf<SampleEntity>()
        for (sample in this) {
            val held = sampleImageStore.sizeOf(userId, sample.sampleId)
            val cost = if (held > 0L) held else ASSUMED_IMAGE_BYTES
            if (spent + cost <= MAX_CACHE_BYTES) {
                spent += cost
                keep += sample
            } else {
                drop += sample
            }
        }
        return keep to drop
    }

    companion object {
        private const val TAG = "CacheSampleImages"

        /**
         * The cache ceiling: 256 MB of frames.
         *
         * Room for roughly a thousand smear frames at the sizes this app produces, which is
         * more than a barangay campaign records, on a budget a low-end field device can spare.
         */
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

        /** What an uncached frame is charged against the budget before it is fetched. */
        const val ASSUMED_IMAGE_BYTES = 256L * 1024L

        /**
         * Frames fetched in one pass. The rest are reported [ImageCacheSummary.missing] and
         * come down on the next one, rather than turning a first sign-in into an hour of radio.
         */
        const val MAX_IMAGES_PER_PASS = 50
    }
}
