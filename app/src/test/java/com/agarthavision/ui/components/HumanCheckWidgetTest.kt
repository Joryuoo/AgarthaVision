package com.agarthavision.ui.components

import com.agarthavision.domain.model.HumanCheckConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign-in human check's page and state (14zcqntnqxf), without a WebView. */
class HumanCheckWidgetTest {

    private val enabled = HumanCheckConfig(siteKey = "0x4AAAAAAA-test", hostname = "example.test")

    @Test
    fun `a value is quoted and cannot close the script it sits in`() {
        assertEquals("\"a\\\"b\\\\c\\u003c/script\\u003e\"", jsString("a\"b\\c</script>"))
    }

    @Test
    fun `the page renders the widget with the site key, action and theme`() {
        val page = turnstilePage(siteKey = "0x4AAAAAAA-test", action = "app_sign_in", dark = true)

        assertTrue(page.contains("sitekey: \"0x4AAAAAAA-test\""))
        assertTrue(page.contains("action: \"app_sign_in\""))
        assertTrue(page.contains("theme: \"dark\""))
        assertTrue(page.contains("turnstile/v0/api.js?onload=onTurnstileLoad&render=explicit"))
        assertTrue(page.contains("window.AgarthaHumanCheck"))
    }

    @Test
    fun `without a site key the check is off and never blocks a submit`() {
        val state = HumanCheckUiState(config = HumanCheckConfig(siteKey = "", hostname = "example.test"))

        assertFalse(state.required)
        assertTrue(state.isSatisfied)
    }

    @Test
    fun `with a site key a submit waits for a token`() {
        val state = HumanCheckUiState(config = enabled)

        assertTrue(state.required)
        assertFalse(state.isSatisfied)
        assertTrue(state.withToken("token-1").isSatisfied)
    }

    @Test
    fun `an expired or failed check drops the token`() {
        val passed = HumanCheckUiState(config = enabled).withToken("token-1")

        assertNull(passed.expired().token)
        assertNull(passed.failed().token)
        assertTrue(passed.failed().failed)
    }

    @Test
    fun `renewing drops the spent token and asks the widget for a new one`() {
        val passed = HumanCheckUiState(config = enabled).withToken("token-1").failed()
        val renewed = passed.renewed()

        assertNull(renewed.token)
        assertFalse(renewed.failed)
        assertEquals(passed.resetKey + 1, renewed.resetKey)
    }
}
