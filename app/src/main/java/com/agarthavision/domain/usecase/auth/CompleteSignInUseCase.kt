package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.sync.SyncScheduler
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * What happens between "the credentials were accepted" and "the dashboard opens".
 *
 * Only the patient list is awaited: a medtech who signs in at the clinic and drives to a
 * barangay with no signal has to arrive with their patients on the device (PB-08a), and that
 * pull takes a second or two. Everything else (pushing pending rows, the other pulls, caching
 * frames) is handed to a background pass via [SyncScheduler.requestSyncAfterSignIn], which is
 * requested even when the pull failed so nothing waits on the next foreground trigger.
 *
 * Never throws (cancellation aside): the caller must navigate whatever happens here.
 */
class CompleteSignInUseCase @Inject constructor(
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase,
    private val syncScheduler: SyncScheduler,
) {
    /**
     * @return success when the patient pull ran (or was skipped); failure carries the pull's error.
     */
    suspend operator fun invoke(): Result<Unit> {
        val pull = try {
            fetchRemoteDataUseCase.pullPatientsOnly()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(e)
        }
        // A cancelled pull must stay cancelled rather than be folded into an ignorable failure.
        pull.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        requestSync()?.let { return Result.failure(it) }
        return pull.map { }
    }

    /** @return the scheduler's error, or null when the request went through. */
    private fun requestSync(): Throwable? = try {
        syncScheduler.requestSyncAfterSignIn()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        e
    }
}
