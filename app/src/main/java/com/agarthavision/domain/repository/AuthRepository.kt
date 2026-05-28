package com.agarthavision.domain.repository

/**
 * Authentication boundary used by presentation-facing use cases.
 */
interface AuthRepository {
    /**
     * Streams the current user's ID (null when signed out).
     */
    val userIdFlow: kotlinx.coroutines.flow.Flow<String?>

    /**
     * Returns true when Supabase has a persisted authenticated session.
     */
    suspend fun hasActiveSession(): Boolean

    /**
     * Signs in an existing dashboard-provisioned user with email and password.
     */
    suspend fun signIn(email: String, password: String)

    /**
     * Returns the current user's ID, or null if not logged in.
     */
    suspend fun getCurrentUserId(): String?
}
