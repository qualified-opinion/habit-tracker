package com.janreins.habitude.domain

import java.time.LocalDate

/** How far through a day's build habits you are. */
data class DayProgress(val done: Int, val due: Int)

/** What the Today tab shows for a day, and in what order. */
object TodayList {

    /** Not archived, and already started by [day] (a habit doesn't show on days before it began). */
    fun visibleOn(item: HabitWithEntries, day: LocalDate): Boolean =
        !item.habit.archived && !day.isBefore(item.habit.createdOn)

    fun progress(items: List<HabitWithEntries>, today: LocalDate): DayProgress = progress(items, today, today)

    /** Build habits due on [day] (or done anyway), and how many of those are done. */
    fun progress(items: List<HabitWithEntries>, day: LocalDate, today: LocalDate): DayProgress {
        val builds = items.filter { visibleOn(it, day) && it.habit.type == HabitType.BUILD }
        val due = builds.filter { isDueOn(it, day, today) || day in it.entries }
        return DayProgress(done = due.count { day in it.entries }, due = due.size)
    }

    fun order(items: List<HabitWithEntries>, today: LocalDate): List<HabitWithEntries> = order(items, today, today)

    /**
     * Habits shown on [day]: build habits still to do first, then the ones done, then rest
     * days, each group keeping its usual order. Break habits keep their order.
     */
    fun order(items: List<HabitWithEntries>, day: LocalDate, today: LocalDate): List<HabitWithEntries> =
        items.filter { visibleOn(it, day) }.sortedBy { item ->
            when {
                item.habit.type == HabitType.BREAK -> 0
                day in item.entries -> 1
                isDueOn(item, day, today) -> 0
                else -> 2
            }
        }

    fun isDueToday(item: HabitWithEntries, today: LocalDate): Boolean = isDueOn(item, today, today)

    /**
     * Whether a build habit wants doing on [day]: due on that day's schedule, or for a
     * times-a-week habit, that week's goal (counting every done day up to [today]) isn't met.
     */
    fun isDueOn(item: HabitWithEntries, day: LocalDate, today: LocalDate): Boolean {
        val target = item.habit.weeklyTarget
        return if (item.habit.isWeekly && target != null) {
            Streaks.doneInWeek(item.entries, day, today) < target
        } else {
            item.habit.isDue(day)
        }
    }
}
