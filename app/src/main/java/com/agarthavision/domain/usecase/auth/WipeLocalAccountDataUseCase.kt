package com.agarthavision.domain.usecase.auth

import androidx.room.withTransaction
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Removes what already reached Supabase from a phone the server has signed out (14zcqntjph8):
 * patients, sessions, samples, verdicts, reports, their JPEGs and exported report files.
 *
 * **Wider than a sign-out on synced data.** [SignOutUseCase] leaves synced rows behind, hidden by
 * `user_id`, for the next login on this phone. Here the phone may belong to someone who has left
 * the laboratory and the database is unencrypted (`patient-pii-position.md` §2), so everything
 * that reached Supabase goes. Nothing is lost by that: the server keeps all of it (C8), and the
 * next sign-in pulls back whatever that account may see.
 *
 * **Narrower than a sign-out on unsynced work: it all stays.** The server refuses a login both
 * when an account is removed and when its password was changed somewhere else, and the phone
 * cannot tell the two apart. A medtech who changed their password on the web must not lose the
 * field work this phone has not uploaded yet. It stays, owned by them, and uploads after they
 * sign back in. Its count goes to the login screen so they know it is waiting.
 */
class WipeLocalAccountDataUseCase @Inject constructor(
    private val database: AgarthaDatabase,
    private val observePendingSyncCountsUseCase: ObservePendingSyncCountsUseCase,
    private val sampleImageStore: SampleImageStore,
    private val reportFileStore: ReportFileStore,
    private val initialFetchStateStore: InitialFetchStateStore,
) {
    /** @return how many of [userId]'s unsynced items were kept for their next sign-in. */
    suspend operator fun invoke(userId: String): Int {
        val wipeDao = database.accountWipeDao()
        val (imagePaths, reportFiles) = database.withTransaction {
            val images = wipeDao.getSyncedSampleImagePaths()
            val files = wipeDao.getSyncedReportFiles()
            wipeDao.deleteSyncedReports()
            wipeDao.deleteSyncedSamples()
            wipeDao.deleteSyncedSessions()
            wipeDao.deleteSyncedPatients()
            images to files
        }

        // Files after the commit, as in DiscardUnsyncedDataUseCase: a file that outlives its row
        // is inert, a row that outlives its file renders broken.
        imagePaths.filter { it.isNotBlank() }.forEach { sampleImageStore.deleteJpeg(it) }
        sampleImageStore.clearImageLoaderCaches()
        reportFiles
            .flatMap { listOfNotNull(it.csvFilePath, it.pdfFilePath) }
            .filter { it.isNotBlank() }
            .forEach { reportFileStore.delete(it) }

        // A later sign-in of this account must pull everything again, and show "not yet synced"
        // until it has.
        initialFetchStateStore.clear(userId)
        return observePendingSyncCountsUseCase(userId).first().totalUnsynced
    }
}
