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
    /** Optional display name copied from auth metadata when available. */
    val displayName: String? = null,
)
