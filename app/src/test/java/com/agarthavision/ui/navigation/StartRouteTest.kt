package com.agarthavision.ui.navigation

import com.agarthavision.domain.usecase.auth.AuthGate
import org.junit.Assert.assertEquals
import org.junit.Test

class StartRouteTest {

    @Test
    fun `not seen starts on onboarding`() {
        assertEquals(Screen.Onboarding.route, startRouteFor(AuthGate.NeedsLogin, false))
    }

    @Test
    fun `seen and needs login starts on login`() {
        assertEquals(Screen.Login.route, startRouteFor(AuthGate.NeedsLogin, true))
    }

    @Test
    fun `seen and authed starts on dashboard`() {
        assertEquals(Screen.Dashboard.route, startRouteFor(AuthGate.Authed, true))
    }

    @Test
    fun `onboarding exits to login when login is needed`() {
        assertEquals(Screen.Login.route, onboardingExitRoute(AuthGate.NeedsLogin))
    }

    @Test
    fun `onboarding exits to dashboard when authed`() {
        assertEquals(Screen.Dashboard.route, onboardingExitRoute(AuthGate.Authed))
    }
}
