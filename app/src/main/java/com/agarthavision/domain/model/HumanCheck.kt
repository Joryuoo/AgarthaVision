package com.agarthavision.domain.model

/**
 * The human check every sign-in passes through (14zcqntnqxf), so a script cannot guess
 * passwords against the auth provider. Cloudflare Turnstile today; the app only knows a public
 * site key and the hostname the widget is registered for (D7).
 *
 * Blank turns the check off: no widget, and sign-in sends no token. That only works while the
 * project's CAPTCHA protection is off, so a release meant for medtechs is built with both set.
 */
data class HumanCheckConfig(val siteKey: String, val hostname: String) {
    val isEnabled: Boolean
        get() = siteKey.isNotBlank() && hostname.isNotBlank()
}

/**
 * The auth provider refused the human check that came with a sign-in: missing, expired, already
 * used, or failed. Nothing about the email or password is implied.
 */
class HumanCheckRejectedException(cause: Throwable? = null) :
    Exception("The security check was refused.", cause)
