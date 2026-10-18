package com.janreins.habitude.ui.edit

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.data.HabitudeDatabase
import com.janreins.habitude.data.pastDaysDatabase
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.notify.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/*
 * The view model runs on the real (paused) main looper rather than a test Main dispatcher:
 * replacing Dispatchers.Main here leaks into the Compose tests that run after this class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EditHabitViewModelTest {
    private val store = ViewModelStore()
    private lateinit var db: HabitudeDatabase
    private lateinit var repository: HabitRepository
    private lateinit var reminders: ReminderScheduler

    @Before fun setUp() {
        db = pastDaysDatabase()
        repository = HabitRepository(db)
        reminders = ReminderScheduler(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() { store.clear(); db.close() }

    private fun viewModel(id: Long = 0L) =
        EditHabitViewModel(repository, reminders, SavedStateHandle(mapOf(EditHabitViewModel.ARG_ID to id)))
            .also { store.put("edit-$id", it) }

    /** Runs the main looper until [done], as Room hands results back to it from its own threads. */
    private fun awaitMain(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        // Give a second, wrongly started save the same chance to finish.
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
    }

    @Test fun doubleTapOnSaveAddsTheHabitOnce() {
        val vm = viewModel()
        vm.setName("Read")
        var calls = 0
        repeat(2) { vm.save({ calls++ }) }
        awaitMain { calls > 0 }
        assertEquals(1, calls)
        assertEquals(listOf("Read"), runBlocking { repository.snapshot() }.map { it.habit.name })
        assertTrue(vm.busy)
    }

    @Test fun doubleTapOnDeleteLeavesOnce() {
        val id = runBlocking {
            repository.save(Habit(name = "Read", emoji = "📚", type = HabitType.BUILD, createdOn = LocalDate.now()))
        }
        val vm = viewModel(id)
        var calls = 0
        repeat(2) { vm.delete { calls++ } }
        awaitMain { calls > 0 }
        assertEquals(1, calls)
        assertTrue(runBlocking { repository.snapshot() }.isEmpty())
    }

    @Test fun archiveAfterSaveIsIgnored() {
        val vm = viewModel()
        vm.setName("Read")
        var calls = 0
        vm.save({ calls++ })
        vm.save({ calls++ }, archived = true)
        awaitMain { calls > 0 }
        assertEquals(1, calls)
        assertEquals(listOf(false), runBlocking { repository.snapshot() }.map { it.habit.archived })
    }
}
