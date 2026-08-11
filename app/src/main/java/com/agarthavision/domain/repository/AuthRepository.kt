package com.agarthavision.domain.repository

import com.agarthavision.domain.model.LocalIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Authentication boundary used by presentation-facing use cases.
 *
 * Per ADR-007, identity (the cached last-known medtech) is tracked separately from
 * live authentication: [observeLocalIdentity] survives token expiry and offline cold
 * starts, while [isAuthenticated] reflects whether Supabase currently holds a token.
 */
interface AuthRepository {
    /**
     * Streams the cached [LocalIdentity], or null when no medtech has ever signed in
     * on this device. Emits on sign-in and process restart.
     */
    fun observeLocalIdentity(): Flow<LocalIdentity?>

    /**
     * Returns the cached [LocalIdentity]'s user id, or null when no medtech has signed
     * in on this device. Survives offline cold starts (unlike [getCurrentUserId], which
     * reads the live Supabase session). Used for offline ownership attribution.
     */
    suspend fun currentLocalUserId(): String?

    /**
     * Returns true when Supabase currently holds a valid authenticated session.
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Returns true when Supabase has a persisted authenticated session.
     */
    suspend fun hasActiveSession(): Boolean

    /**
     * Signs in an existing dashboard-provisioned user with email and password, and
     * caches the resulting [LocalIdentity] for offline attribution.
     */
    suspend fun signIn(email: String, password: String)

    /**
     * Returns the current user's ID, or null if not logged in.
     */
    suspend fun getCurrentUserId(): String?

    /**
     * Signs the medtech out per ADR-008: revokes the live Supabase session **and** clears
     * the cached [LocalIdentity], returning the device to the never-signed-in state. Local
     * clearing always succeeds, even when the remote token revocation fails, so sign-out
     * works offline. Already-owned rows are unaffected — only future offline attribution
     * changes.
     */
    suspend fun signOut()
}
