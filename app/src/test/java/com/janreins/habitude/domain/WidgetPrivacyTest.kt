package com.janreins.habitude.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPrivacyTest {
    private val habits = listOf(
        HabitWithEntries(
            Habit(name = "Private habit", emoji = "💧", type = HabitType.BUILD, createdOn = LocalDate.of(2026, 1, 1), dailyTarget = 8),
            setOf(LocalDate.of(2026, 1, 2)),
            counts = mapOf(LocalDate.of(2026, 1, 2) to 8),
        ),
    )

    @Test
    fun pinHidesAllHabitDataIncludingCountsAndProgress() {
        assertNull(widgetHabits(true, habits))
        assertNull(widgetHabits(true, emptyList()))
    }

    @Test
    fun withoutPinHabitsAndEmptyStateRemainAvailable() {
        assertEquals(habits, widgetHabits(false, habits))
        assertEquals(emptyList<HabitWithEntries>(), widgetHabits(false, emptyList()))
    }
}
