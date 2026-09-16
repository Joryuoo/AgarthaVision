package com.agarthavision.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the product-decided values of the session input constants so a drive-by
 * edit to [SessionInputLimits.kt] shows up in test output rather than silently
 * widening or narrowing the fields users see.
 *
 * [SESSION_LABEL_MAX_LENGTH] was deliberately reduced from 40 to 20 to ensure
 * the label fits on a single card title line — this test locks that decision in.
 */
class SessionInputLimitsTest {

    @Test
    fun `SESSION_LABEL_MAX_LENGTH is 20`() {
        assertEquals(
            "SESSION_LABEL_MAX_LENGTH was changed; update this test only after a product decision",
            20,
            SESSION_LABEL_MAX_LENGTH,
        )
    }

    @Test
    fun `SESSION_NOTE_MAX_LENGTH is 200`() {
        assertEquals(
            "SESSION_NOTE_MAX_LENGTH was changed; update this test only after a product decision",
            200,
            SESSION_NOTE_MAX_LENGTH,
        )
    }
}
