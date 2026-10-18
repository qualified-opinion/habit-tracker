package com.janreins.habitude.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderText(val title: String, val body: String)

/** What reminders say and when they fire. No Android here, so it can be unit tested. */
object Reminders {

    /** Default time of the evening check for streaks about to break, in minutes after midnight (8pm). */
    const val NUDGE_MINUTES = 20 * 60

    /** Whether [minutes] is a time of day, 00:00 to 23:59. */
    fun isValidMinutes(minutes: Int): Boolean = minutes in 0 until 24 * 60

    /** The next moment a daily reminder set for [minutes] after midnight should fire. */
    fun nextTrigger(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** The reminder for one habit today, or null if there's nothing to remind about. */
    fun reminderFor(item: HabitWithEntries, today: LocalDate): ReminderText? {
        val habit = item.habit
        if (habit.archived || today in item.entries) return null
        val title = "${habit.emoji} ${habit.name}"
        val target = habit.weeklyTarget
        return when {
            habit.type == HabitType.BREAK -> {
                val clean = Streaks.daysClean(item.entries, habit.createdOn, today)
                val body = if (clean > 0) "${if (clean == 1) "1 day" else "$clean days"} clean. You've got this." else "Today's a clean slate. You've got this."
                ReminderText(title, body)
            }
            habit.isWeekly && target != null -> {
                val done = Streaks.doneInWeek(item.entries, today, today)
                if (done >= target) return null
                ReminderText(title, "$done of $target this week. Today's a good day for one more.")
            }
            else -> {
                if (!habit.isDue(today)) return null
                val streak = Streaks.currentBuildStreak(item.entries, habit::isDue, today)
                val body = if (streak > 0) "Keep your ${dayWord(streak)} streak going 🔥" else "A good day to start a streak."
                val counted = habit.dailyTarget?.let { "${item.countOn(today)} of $it so far today. " }.orEmpty()
                ReminderText(title, counted + body)
            }
        }
    }

    /**
     * Build habits not done today whose streak would break tonight: due today with a streak of
     * at least [minStreak] days, or a times-a-week goal that now needs every day left this week
     * (with at least one good week behind it). Streaks are in days, or weeks for weekly goals.
     */
    fun atRisk(items: List<HabitWithEntries>, today: LocalDate, minStreak: Int = 2): List<Pair<HabitWithEntries, Int>> =
        items
            .filter { !it.habit.archived && it.habit.type == HabitType.BUILD && today !in it.entries }
            .mapNotNull { item ->
                val habit = item.habit
                val target = habit.weeklyTarget
                if (habit.isWeekly && target != null) {
                    val needed = target - Streaks.doneInWeek(item.entries, today, today)
                    val daysLeft = 8 - today.dayOfWeek.value
                    val streak = Streaks.currentWeeklyStreak(item.entries, target, habit.createdOn, today)
                    (item to streak).takeIf { needed == daysLeft && streak >= 1 }
                } else {
                    val streak = Streaks.currentBuildStreak(item.entries, habit::isDue, today)
                    (item to streak).takeIf { habit.isDue(today) && streak >= minStreak }
                }
            }
            .sortedByDescending { (_, streak) -> streak }

    fun nudgeFor(items: List<HabitWithEntries>, today: LocalDate): ReminderText? {
        val risky = atRisk(items, today)
        return when (risky.size) {
            0 -> null
            1 -> {
                val (item, streak) = risky.first()
                val length = if (item.habit.isWeekly) weekWord(streak) else dayWord(streak)
                ReminderText(
                    "Your $length streak is at risk",
                    "${item.habit.emoji} ${item.habit.name} still needs a tick today.",
                )
            }
            else -> ReminderText(
                "${risky.size} streaks need you tonight",
                risky.joinToString(", ") { (item, streak) ->
                    "${item.habit.emoji} ${item.habit.name} ($streak${if (item.habit.isWeekly) " wk" else ""})"
                },
            )
        }
    }

    private fun dayWord(n: Int) = if (n == 1) "1-day" else "$n-day"

    private fun weekWord(n: Int) = if (n == 1) "1-week" else "$n-week"
}
