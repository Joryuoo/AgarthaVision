package com.agarthavision.domain.model

/**
 * Cached snapshot of the last authenticated medtech, persisted device-side so that
 * offline work can be attributed to an owner without a live Supabase session.
 *
 * Distinct from whether Supabase currently holds a valid token: the identity survives
 * token expiry and offline cold starts. Per ADR-007 (offline-first entry).
 */
data class LocalIdentity(
    /** Supabase Auth user id (matches `profiles.id` / `auth.uid()`). */
    val userId: String,
    /** Email the user signed in with; shown in the Dashboard account state. */
    val email: String,
    /** Medtech's required full name (14zcqntx3v6). Defaults to [PENDING_PROFILE_PLACEHOLDER] if not supplied. */
    val displayName: String = PENDING_PROFILE_PLACEHOLDER,
    /** Last known branding for the signed-in medtech's active laboratory. */
    val laboratory: LaboratoryBranding? = null,
) {
    companion object {
        const val PENDING_PROFILE_PLACEHOLDER = "Pending Profile Setup"
    }
}
