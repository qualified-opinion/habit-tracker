package com.janreins.habitude.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.domain.DayProgress
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.PastDays
import com.janreins.habitude.domain.Streaks
import com.janreins.habitude.domain.TodayList
import com.janreins.habitude.ui.DayClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitCardState(
    val id: Long,
    val name: String,
    val emoji: String,
    val type: HabitType,
    val day: LocalDate,
    val isToday: Boolean,
    /** Done on the shown day for a build habit, slipped for a break habit. */
    val loggedOnDay: Boolean,
    val dueOnDay: Boolean,
    /** Current streak for a build habit (weeks for a weekly goal), days clean for a break habit. */
    val current: Int,
    val best: Int,
    /** For a times-a-week habit: the goal, and how many days are done this week. */
    val weeklyTarget: Int? = null,
    val doneThisWeek: Int = 0,
    /** For a count habit: the current goal and the shown day's count. */
    val dailyTarget: Int? = null,
    val countOnDay: Int = 0,
)

data class TodayUiState(
    val loading: Boolean = true,
    /** The day the cards are for. */
    val day: LocalDate? = null,
    val today: LocalDate? = null,
    val isToday: Boolean = true,
    val label: String = "Today",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Build habits done out of those due on the shown day. */
    val progress: DayProgress = DayProgress(0, 0),
    val building: List<HabitCardState> = emptyList(),
    val breaking: List<HabitCardState> = emptyList(),
) {
    val isEmpty: Boolean get() = building.isEmpty() && breaking.isEmpty()
}

class TodayViewModel(
    private val repository: HabitRepository,
    today: Flow<LocalDate> = DayClock.today,
) : ViewModel() {

    private data class Selection(val day: LocalDate, val chosenOn: LocalDate)
    private val selection = MutableStateFlow<Selection?>(null)
    private val realToday = today.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun shownDay(today: LocalDate): LocalDate =
        selection.value?.takeIf { it.chosenOn == today }?.day?.let { PastDays.clamp(it, today) } ?: today

    fun previousDay() = moveDay(-1)
    fun nextDay() = moveDay(1)
    fun backToToday() { selection.value = null }

    private fun moveDay(delta: Long) {
        val today = realToday.value ?: return
        selection.value = Selection(PastDays.clamp(shownDay(today).plusDays(delta), today), today)
    }

    val state: StateFlow<TodayUiState> = combine(repository.habits, realToday.filterNotNull(), selection) { habits, today, chosen ->
        val day = chosen?.takeIf { it.chosenOn == today }?.day?.let { PastDays.clamp(it, today) } ?: today
        val cards = TodayList.order(habits, day, today).map { it.toCard(day, today) }
        TodayUiState(
            loading = false,
            day = day,
            today = today,
            isToday = day == today,
            label = PastDays.label(day, today),
            canGoBack = day.isAfter(PastDays.earliest(today)),
            canGoForward = day.isBefore(today),
            progress = TodayList.progress(habits, day, today),
            building = cards.filter { it.type == HabitType.BUILD },
            breaking = cards.filter { it.type == HabitType.BREAK },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun validDay(day: LocalDate): Boolean = realToday.value?.let {
        day in PastDays.earliest(it)..it
    } == true

    /** Writes only the rendered day, including taps queued across navigation or midnight. */
    fun setLogged(habitId: Long, day: LocalDate, logged: Boolean) {
        viewModelScope.launch {
            if (validDay(day)) repository.setEntry(habitId, day, logged)
        }
    }

    /** Logs a slip on the day shown, with an optional note on what led to it. */
    fun logSlip(habitId: Long, day: LocalDate, note: String) {
        viewModelScope.launch {
            if (!validDay(day)) return@launch
            repository.setEntry(habitId, day, true)
            if (note.isNotBlank()) repository.setNote(habitId, day, note)
        }
    }

    /** Counts a counted habit up (or down, with a negative [delta]) on the day shown. */
    fun addCount(habitId: Long, day: LocalDate, delta: Int) {
        viewModelScope.launch {
            if (validDay(day)) repository.addCount(habitId, day, delta, today = realToday.value ?: return@launch)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TodayViewModel((this[APPLICATION_KEY] as HabitudeApplication).repository)
            }
        }
    }
}

internal fun HabitWithEntries.toCard(day: LocalDate, today: LocalDate = day): HabitCardState =
    HabitCardState(
        id = habit.id,
        name = habit.name,
        emoji = habit.emoji,
        type = habit.type,
        day = day,
        isToday = day == today,
        loggedOnDay = day in entries,
        dueOnDay = TodayList.isDueOn(this, day, today),
        current = Streaks.current(this, today),
        best = Streaks.best(this, today),
        weeklyTarget = habit.weeklyTarget.takeIf { habit.isWeekly },
        doneThisWeek = Streaks.doneInWeek(entries, day, today),
        dailyTarget = habit.dailyTarget.takeIf { habit.isCount },
        countOnDay = countOn(day),
    )
