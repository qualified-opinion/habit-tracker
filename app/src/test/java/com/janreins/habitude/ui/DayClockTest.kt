package com.janreins.habitude.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    @After fun backToNow() = DayClock.refresh()

    @Test fun todayMovesOnOnceAfterMidnight() = runTest {
        val day = LocalDate.of(2026, 10, 23)
        DayClock.refresh(day.atTime(23, 59))
        val seen = mutableListOf<LocalDate>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { DayClock.today.collect { seen += it } }

        DayClock.refresh(day.atTime(23, 59, 59))
        assertEquals(listOf(day), seen)

        DayClock.refresh(day.plusDays(1).atStartOfDay().plusSeconds(1))
        DayClock.refresh(LocalDateTime.of(day.plusDays(1), LocalTime.of(8, 0)))
        assertEquals(listOf(day, day.plusDays(1)), seen)
        assertEquals(day.plusDays(1), DayClock.today())
        job.cancel()
    }
}
