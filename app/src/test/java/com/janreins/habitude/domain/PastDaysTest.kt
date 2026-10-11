package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PastDaysTest {
    private val today = LocalDate.of(2025, 10, 8)

    @Test fun earliestIsSevenDaysBack() { assertEquals(today.minusDays(7), PastDays.earliest(today)) }
    @Test fun clampNeverGoesIntoTheFuture() { assertEquals(today, PastDays.clamp(today.plusDays(1), today)) }
    @Test fun clampStopsAtSevenDaysBack() {
        assertEquals(today.minusDays(7), PastDays.clamp(today.minusDays(8), today))
        assertEquals(today.minusDays(3), PastDays.clamp(today.minusDays(3), today))
    }
    @Test fun labelsTodayYesterdayAndDates() {
        assertEquals("Today", PastDays.label(today, today))
        assertEquals("Yesterday", PastDays.label(today.minusDays(1), today))
        assertEquals("Mon 6 Oct", PastDays.label(today.minusDays(2), today))
    }
}
