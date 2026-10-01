package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.SignedOutNoticeStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Emits why the server signed this phone out, or null when it did not (14zcqntjph8). Drives the
 * jump to the login screen and the explanation on it, until the next successful sign-in.
 */
class ObserveSignedOutNoticeUseCase @Inject constructor(
    private val signedOutNoticeStore: SignedOutNoticeStore,
) {
    operator fun invoke(): Flow<SignedOutNotice?> = signedOutNoticeStore.observe()
}
