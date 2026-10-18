package com.janreins.habitude.ui.edit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.data.HabitudeDatabase
import com.janreins.habitude.data.pastDaysDatabase
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.notify.ReminderScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EditHabitViewModelTest {
    private val store = ViewModelStore()
    private lateinit var db: HabitudeDatabase
    private lateinit var repository: HabitRepository
    private lateinit var reminders: ReminderScheduler

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = pastDaysDatabase()
        repository = HabitRepository(db)
        reminders = ReminderScheduler(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() { store.clear(); db.close(); Dispatchers.resetMain() }

    private fun viewModel(id: Long = 0L) =
        EditHabitViewModel(repository, reminders, SavedStateHandle(mapOf(EditHabitViewModel.ARG_ID to id)))
            .also { store.put("edit-$id", it) }

    @Test fun doubleTapOnSaveAddsTheHabitOnce() = runBlocking {
        val vm = viewModel()
        vm.setName("Read")
        var calls = 0
        val saved = CompletableDeferred<Unit>()
        repeat(2) { vm.save({ calls++; saved.complete(Unit) }) }
        withTimeout(5_000) { saved.await() }
        delay(200)
        assertEquals(1, calls)
        assertEquals(listOf("Read"), repository.snapshot().map { it.habit.name })
        assertTrue(vm.busy)
    }

    @Test fun doubleTapOnDeleteLeavesOnce() = runBlocking {
        val id = repository.save(Habit(name = "Read", emoji = "📚", type = HabitType.BUILD, createdOn = LocalDate.now()))
        val vm = viewModel(id)
        var calls = 0
        val deleted = CompletableDeferred<Unit>()
        repeat(2) { vm.delete { calls++; deleted.complete(Unit) } }
        withTimeout(5_000) { deleted.await() }
        delay(200)
        assertEquals(1, calls)
        assertTrue(repository.snapshot().isEmpty())
    }

    @Test fun archiveAfterSaveIsIgnored() = runBlocking {
        val vm = viewModel()
        vm.setName("Read")
        val saved = CompletableDeferred<Unit>()
        vm.save({ saved.complete(Unit) })
        vm.save({}, archived = true)
        withTimeout(5_000) { saved.await() }
        delay(200)
        assertEquals(listOf(false), repository.snapshot().map { it.habit.archived })
    }
}
