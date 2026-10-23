package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailyTargetHistoryTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val water = Habit(name = "Water", emoji = "💧", type = HabitType.BUILD, createdOn = today.minusDays(30), dailyTarget = 8)

    @Test fun changingTheGoalKeepsTheOldOneForEarlierDays() {
        val changed = water.withDailyTarget(10, today)
        assertEquals(listOf(PastDailyTarget(today.minusDays(1), 8)), changed.pastDailyTargets)
        assertEquals(8, changed.dailyTargetOn(today.minusDays(1)))
        assertEquals(8, changed.dailyTargetOn(today.minusDays(29)))
        assertEquals(10, changed.dailyTargetOn(today))
    }

    @Test fun eachChangeKeepsItsOwnStretch() {
        val changed = water.withDailyTarget(10, today.minusDays(10)).withDailyTarget(6, today)
        assertEquals(8, changed.dailyTargetOn(today.minusDays(11)))
        assertEquals(10, changed.dailyTargetOn(today.minusDays(10)))
        assertEquals(10, changed.dailyTargetOn(today.minusDays(1)))
        assertEquals(6, changed.dailyTargetOn(today))
    }

    @Test fun changingItTwiceInOneDayKeepsTheFirstOldGoal() {
        val changed = water.withDailyTarget(10, today).withDailyTarget(12, today)
        assertEquals(listOf(PastDailyTarget(today.minusDays(1), 8)), changed.pastDailyTargets)
        assertEquals(12, changed.dailyTargetOn(today))
    }

    @Test fun noHistoryWhenNothingChangesOrThereWasNoGoal() {
        assertSame(water, water.withDailyTarget(8, today))
        val ticked = water.copy(dailyTarget = null).withDailyTarget(8, today)
        assertTrue(ticked.pastDailyTargets.isEmpty())
        val startedToday = water.copy(createdOn = today).withDailyTarget(10, today)
        assertTrue(startedToday.pastDailyTargets.isEmpty())
    }

    @Test fun goingBackToASingleTickStillRemembersTheGoal() {
        val changed = water.withDailyTarget(null, today)
        assertEquals(listOf(PastDailyTarget(today.minusDays(1), 8)), changed.pastDailyTargets)
    }

    @Test fun anOldDayTickedDoneCountsAsItsOwnGoal() {
        val item = HabitWithEntries(water.withDailyTarget(10, today), entries = setOf(today.minusDays(3)))
        assertEquals(8, item.countOn(today.minusDays(3)))
    }

    @Test fun backupsKeepOldGoals() {
        val habit = water.copy(id = 1).withDailyTarget(10, today)
        val text = Backup.export(listOf(HabitWithEntries(habit, emptySet())), true, LocalDateTime.of(2026, 10, 23, 9, 0))
        assertEquals(habit.pastDailyTargets, Backup.parse(text).habits.single().habit.pastDailyTargets)
    }

    @Test fun backupsDropGoalsThatCantBeRight() {
        val exported = Backup.export(listOf(HabitWithEntries(water.copy(id = 1), emptySet())), true, LocalDateTime.of(2026, 10, 23, 9, 0))
        assertTrue(exported.contains("\"pastDailyTargets\": []"))
        val text = exported
            .replace("\"pastDailyTargets\": []", "\"pastDailyTargets\": [{\"until\": \"2026-10-01\", \"target\": 500}, {\"until\": \"2026-10-05\", \"target\": 4}]")
        assertEquals(listOf(PastDailyTarget(LocalDate.of(2026, 10, 5), 4)), Backup.parse(text).habits.single().habit.pastDailyTargets)
    }
}
