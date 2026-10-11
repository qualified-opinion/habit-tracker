package com.janreins.habitude.data

import android.app.Application
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HabitRepositoryPastDaysTest {
    private lateinit var db: HabitudeDatabase
    private lateinit var repository: HabitRepository
    private val today = LocalDate.of(2026, 10, 8)
    private val past = today.minusDays(1)
    private val habit = Habit(name = "Water", emoji = "💧", type = HabitType.BUILD, createdOn = today.minusDays(20), dailyTarget = 8)

    @Before fun setUp() { db = pastDaysDatabase(); repository = HabitRepository(db) }
    @After fun tearDown() { db.close() }

    @Test fun addCountOnPastDoneDayAfterGoalRaiseKeepsItDone() = runBlocking {
        val id = repository.save(habit)
        repository.addCount(id, past, 8, today)
        repository.save(habit.copy(id = id, dailyTarget = 10))
        repository.recheckCount(id, today)
        repository.addCount(id, past, 1, today)
        val item = repository.snapshot().single()
        assertEquals(9, item.countOn(past))
        assertEquals(setOf(past), item.entries)
        assertFalse(today in item.counts)
    }

    @Test fun addCountToZeroOnPastDayUnmarks() = runBlocking {
        val id = repository.save(habit)
        repository.addCount(id, past, 8, today)
        repository.addCount(id, past, -7, today)
        assertTrue(past in repository.snapshot().single().entries)
        repository.addCount(id, past, -1, today)
        val item = repository.snapshot().single()
        assertTrue(item.entries.isEmpty())
        assertTrue(item.counts.isEmpty())
    }

    @Test fun setEntryWritesOnlyTheGivenDay() = runBlocking {
        val id = repository.save(habit)
        repository.setEntry(id, past, true)
        assertEquals(setOf(past), repository.snapshot().single().entries)
        repository.setEntry(id, past, false)
        assertTrue(repository.snapshot().single().entries.isEmpty())
    }

    @Test fun notificationStyleDefaultTodayKeepsPastDayDone() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1)
        val id = repository.save(habit)
        repository.addCount(id, yesterday, 8)
        repository.save(habit.copy(id = id, dailyTarget = 10))
        repository.addCount(id, yesterday, 1)
        assertTrue(yesterday in repository.snapshot().single().entries)
    }
}
