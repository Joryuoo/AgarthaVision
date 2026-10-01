package com.agarthavision.domain.model

/**
 * What the server last said about whether the cached account may still use the app
 * (14zcqntjph8).
 *
 * Only [REFUSED] has consequences: the device is signed out and its synced data removed. So
 * it is reserved for an answer the server actually gave. Anything that could be the network,
 * the server having a bad minute, or a token that simply expired is [UNKNOWN], and the app
 * carries on offline exactly as before.
 */
enum class AccountAccess {
    /** The server renewed the login. */
    ALLOWED,

    /**
     * The server refused to renew the login: the account was removed, banned, or had its
     * sessions revoked. The Admin Console's deactivation and offboarding end in one of those,
     * and so does a password change made on the web or on another phone.
     */
    REFUSED,

    /** No answer worth acting on: offline, a timeout, a 5xx, or nobody signed in. */
    UNKNOWN,
}

/**
 * Why the device was last signed out by the server rather than by the medtech, kept so the
 * login screen can say so (14zcqntjph8).
 *
 * @property unsyncedKept items of the signed-out account that had not reached Supabase yet. They
 *   stay on the phone and upload after the medtech signs back in. Zero in the usual case.
 */
data class SignedOutNotice(
    val unsyncedKept: Int,
)
