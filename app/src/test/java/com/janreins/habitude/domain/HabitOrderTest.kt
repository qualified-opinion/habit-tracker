package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitOrderTest {
    private val day = LocalDate.of(2026, 10, 18)
    private fun item(id: Long) = HabitWithEntries(Habit(id, "Habit $id", "🌱", HabitType.BUILD, createdOn = day), emptySet())
    private val items = listOf(item(1), item(2), item(3), item(4))

    @Test fun withoutAnOrderHabitsKeepTheOrderTheyWereMade() {
        assertEquals(listOf(1L, 2, 3, 4), HabitOrder.sort(items.reversed(), emptyList()).map { it.habit.id })
    }

    @Test fun followsTheSavedOrderAndPutsNewHabitsLast() {
        assertEquals(listOf(3L, 1, 2, 4), HabitOrder.sort(items, listOf(3, 1)).map { it.habit.id })
    }

    @Test fun ignoresIdsOfDeletedHabits() {
        assertEquals(listOf(2L, 1, 3, 4), HabitOrder.sort(items, listOf(9, 2, 8, 1)).map { it.habit.id })
    }

    @Test fun movingSwapsWithTheNeighbourShownNextToIt() {
        val all = listOf(1L, 2, 3, 4)
        assertEquals(listOf(2L, 1, 3, 4), HabitOrder.move(all, all, 2, -1))
        assertEquals(listOf(1L, 3, 2, 4), HabitOrder.move(all, all, 2, 1))
        // 2 is archived, so 3 moves past it and swaps with 1; 2 keeps its spot.
        assertEquals(listOf(3L, 2, 1, 4), HabitOrder.move(all, listOf(1, 3, 4), 3, -1))
    }

    @Test fun movingPastEitherEndChangesNothing() {
        val all = listOf(1L, 2, 3)
        assertEquals(all, HabitOrder.move(all, all, 1, -1))
        assertEquals(all, HabitOrder.move(all, all, 3, 1))
        assertEquals(all, HabitOrder.move(all, all, 7, 1))
    }

    @Test fun encodesAndDecodesTheList() {
        assertEquals(listOf(3L, 1, 2), HabitOrder.decode(HabitOrder.encode(listOf(3, 1, 2))))
        assertEquals(emptyList<Long>(), HabitOrder.decode(null))
        assertEquals(listOf(5L), HabitOrder.decode("x,5,"))
    }
}
