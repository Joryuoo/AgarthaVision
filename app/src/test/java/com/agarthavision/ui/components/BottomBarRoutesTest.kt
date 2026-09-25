package com.agarthavision.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bottom bar against the failure that used to be possible here.
 *
 * `bottomBarRoutes` was a second, hand-written copy of the same route literals held in
 * [tabs], and `AgarthaNavGraph` checks that copy to decide whether to draw the bar.
 * Renaming a tab's route in one place and not the other made the bar silently stop
 * appearing on that screen: no crash, no warning, and nothing to grep for, because both
 * spellings were valid strings.
 *
 * It is derived now, so this can no longer drift — these assertions exist to fail loudly if
 * somebody reintroduces the literal set.
 */
class BottomBarRoutesTest {

    @Test
    fun `bottomBarRoutes is exactly the tab routes`() {
        assertEquals(tabs.map { it.route }.toSet(), bottomBarRoutes)
    }

    @Test
    fun `the four root destinations are the tab set`() {
        assertEquals(
            listOf("dashboard", "patients", "reports", "settings"),
            tabs.map { it.route },
        )
    }

    @Test
    fun `every tab route is unique`() {
        assertEquals(tabs.size, bottomBarRoutes.size)
    }

    @Test
    fun `drill-down routes do not show the bar`() {
        // SessionDetail and SampleDetail live under records/ and address a session or a
        // sample, not a tab. Capture and Login are not tabs either.
        listOf(
            "records/session/{sessionId}",
            "records/sample/{sampleId}",
            "capture",
            "login",
            "verification_queue",
        ).forEach { route ->
            assertTrue("$route must not show the bottom bar", route !in bottomBarRoutes)
        }
    }

    @Test
    fun `the retired route names are gone`() {
        // The tab was renamed from sessions to patients and from records to reports. A
        // stale literal here is exactly the desync this file exists to catch.
        assertTrue("sessions" !in bottomBarRoutes)
        assertTrue("records" !in bottomBarRoutes)
    }
}
