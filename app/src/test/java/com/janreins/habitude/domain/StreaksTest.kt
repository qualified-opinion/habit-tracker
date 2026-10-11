package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class StreaksTest {
    private val everyDay = DayOfWeek.entries.toSet()

    // 2026-10-05 is a Monday
    private val mon = LocalDate.of(2026, 10, 5)
    private fun day(offset: Long) = mon.plusDays(offset)

    @Test
    fun noEntriesMeansNoStreak() {
        assertEquals(0, Streaks.currentBuildStreak(emptySet(), everyDay, mon))
        assertEquals(0, Streaks.bestBuildStreak(emptySet(), everyDay, mon))
    }

    @Test
    fun todayNotDoneYetKeepsYesterdaysStreak() {
        val done = setOf(day(0), day(1), day(2))
        assertEquals(3, Streaks.currentBuildStreak(done, everyDay, day(3)))
        assertEquals(4, Streaks.currentBuildStreak(done + day(3), everyDay, day(3)))
    }

    @Test
    fun missedScheduledDayEndsStreak() {
        val done = setOf(day(0), day(1), day(3))
        assertEquals(1, Streaks.currentBuildStreak(done, everyDay, day(3)))
        assertEquals(2, Streaks.bestBuildStreak(done, everyDay, day(3)))
    }

    @Test
    fun restDaysAreSkipped() {
        val schedule = setOf(MONDAY, WEDNESDAY, FRIDAY)
        val done = setOf(day(0), day(2), day(4)) // Mon, Wed, Fri
        assertEquals(3, Streaks.currentBuildStreak(done, schedule, day(6))) // Sunday
        assertEquals(3, Streaks.bestBuildStreak(done, schedule, day(6)))
    }

    @Test
    fun doingItOnARestDayCountsAsABonus() {
        val schedule = setOf(MONDAY, WEDNESDAY)
        val done = setOf(day(0), day(1), day(2)) // Mon, Tue (rest), Wed
        assertEquals(3, Streaks.currentBuildStreak(done, schedule, day(2)))
    }

    @Test
    fun streakFromLongAgoIsZeroAfterAMiss() {
        val done = setOf(day(0), day(1))
        assertEquals(0, Streaks.currentBuildStreak(done, everyDay, day(5)))
        assertEquals(2, Streaks.bestBuildStreak(done, everyDay, day(5)))
    }

    @Test
    fun daysCleanCountsFromStartWithNoSlips() {
        assertEquals(0, Streaks.daysClean(emptySet(), mon, mon))
        assertEquals(10, Streaks.daysClean(emptySet(), mon, day(10)))
    }

    @Test
    fun daysCleanResetsOnSlip() {
        assertEquals(0, Streaks.daysClean(setOf(day(4)), mon, day(4)))
        assertEquals(3, Streaks.daysClean(setOf(day(4)), mon, day(7)))
    }

    @Test
    fun bestCleanRunLooksAcrossSlips() {
        // Started Mon; slipped on day 6 and day 8; today is day 10.
        val slips = setOf(day(6), day(8))
        assertEquals(2, Streaks.daysClean(slips, mon, day(10)))
        assertEquals(5, Streaks.bestCleanRun(slips, mon, day(10)))
    }

    @Test
    fun bestCleanRunIncludesCurrentRun() {
        assertEquals(9, Streaks.bestCleanRun(setOf(day(1)), mon, day(10)))
    }

    @Test
    fun scheduleMaskRoundTrips() {
        val days = setOf(MONDAY, WEDNESDAY, FRIDAY)
        assertEquals(days, Schedule.fromMask(Schedule.toMask(days)))
        assertEquals(everyDay, Schedule.fromMask(0b1111111))
    }
    @Test
    fun backdatedTickOnYesterdayExtendsCurrentStreak() {
        val done = setOf(day(0), day(1))
        assertEquals(0, Streaks.currentBuildStreak(done, everyDay, day(3)))
        assertEquals(3, Streaks.currentBuildStreak(done + day(2), everyDay, day(3)))
    }

    @Test
    fun backdatedTickFillingAGapJoinsTwoRuns() {
        val done = setOf(day(0), day(1), day(3), day(4))
        assertEquals(2, Streaks.currentBuildStreak(done, everyDay, day(5)))
        assertEquals(5, Streaks.currentBuildStreak(done + day(2), everyDay, day(5)))
        assertEquals(5, Streaks.bestBuildStreak(done + day(2), everyDay, day(5)))
    }
}
