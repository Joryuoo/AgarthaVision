package com.agarthavision.ui.sessions

import com.agarthavision.domain.session.SessionLabelGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the product-decided values of the session input constants so a drive-by
 * edit to [SessionInputLimits.kt] shows up in test output rather than silently
 * widening or narrowing the fields users see.
 *
 * [SESSION_LABEL_MAX_LENGTH] was 40, then 20 to fit one card title line, and is now 32. The
 * 20 was the bug: `C.G.-0730600000-001` is exactly 20 characters, so the generated label
 * filled the field completely and the first character typed while editing it was dropped.
 *
 * `SESSION_NOTE_MAX_LENGTH` is gone. The note it bounded went with PB-09 — a session has no
 * note, so there is no value left to pin.
 */
class SessionInputLimitsTest {

    @Test
    fun `SESSION_LABEL_MAX_LENGTH is 32`() {
        assertEquals(
            "SESSION_LABEL_MAX_LENGTH was changed; update this test only after a product decision",
            32,
            SESSION_LABEL_MAX_LENGTH,
        )
    }

    @Test
    fun `the generated label leaves room to be edited`() {
        // The regression this file exists to catch: a cap that exactly equals the generated
        // label reads as a working field and silently eats the first keystroke.
        val generated = "C.G.-0730600000-${"1".padStart(SessionLabelGenerator.SEQUENCE_DIGITS, '0')}"

        assertTrue(
            "the cap must exceed the generated label, not merely fit it",
            generated.length < SESSION_LABEL_MAX_LENGTH,
        )
    }
}
