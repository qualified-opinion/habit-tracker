package com.janreins.habitude.ui.today

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.data.HabitudeDatabase
import com.janreins.habitude.data.pastDaysDatabase
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodayViewModelPastDaysTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val past = today.minusDays(1)
    private val clock = MutableStateFlow(today)
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: HabitudeDatabase
    private lateinit var repository: HabitRepository
    private lateinit var vm: TodayViewModel
    private val habit = Habit(name = "Read", emoji = "📚", type = HabitType.BUILD, createdOn = today.minusDays(30))

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = pastDaysDatabase()
        repository = HabitRepository(db)
        vm = TodayViewModel(repository, clock)
        store.put("today", vm)
    }
    @After fun tearDown() { store.clear(); db.close(); Dispatchers.resetMain() }

    private suspend fun TestScope.observe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.first { !it.loading }
        assertFalse(vm.state.value.loading)
    }

    @Test fun cannotGoBeforeSevenDaysOrAfterToday() = runTest(dispatcher) {
        observe()
        repeat(20) { vm.previousDay() }; runCurrent()
        assertEquals(today.minusDays(7), vm.state.value.day)
        assertFalse(vm.state.value.canGoBack)
        repeat(20) { vm.nextDay() }; runCurrent()
        assertEquals(today, vm.state.value.day)
        assertFalse(vm.state.value.canGoForward)
    }
    @Test fun tickOnPastDayWritesThatDayNotToday() = runTest(dispatcher) {
        val id = repository.save(habit)
        observe(); vm.previousDay(); runCurrent()
        val renderedDay = vm.state.value.building.single().day
        vm.backToToday()
        vm.setLogged(id, renderedDay, true); runCurrent()
        assertEquals(setOf(past), repository.habits.first { it.single().entries == setOf(past) }.single().entries)
        assertFalse(today in repository.snapshot().single().entries)
    }
    @Test fun backdatedTickExtendsStreakShownOnCard() = runTest(dispatcher) {
        val id = repository.save(habit)
        repository.setEntry(id, today.minusDays(2), true)
        observe(); vm.previousDay(); runCurrent()
        assertEquals(0, vm.state.value.building.single().current)
        vm.setLogged(id, past, true)
        vm.state.first { it.building.single().current == 2 }
        assertEquals(2, vm.state.value.building.single().current)
        assertEquals(2, vm.state.value.building.single().best)
    }
    @Test fun restDayCardShowsNotDueOnPastDay() = runTest(dispatcher) {
        repository.save(habit.copy(schedule = setOf(DayOfWeek.THURSDAY)))
        observe(); vm.previousDay(); runCurrent()
        assertFalse(vm.state.value.building.single().dueOnDay)
    }
    @Test fun weeklyGoalCardOnPastDay() = runTest(dispatcher) {
        val id = repository.save(habit.copy(weeklyTarget = 2))
        repository.setEntry(id, past, true); repository.setEntry(id, today, true)
        observe(); repeat(3) { vm.previousDay() }; runCurrent()
        val card = vm.state.value.building.single()
        assertFalse(card.dueOnDay)
        assertEquals(2, card.doneThisWeek)
        assertEquals(1, card.current)
    }
    @Test fun selectionResetsToTodayAfterMidnight() = runTest(dispatcher) {
        observe(); vm.previousDay(); runCurrent()
        clock.value = today.plusDays(1); runCurrent()
        assertEquals(today.plusDays(1), vm.state.value.day)
        assertTrue(vm.state.value.isToday)
    }
    @Test fun backToTodayResetsSelection() = runTest(dispatcher) {
        observe(); vm.previousDay(); runCurrent()
        vm.backToToday(); runCurrent()
        assertEquals(today, vm.state.value.day)
    }
    @Test fun outOfRangeDayWriteIsIgnored() = runTest(dispatcher) {
        val id = repository.save(habit.copy(dailyTarget = 8))
        observe()
        for (day in listOf(today.minusDays(8), today.plusDays(1))) {
            vm.setLogged(id, day, true); vm.logSlip(id, day, "note"); vm.addCount(id, day, 8)
        }
        runCurrent()
        val item = repository.snapshot().single()
        assertTrue(item.entries.isEmpty()); assertTrue(item.counts.isEmpty()); assertTrue(item.notes.isEmpty())
    }
    @Test fun countOnPastDayUsesStopgap() = runTest(dispatcher) {
        val id = repository.save(habit.copy(dailyTarget = 8))
        repository.addCount(id, past, 8, today)
        repository.save(habit.copy(id = id, dailyTarget = 10))
        observe(); vm.previousDay(); runCurrent()
        vm.addCount(id, past, 1); runCurrent()
        val card = vm.state.first { it.building.single().countOnDay == 9 }.building.single()
        assertEquals(9, card.countOnDay); assertTrue(card.loggedOnDay)
        assertFalse(today in repository.snapshot().single().counts)
    }
    @Test fun queuedTickAcrossMidnightKeepsRenderedDay() = runTest(dispatcher) {
        val id = repository.save(habit)
        observe()
        clock.value = today.plusDays(1)
        vm.setLogged(id, today, true); runCurrent()
        assertEquals(setOf(today), repository.habits.first { it.single().entries == setOf(today) }.single().entries)
    }
    @Test fun oldestRenderedDayAcrossMidnightIsDropped() = runTest(dispatcher) {
        val id = repository.save(habit)
        observe()
        clock.value = today.plusDays(1); runCurrent()
        vm.setLogged(id, today.minusDays(7), true); runCurrent()
        assertTrue(repository.snapshot().single().entries.isEmpty())
    }
    @Test fun slipAndNoteWriteOnlyRenderedDay() = runTest(dispatcher) {
        val id = repository.save(habit.copy(type = HabitType.BREAK))
        observe(); vm.previousDay(); runCurrent()
        vm.backToToday(); vm.logSlip(id, past, "Trigger"); runCurrent()
        val item = repository.habits.first { it.single().notes[past] == "Trigger" }.single()
        assertEquals(setOf(past), item.entries); assertEquals(mapOf(past to "Trigger"), item.notes)
    }
}
