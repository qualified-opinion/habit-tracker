package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CountAndMilestoneTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun water(entries: Set<LocalDate> = emptySet(), counts: Map<LocalDate, Int> = emptyMap()) =
        HabitWithEntries(
            Habit(1, "Water", "💧", HabitType.BUILD, createdOn = today.minusDays(60), dailyTarget = 8),
            entries,
            counts,
        )

    private fun daily(streak: Int, id: Long = 1, type: HabitType = HabitType.BUILD): HabitWithEntries {
        val start = today.minusDays(streak.toLong() - 1)
        return when (type) {
            HabitType.BUILD -> HabitWithEntries(
                Habit(id, "Read", "📚", type, createdOn = today.minusDays(400)),
                (0 until streak).map { start.plusDays(it.toLong()) }.toSet(),
            )
            // Days clean counts whole days since the start, so a 30-day run started 30 days ago.
            HabitType.BREAK -> HabitWithEntries(Habit(id, "Smoking", "🚬", type, createdOn = today.minusDays(streak.toLong())), emptySet())
        }
    }

    @Test
    fun countsAddUpAndNeverGoBelowZero() {
        assertEquals(1, Counts.next(stored = null, ticked = false, target = 8, delta = 1))
        assertEquals(4, Counts.next(stored = 3, ticked = false, target = 8, delta = 1))
        assertEquals(0, Counts.next(stored = 0, ticked = false, target = 8, delta = -1))
        assertEquals(Counts.MAX_COUNT, Counts.next(stored = Counts.MAX_COUNT, ticked = true, target = 8, delta = 1))
    }

    @Test
    fun aDayTickedBeforeCountingStartsAtTheGoal() {
        // Ticked done as a plain habit, then turned into a count habit: it shouldn't drop to 0.
        assertEquals(8, Counts.current(stored = null, ticked = true, target = 8))
        assertEquals(7, Counts.next(stored = null, ticked = true, target = 8, delta = -1))
        val item = water(entries = setOf(today.minusDays(1)), counts = mapOf(today to 3))
        assertEquals(8, item.countOn(today.minusDays(1)))
        assertEquals(3, item.countOn(today))
        assertEquals(0, item.countOn(today.minusDays(2)))
    }

    @Test
    fun theDayIsDoneAtTheGoal() {
        assertFalse(Counts.isDone(7, 8))
        assertTrue(Counts.isDone(8, 8))
        assertTrue(Counts.isDone(9, 8))
    }

    @Test
    fun reminderSaysHowFarAlongTheDayIs() {
        val text = Reminders.reminderFor(water(counts = mapOf(today to 3)), today)
        assertEquals("3 of 8 so far today. A good day to start a streak.", text?.body)
    }

    @Test
    fun backupKeepsCountsAndTheDailyGoal() {
        val item = water(entries = setOf(today.minusDays(1)), counts = mapOf(today.minusDays(1) to 9, today to 3))
        val back = Backup.parse(Backup.export(listOf(item), eveningNudge = true, now = LocalDateTime.of(2026, 10, 8, 9, 0)))
        assertEquals(item, back.habits.single())
    }

    @Test
    fun backupDropsADailyGoalOnABreakHabit() {
        val json = """
            {"app":"Habitude","format":1,"exportedAt":"2026-10-08T09:00","habits":[
              {"id":1,"name":"Smoking","emoji":"🚬","type":"BREAK","days":[1,2,3,4,5,6,7],
               "createdOn":"2026-09-01","entries":[],"dailyTarget":5,"counts":{"2026-10-01":-2}}
            ]}
        """.trimIndent()
        val habit = Backup.parse(json).habits.single()
        assertNull(habit.habit.dailyTarget)
        assertTrue(habit.counts.isEmpty())
    }

    @Test
    fun milestoneIsTheHighestStepReached() {
        assertEquals(0, Milestones.reached(Milestones.DAYS, 6))
        assertEquals(7, Milestones.reached(Milestones.DAYS, 7))
        assertEquals(30, Milestones.reached(Milestones.DAYS, 99))
        assertEquals(365, Milestones.reached(Milestones.DAYS, 1000))
    }

    @Test
    fun eachMilestoneIsCelebratedOncePerRun() {
        assertEquals(7 to 7, Milestones.check(Milestones.DAYS, current = 7, celebrated = 0))
        assertEquals(null to 7, Milestones.check(Milestones.DAYS, current = 8, celebrated = 7))
        assertEquals(30 to 30, Milestones.check(Milestones.DAYS, current = 30, celebrated = 7))
    }

    @Test
    fun aBrokenRunCanCelebrateAgain() {
        // The streak broke: what's remembered drops back, so 7 days can be celebrated again.
        assertEquals(null to 0, Milestones.check(Milestones.DAYS, current = 2, celebrated = 30))
        assertEquals(7 to 7, Milestones.check(Milestones.DAYS, current = 7, celebrated = 0))
    }

    @Test
    fun reviewCelebratesStreaksAndCleanRuns() {
        val items = listOf(daily(7, id = 1), daily(30, id = 2, type = HabitType.BREAK), daily(5, id = 3))
        val (found, remember) = Milestones.review(items, today, celebrated = { 0 })
        assertEquals(listOf(1L, 2L), found.map { it.habitId })
        assertEquals("A 7-day streak!", found[0].title)
        assertEquals("30 days clean!", found[1].title)
        assertEquals(mapOf(1L to 7, 2L to 30, 3L to 0), remember)
    }

    @Test
    fun firstReviewOnlyTakesNote() {
        val (found, remember) = Milestones.review(listOf(daily(45)), today, celebrated = { 0 }, quiet = true)
        assertTrue(found.isEmpty())
        assertEquals(mapOf(1L to 30), remember)
    }

    @Test
    fun weeklyGoalsCountMilestonesInWeeks() {
        val habit = Habit(1, "Gym", "🏃", HabitType.BUILD, createdOn = today.minusWeeks(10), weeklyTarget = 1)
        val mondays = (1..4).map { Stats.weekStart(today).minusWeeks(it.toLong()) }.toSet()
        val (found, _) = Milestones.review(listOf(HabitWithEntries(habit, mondays)), today, celebrated = { 0 })
        assertEquals("4 weeks in a row!", found.single().title)
    }

    @Test
    fun archivedHabitsAreLeftAlone() {
        val item = daily(7).let { it.copy(habit = it.habit.copy(archived = true)) }
        val (found, remember) = Milestones.review(listOf(item), today, celebrated = { 0 })
        assertTrue(found.isEmpty())
        assertTrue(remember.isEmpty())
    }
    @Test fun pastDoneDayStaysDoneWhenCountGoesUpUnderAHigherGoal() {
        assertTrue(Counts.doneAfterEdit(true, 9, 10, true))
    }
    @Test fun pastDoneDayStaysDoneWhenCountGoesDownButNotToZero() {
        assertTrue(Counts.doneAfterEdit(true, 1, 10, true))
    }
    @Test fun pastDoneDayClearsAtZero() { assertFalse(Counts.doneAfterEdit(true, 0, 10, true)) }
    @Test fun pastNotDoneDayBecomesDoneAtCurrentGoal() {
        assertFalse(Counts.doneAfterEdit(false, 9, 10, true))
        assertTrue(Counts.doneAfterEdit(false, 10, 10, true))
    }
    @Test fun todayStillFollowsTheCurrentGoal() {
        assertFalse(Counts.doneAfterEdit(true, 9, 10, false))
        assertTrue(Counts.doneAfterEdit(false, 10, 10, false))
    }
}
