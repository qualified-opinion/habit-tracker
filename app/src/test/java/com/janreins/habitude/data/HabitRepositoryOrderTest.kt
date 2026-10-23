package com.janreins.habitude.data

import android.app.Application
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HabitRepositoryOrderTest {
    private class FakeOrder : HabitOrderStore {
        override val order = MutableStateFlow<List<Long>>(emptyList())
        override fun setOrder(ids: List<Long>) { order.value = ids }
    }

    private lateinit var db: HabitudeDatabase
    private val store = FakeOrder()
    private lateinit var repository: HabitRepository
    private val day = LocalDate.of(2026, 10, 23)
    private fun habit(name: String) = Habit(name = name, emoji = "🌱", type = HabitType.BUILD, createdOn = day)

    @Before fun setUp() { db = pastDaysDatabase(); repository = HabitRepository(db, store) }
    @After fun tearDown() { db.close() }

    private fun names() = runBlocking { repository.snapshot() }.map { it.habit.name }

    @Test fun newHabitsGoLastAndMovingChangesTheOrder() = runBlocking {
        val ids = listOf("Read", "Walk", "Water").map { repository.save(habit(it)) }
        assertEquals(listOf("Read", "Walk", "Water"), names())
        repository.move(ids[2], -1, ids)
        repository.move(ids[2], -1, ids.let { listOf(it[0], it[2], it[1]) })
        assertEquals(listOf("Water", "Read", "Walk"), names())
        repository.save(habit("Stretch"))
        assertEquals(listOf("Water", "Read", "Walk", "Stretch"), names())
    }

    @Test fun importKeepsTheBackupsOrder() = runBlocking {
        val items = listOf(5L, 2L, 9L).map { id -> HabitWithEntries(habit("Habit $id").copy(id = id), emptySet()) }
        repository.replaceAll(items)
        assertEquals(listOf(5L, 2L, 9L), repository.snapshot().map { it.habit.id })
    }
}
