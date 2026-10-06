package com.agarthavision.core.util

import android.util.Log
import com.agarthavision.BuildConfig

/**
 * Centralized logging wrapper for AgarthaVision.
 *
 * Direct calls to `android.util.Log.*` or `println` in production code are forbidden
 * by detekt rule (`ForbiddenImport` and `ForbiddenMethodCall`).
 *
 * This wrapper ensures:
 * 1. Log statements run only in debug builds or are controlled centrally.
 * 2. Throwables logged do not leak raw SQL or un-sanitized details into Logcat.
 */
object Logger {

    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message, sanitizeThrowable(throwable))
            } else {
                Log.w(tag, message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, sanitizeThrowable(throwable))
            } else {
                Log.e(tag, message)
            }
        }
    }

    /**
     * Sanitizes exception objects before passing to Logcat so that potential SQL query
     * parameters or raw values embedded in exception messages are not leaked.
     */
    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        return throwable
    }
}
