package com.agarthavision.domain.usecase.auth

import androidx.room.withTransaction
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.domain.repository.ReportFileStore
import javax.inject.Inject

/**
 * Removes an account's clinical data from the phone once the server no longer accepts it
 * (14zcqntjph8). The deactivated medtech's patients, sessions, samples, verdicts, reports, JPEGs
 * and exported report files all go.
 *
 * **Wider than a sign-out on purpose.** [SignOutUseCase] removes only unsynced work and leaves
 * synced rows behind, hidden by `user_id`, for the next login on this phone. Here the phone
 * belongs to someone who has left the laboratory and the database is unencrypted
 * (`patient-pii-position.md` §2), so everything that reached Supabase is removed too. Nothing is
 * lost by that: the server keeps all of it (C8), and the next sign-in pulls back whatever that
 * account may see.
 *
 * **Unsynced work is removed as well, and counted.** The server has refused this account, so it
 * can no longer take the upload: there is no final sync to try. The count goes to the login
 * screen so the removal is never silent.
 *
 * Other accounts' unsynced rows survive; see [com.agarthavision.data.local.dao.AccountWipeDao].
 */
class WipeLocalAccountDataUseCase @Inject constructor(
    private val database: AgarthaDatabase,
    private val discardUnsyncedDataUseCase: DiscardUnsyncedDataUseCase,
    private val sampleImageStore: SampleImageStore,
    private val reportFileStore: ReportFileStore,
    private val initialFetchStateStore: InitialFetchStateStore,
) {
    /** @return how many unsynced items were removed with everything else. */
    suspend operator fun invoke(userId: String): Int {
        val unsynced = discardUnsyncedDataUseCase(userId).total

        val wipeDao = database.accountWipeDao()
        val (imagePaths, reportFiles) = database.withTransaction {
            val images = wipeDao.getWipedSampleImagePaths(userId)
            val files = wipeDao.getWipedReportFiles(userId)
            wipeDao.deleteReports(userId)
            wipeDao.deleteSamples(userId)
            wipeDao.deleteSessions(userId)
            wipeDao.deletePatientLinks(userId)
            wipeDao.deletePatients(userId)
            images to files
        }

        // Files after the commit, as in DiscardUnsyncedDataUseCase: a file that outlives its row
        // is inert, a row that outlives its file renders broken.
        imagePaths.filter { it.isNotBlank() }.forEach { sampleImageStore.deleteJpeg(it) }
        sampleImageStore.deleteAllFor(userId)
        sampleImageStore.clearImageLoaderCaches()
        reportFiles
            .flatMap { listOfNotNull(it.csvFilePath, it.pdfFilePath) }
            .filter { it.isNotBlank() }
            .forEach { reportFileStore.delete(it) }

        // A later sign-in of this account must pull everything again, and show "not yet synced"
        // until it has.
        initialFetchStateStore.clear(userId)
        return unsynced
    }
}
