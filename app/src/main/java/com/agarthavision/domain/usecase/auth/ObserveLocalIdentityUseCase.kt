package com.agarthavision.domain.usecase.auth

import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams the cached [LocalIdentity] so screens can render owner/anonymous state without
 * requiring a live Supabase session. Emits null when no medtech has signed in on this
 * device. Per ADR-007.
 */
class ObserveLocalIdentityUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    /** Observes the device's cached identity. */
    operator fun invoke(): Flow<LocalIdentity?> = authRepository.observeLocalIdentity()
}
