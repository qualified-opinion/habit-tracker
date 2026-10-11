package com.janreins.habitude.domain

/** Counting a habit up through the day, like glasses of water. */
object Counts {
    const val MAX_TARGET = 99

    /** The most a day can be counted to, so a stuck tap can't run away. */
    const val MAX_COUNT = 999

    /**
     * The count for a day from what's stored: the saved count if there is one, otherwise the
     * goal if the day was ticked done (say, before it became a count habit), otherwise 0.
     */
    fun current(stored: Int?, ticked: Boolean, target: Int): Int = stored ?: if (ticked) target else 0

    /** The count after adding [delta] (negative to take one off), never below 0. */
    fun next(stored: Int?, ticked: Boolean, target: Int, delta: Int): Int =
        (current(stored, ticked, target) + delta).coerceIn(0, MAX_COUNT)

    /** Past done days stay done until explicitly cleared; today follows the current goal. */
    fun doneAfterEdit(wasDone: Boolean, newCount: Int, target: Int, isPastDay: Boolean): Boolean =
        if (isPastDay && wasDone) newCount > 0 else newCount >= target

    /** Whether [count] reaches the day's goal. */
    fun isDone(count: Int, target: Int): Boolean = count >= target
}
