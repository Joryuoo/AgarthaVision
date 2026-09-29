package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.Ratio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeBadgeTest {

    @Test
    fun `prev 0, cur 5 gives Change None`() {
        assertEquals(Change.None, countChange(5, 0))
    }

    @Test
    fun `0 over 0 gives Change None`() {
        assertEquals(Change.None, ratioChange(Ratio(0, 0), Ratio(0, 0)))
    }

    @Test
    fun `10 to 12 gives Change Count plus 2`() {
        assertEquals(Change.Count(2), countChange(12, 10))
    }

    @Test
    fun `ratio with a 0 denominator gives Change None`() {
        assertEquals(Change.None, ratioChange(Ratio(1, 10), Ratio(0, 0)))
        assertEquals(Change.None, ratioChange(Ratio(0, 0), Ratio(2, 10)))
    }

    @Test
    fun `20 percent to 17 percent gives Change Points minus 3`() {
        val prev = Ratio(20, 100)
        val cur = Ratio(17, 100)
        assertEquals(Change.Points(-3), ratioChange(cur, prev))
    }

    @Test
    fun `equal values give Change Flat`() {
        assertEquals(Change.Flat, countChange(10, 10))
        assertEquals(Change.Flat, ratioChange(Ratio(25, 100), Ratio(25, 100)))
    }

    @Test
    fun `badgeText formats period suffixes correctly`() {
        assertEquals("+8 day", Change.Count(8).badgeText(HomePeriod.TODAY))
        assertEquals("+8 wk", Change.Count(8).badgeText(HomePeriod.LAST_7_DAYS))
        assertEquals("+8 30 d", Change.Count(8).badgeText(HomePeriod.LAST_30_DAYS))
        assertEquals("−3 pts", Change.Points(-3).badgeText(HomePeriod.LAST_7_DAYS))
        assertEquals("", Change.None.badgeText(HomePeriod.LAST_7_DAYS))
        assertEquals("Flat", Change.Flat.badgeText(HomePeriod.LAST_7_DAYS))
    }

    @Test
    fun `spokenText formats accessible description`() {
        val up8 = Change.Count(8)
        assertEquals(
            "up 8 versus the previous 7 days",
            up8.spokenText(HomePeriod.LAST_7_DAYS),
        )

        val down3Pts = Change.Points(-3)
        assertEquals(
            "down 3 points versus the previous 7 days",
            down3Pts.spokenText(HomePeriod.LAST_7_DAYS),
        )
    }
}
