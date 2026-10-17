package com.agarthavision.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agarthavision.R
import com.agarthavision.core.util.Logger
import com.agarthavision.domain.model.HumanCheckConfig
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * The sign-in human check as a screen holds it (14zcqntnqxf).
 *
 * A token is single-use and expires, so it is dropped after every sign-in attempt and whenever
 * the widget says it expired; bumping [resetKey] asks the widget for a fresh one.
 */
data class HumanCheckUiState(
    val config: HumanCheckConfig = HumanCheckConfig(siteKey = "", hostname = ""),
    val token: String? = null,
    /** The widget could not load or the check failed; the screen offers a retry. */
    val failed: Boolean = false,
    val resetKey: Int = 0,
) {
    /** False when the build carries no site key: no widget, and sign-in sends no token. */
    val required: Boolean
        get() = config.isEnabled

    /** Whether a submit may go ahead as far as the check is concerned. */
    val isSatisfied: Boolean
        get() = !required || token != null

    fun withToken(token: String) = copy(token = token, failed = false)

    fun expired() = copy(token = null)

    fun failed() = copy(token = null, failed = true)

    /** After any attempt, or on the medtech's retry: drop the token and run the check again. */
    fun renewed() = copy(token = null, failed = false, resetKey = resetKey + 1)
}

/** Test tag on the widget's container. */
const val HUMAN_CHECK_TAG = "human_check"

/**
 * Cloudflare Turnstile in a WebView, the way Cloudflare documents it for native apps: Turnstile
 * runs only in a browser. The page is loaded with `https://<hostname>/` as its base URL, so
 * Turnstile sees the hostname the widget is registered for without the app hosting a page.
 *
 * The token goes to the auth provider with the sign-in, and the provider checks it with
 * Cloudflare (siteverify). The app never holds the widget's secret.
 */
@Composable
fun HumanCheckWidget(
    action: String,
    state: HumanCheckUiState,
    callbacks: HumanCheckCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().testTag(HUMAN_CHECK_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TurnstileWebView(
            config = state.config,
            action = action,
            dark = colors.isDark,
            resetKey = state.resetKey,
            callbacks = callbacks,
        )
        if (state.failed) {
            Text(
                text = stringResource(R.string.human_check_failed),
                color = colors.dangerText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = callbacks.onRetry) {
                Text(text = stringResource(R.string.human_check_retry), color = colors.accent)
            }
        }
    }
}

/** What the widget reports back. Delivered on the main thread. */
data class HumanCheckCallbacks(
    val onToken: (String) -> Unit,
    val onExpired: () -> Unit,
    val onError: () -> Unit,
    val onRetry: () -> Unit,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileWebView(
    config: HumanCheckConfig,
    action: String,
    dark: Boolean,
    resetKey: Int,
    callbacks: HumanCheckCallbacks,
) {
    val current by rememberUpdatedState(callbacks)
    val webView = remember { mutableStateOf<WebView?>(null) }
    val firstResetKey = remember { resetKey }

    LaunchedEffect(resetKey) {
        if (resetKey != firstResetKey) webView.value?.evaluateJavascript(RESET_SCRIPT, null)
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(WIDGET_HEIGHT),
        factory = { context ->
            val main = Handler(Looper.getMainLooper())
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                // Turnstile needs JavaScript, DOM storage and cookies (Cloudflare's WebView notes).
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(HumanCheckBridge(main) { current }, BRIDGE_NAME)
                // The widget's privacy and terms links open in the browser, never in this frame.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        runCatching {
                            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString())))
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(
                    "https://${config.hostname}/",
                    turnstilePage(siteKey = config.siteKey, action = action, dark = dark),
                    "text/html",
                    "utf-8",
                    null,
                )
                webView.value = this
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface(BRIDGE_NAME)
            view.destroy()
            webView.value = null
        },
    )
}

/**
 * What the page calls. JavaScript calls arrive on a WebView thread, so each is posted to the
 * main thread. Public (in bytecode) because the WebView only exposes public methods.
 */
internal class HumanCheckBridge(
    private val main: Handler,
    private val callbacks: () -> HumanCheckCallbacks,
) {
    @JavascriptInterface
    fun onToken(token: String) {
        main.post { callbacks().onToken(token) }
    }

    @JavascriptInterface
    fun onExpired() {
        main.post { callbacks().onExpired() }
    }

    @JavascriptInterface
    fun onError(code: String) {
        Logger.w(TAG, "Turnstile reported error $code")
        main.post { callbacks().onError() }
    }

    private companion object {
        const val TAG = "HumanCheck"
    }
}

/**
 * The page the WebView loads: Turnstile rendered explicitly, reporting through [BRIDGE_NAME].
 * Internal so the page is tested without a WebView.
 */
internal fun turnstilePage(siteKey: String, action: String, dark: Boolean): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html, body { margin: 0; padding: 0; background: transparent; } body { display: flex; justify-content: center; }</style>
<script>
var widgetId = null;
function report(name, value) { if (window.$BRIDGE_NAME) { window.$BRIDGE_NAME[name](value === undefined ? "" : String(value)); } }
function onTurnstileLoad() {
  widgetId = turnstile.render("#check", {
    sitekey: ${jsString(siteKey)},
    action: ${jsString(action)},
    theme: ${jsString(if (dark) "dark" else "light")},
    size: "flexible",
    "refresh-expired": "auto",
    callback: function (token) { report("onToken", token); },
    "expired-callback": function () { report("onExpired"); },
    "timeout-callback": function () { report("onExpired"); },
    "error-callback": function (code) { report("onError", code); return true; }
  });
}
function resetHumanCheck() { if (widgetId !== null) { turnstile.reset(widgetId); } }
</script>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=onTurnstileLoad&render=explicit" async defer onerror="report('onError', 'load')"></script>
</head>
<body><div id="check"></div></body>
</html>
""".trimIndent()

/** A JavaScript string literal that is also safe inside an HTML `<script>`. */
internal fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<', '>', '&', ' ', ' ' -> append("\\u%04x".format(char.code))
            else -> if (char.code < FIRST_PRINTABLE) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}

private const val BRIDGE_NAME = "AgarthaHumanCheck"
private const val FIRST_PRINTABLE = 0x20
private const val RESET_SCRIPT = "resetHumanCheck();"

// Turnstile's "flexible" widget is 65 dp tall; a little room for its focus ring.
private val WIDGET_HEIGHT = 72.dp
