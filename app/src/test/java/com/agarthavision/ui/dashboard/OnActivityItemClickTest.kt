package com.agarthavision.ui.dashboard

import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [onActivityItemClick] routes each [ActivityItem] subtype to the screen that best explains it.
 * Exercised directly since it is a plain top-level function with no Compose or Android
 * dependency.
 */
class OnActivityItemClickTest {

    private var navigatedRoute: String? = null
    private var navigatedTab: String? = null

    private val onNavigate: (String) -> Unit = { navigatedRoute = it }
    private val onNavigateToTab: (String) -> Unit = { navigatedTab = it }

    @Test
    fun `FramesVerified opens SessionDetail for its session`() {
        onActivityItemClick(
            ActivityItem.FramesVerified("s-1", "Smear 1", count = 2, occurredAt = 1_000L),
            onNavigate,
            onNavigateToTab,
        )

        assertEquals(Screen.SessionDetail.createRoute("s-1"), navigatedRoute)
        assertNull(navigatedTab)
    }

    @Test
    fun `FramesCaptured opens SessionDetail for its session`() {
        onActivityItemClick(
            ActivityItem.FramesCaptured("s-2", "Smear 2", count = 3, occurredAt = 1_000L),
            onNavigate,
            onNavigateToTab,
        )

        assertEquals(Screen.SessionDetail.createRoute("s-2"), navigatedRoute)
        assertNull(navigatedTab)
    }

    @Test
    fun `SessionStarted opens SessionDetail for its session`() {
        onActivityItemClick(
            ActivityItem.SessionStarted("s-3", "Smear 3", occurredAt = 1_000L),
            onNavigate,
            onNavigateToTab,
        )

        assertEquals(Screen.SessionDetail.createRoute("s-3"), navigatedRoute)
        assertNull(navigatedTab)
    }

    @Test
    fun `PatientAdded opens PatientSessions for its patient`() {
        onActivityItemClick(
            ActivityItem.PatientAdded("p-1", "R.*, J*.", occurredAt = 1_000L),
            onNavigate,
            onNavigateToTab,
        )

        assertEquals(Screen.PatientSessions.createRoute("p-1"), navigatedRoute)
        assertNull(navigatedTab)
    }

    @Test
    fun `SyncFinished switches to the Settings tab rather than navigating a route`() {
        onActivityItemClick(
            ActivityItem.SyncFinished(itemCount = 5, occurredAt = 1_000L),
            onNavigate,
            onNavigateToTab,
        )

        assertEquals(Screen.Settings.route, navigatedTab)
        assertNull(navigatedRoute)
    }
}
