package com.janreins.habitude.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TodayListPastDayTest {
    private val mon = LocalDate.of(2026, 10, 5)
    private val today = mon.plusDays(3)
    private fun item(id: Long = 1, type: HabitType = HabitType.BUILD) =
        HabitWithEntries(Habit(id, "Read", "📚", type, createdOn = mon.minusWeeks(1)), emptySet())

    @Test fun restDayIsNotDueOnPastDay() {
        val item = item().let { it.copy(habit = it.habit.copy(schedule = setOf(DayOfWeek.TUESDAY))) }
        assertFalse(TodayList.isDueOn(item, mon, today))
    }
    @Test fun pastScheduleAppliesOnPastDay() {
        val item = item().let { it.copy(habit = it.habit.copy(
            schedule = setOf(DayOfWeek.TUESDAY),
            pastSchedules = listOf(PastSchedule(mon, setOf(DayOfWeek.MONDAY))),
        )) }
        assertTrue(TodayList.isDueOn(item, mon, today))
        assertFalse(TodayList.isDueOn(item, today, today))
    }
    @Test fun habitStartedLaterIsHiddenOnEarlierDay() {
        val item = item().let { it.copy(habit = it.habit.copy(createdOn = mon.plusDays(1))) }
        assertFalse(TodayList.visibleOn(item, mon))
        assertTrue(TodayList.order(listOf(item), mon, today).isEmpty())
    }
    @Test fun breakHabitHiddenBeforeCleanSince() {
        val item = item(type = HabitType.BREAK).let { it.copy(habit = it.habit.copy(createdOn = mon.plusDays(1))) }
        assertFalse(TodayList.visibleOn(item, mon))
        assertTrue(TodayList.visibleOn(item, mon.plusDays(1)))
    }
    @Test fun weeklyGoalMetLaterInWeekIsNotDueOnEarlierDay() {
        val item = item().let { it.copy(habit = it.habit.copy(weeklyTarget = 2), entries = setOf(mon.plusDays(1), today)) }
        assertFalse(TodayList.isDueOn(item, mon, today))
        assertTrue(TodayList.isDueToday(item, mon))
    }
    @Test fun weeklyGoalNotMetIsDueOnPastDay() {
        val item = item().let { it.copy(habit = it.habit.copy(weeklyTarget = 3), entries = setOf(today, today.plusDays(1))) }
        assertTrue(TodayList.isDueOn(item, mon, today))
    }
    @Test fun progressCountsOnlyThatDaysDueHabits() {
        val done = item().copy(entries = setOf(mon))
        val rest = item(2).let { it.copy(habit = it.habit.copy(schedule = setOf(DayOfWeek.TUESDAY))) }
        val pending = item(3).copy(entries = setOf(today))
        val later = item(4).let { it.copy(habit = it.habit.copy(createdOn = today), entries = setOf(mon)) }
        val archived = item(5).let { it.copy(habit = it.habit.copy(archived = true)) }
        assertEquals(DayProgress(1, 2), TodayList.progress(listOf(done, rest, pending, later, archived, item(6, HabitType.BREAK)), mon, today))
    }
}
