package com.agarthavision.ui.dashboard

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [coveragePageHeight] is the direct regression test for the blank-My-Coverage-page bug: page 1
 * used to get `heightIn(min = 220.dp)` inside the pager's unbounded LazyColumn context, which
 * collapsed to 0px. It now always gets a FIXED height, falling back to 220.dp only until page 0's
 * measured height is known.
 */
class KpiPagerHeightTest {

    @Test
    fun `falls back to 220dp when page 0 has not been measured yet`() {
        assertEquals(220.dp, coveragePageHeight(0f))
    }

    @Test
    fun `uses the measured page 0 height once available`() {
        assertEquals(180.dp, coveragePageHeight(180f))
    }

    @Test
    fun `treats a negative measured height the same as unmeasured`() {
        assertEquals(220.dp, coveragePageHeight(-5f))
    }
}
