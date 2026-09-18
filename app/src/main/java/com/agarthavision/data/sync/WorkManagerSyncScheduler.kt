package com.agarthavision.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.agarthavision.domain.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs sync passes through WorkManager.
 *
 * Replaces the old arrangement, where every sync lived in a `viewModelScope`: navigating away
 * mid-push cancelled it, nothing retried, and nothing survived process death. WorkManager
 * persists the request across both, and reschedules itself after a reboot without the app
 * needing `RECEIVE_BOOT_COMPLETED`.
 *
 * **Not a foreground service, deliberately.** The app targets SDK 36, and from Android 15 a
 * `dataSync` foreground service is capped at six hours in any twenty-four and then throws
 * `ForegroundServiceStartNotAllowedException` until the user foregrounds the app. WorkManager
 * is Google's named alternative for exactly this case.
 *
 * **It will not always run on the fleet this ships to.** MIUI gates background work behind a
 * per-app "Background autostart" permission that is off by default for third-party apps, and
 * Xiaomi handsets are what the medtechs carry. That is why nothing here is load-bearing: a
 * missed pass is caught by the next foreground trigger, and the queue is durable in Room
 * regardless. See `docs/map/processes/sync.md`.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun requestSync() {
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request())
    }

    /**
     * True while a pass is running.
     *
     * Read off WorkManager rather than tracked in a flag, so it stays correct when the screen
     * that asked for the sync is gone and when the process that asked for it is gone.
     *
     * **Cold on purpose — do not turn this back into a plain `val`.** This class is a
     * `@Singleton` that Hilt constructs while it is field-injecting `AgarthaVisionApp`. An
     * eagerly built flow touches `WorkManager.getInstance()` during that construction, which
     * calls straight back into the app's `workManagerConfiguration` for the
     * `HiltWorkerFactory` that the very same injection pass has not assigned yet, and the app
     * dies on startup with `lateinit property workerFactory has not been initialized`.
     * Deferring to first collection puts that lookup safely after `onCreate`.
     */
    override val isSyncing: Flow<Boolean> = flow {
        emitAll(
            workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } },
        )
    }.conflate()

    private fun request() = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            // CONNECTED rather than UNMETERED: a medtech on mobile data in a barangay is the
            // case this exists for, and the payload is rows, not images.
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        )
        // Expedited when the quota allows, an ordinary job when it does not. The fallback is
        // required: without it an out-of-quota request throws instead of queueing.
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
        .build()

    private companion object {
        /**
         * One name for every trigger, so app start, a patient save and a report generated in
         * the same minute collapse into one pass instead of three.
         */
        const val SYNC_WORK_NAME = "agartha-sync"

        /** WorkManager's own floor is 10 seconds; this is the first retry delay. */
        const val BACKOFF_SECONDS = 30L
    }
}
