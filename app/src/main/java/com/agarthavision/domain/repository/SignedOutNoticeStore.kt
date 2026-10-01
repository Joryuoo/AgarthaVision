package com.agarthavision.domain.repository

import com.agarthavision.domain.model.SignedOutNotice
import kotlinx.coroutines.flow.Flow

/**
 * Remembers that the server signed this device out, until somebody signs in again
 * (14zcqntjph8).
 *
 * Persisted, because the wipe can happen in a background sync with no screen open, and the
 * medtech has to read why the next time they open the app.
 */
interface SignedOutNoticeStore {
    /** The pending notice, or null when the last sign-out was the medtech's own. */
    fun observe(): Flow<SignedOutNotice?>

    suspend fun record(notice: SignedOutNotice)

    /** Called on a successful sign-in. */
    suspend fun clear()
}
