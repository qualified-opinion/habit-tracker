package com.janreins.habitude.ui.today

import android.app.Application
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.data.HabitudeDatabase
import com.janreins.habitude.data.pastDaysDatabase
import com.janreins.habitude.domain.DayProgress
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.ui.lock.LocalAppLocked
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodayScreenPastDaysTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: HabitudeDatabase
    private lateinit var vm: TodayViewModel
    private val store = ViewModelStore()
    private val today = LocalDate.of(2025, 10, 8)
    private val clock = MutableStateFlow(today)
    private val locked = mutableStateOf(false)

    @Before fun setUp() {
        db = pastDaysDatabase()
        val repo = HabitRepository(db)
        runBlocking { repo.save(Habit(name = "Read", emoji = "📚", type = HabitType.BUILD, createdOn = today.minusDays(30))) }
        vm = TodayViewModel(repo, clock); store.put("today", vm)
    }
    @After fun tearDown() { store.clear(); db.close() }
    private fun awaitState(predicate: (TodayUiState) -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            // ViewModel and Room post to Android's paused main looper independently of Compose.
            shadowOf(Looper.getMainLooper()).idle()
            predicate(vm.state.value)
        }
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLocked provides locked.value) {
                    TodayScreen({}, {}, vm)
                }
            }
        }
        awaitState { !it.loading }
    }

    @Test fun bannerAndBackToTodayShowOnPastDay() {
        render()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Viewing Tuesday, 7 October").assertIsDisplayed()
        compose.onNodeWithText("TUESDAY, 7 OCTOBER").assertIsDisplayed()
        compose.onNodeWithText("0 of 1 done on Tue 7 Oct").assertIsDisplayed()
        compose.onNodeWithText("Back to today").performClick()
        compose.onNodeWithText("Viewing Tuesday, 7 October").assertDoesNotExist()
        compose.onNodeWithText("0 of 1 done today").assertIsDisplayed()
    }
    @Test fun nextIsDisabledOnToday() {
        render()
        compose.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Next day").assertIsEnabled()
    }
    @Test fun relockResetsToToday() {
        render()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Back to today").assertExists()
        compose.runOnIdle { locked.value = true }
        awaitState { it.isToday }
        compose.onNodeWithText("Back to today").assertDoesNotExist()
        assertEquals(today, vm.state.value.day)
    }
    @Test fun allDoneProgressUsesSelectedDate() {
        compose.setContent { MaterialTheme { DayProgressLine(DayProgress(5, 5), today.minusDays(2), false) } }
        compose.onNodeWithText("All 5 done on Mon 6 Oct 🎉").assertIsDisplayed()
    }
    @Test fun singleDoneProgressUsesSelectedDate() {
        compose.setContent { MaterialTheme { DayProgressLine(DayProgress(1, 1), today.minusDays(2), false) } }
        compose.onNodeWithText("Done on Mon 6 Oct 🎉").assertIsDisplayed()
    }
    @Test fun pastCardTickWritesRenderedDay() {
        render()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Read, done on Tue 7 Oct").performScrollTo().performClick()
        awaitState { it.building.single().loggedOnDay }
        compose.onNodeWithContentDescription("Next day").performScrollTo().performClick()
        awaitState { it.isToday }
        compose.runOnIdle { assertEquals(false, vm.state.value.building.single().loggedOnDay) }
    }
    @Test fun pastRestCardShowsRestDayEvenWithACurrentStreak() {
        val card = HabitCardState(
            id = 1, name = "Read", emoji = "📚", type = HabitType.BUILD,
            day = today.minusDays(2), isToday = false, loggedOnDay = false,
            dueOnDay = false, current = 2, best = 2,
        )
        compose.setContent { MaterialTheme { HabitCard(card, {}, {}, {}, {}) } }
        compose.onNodeWithText("Rest day · 🔥 2 days · best 2 days").assertIsDisplayed()
    }
}
