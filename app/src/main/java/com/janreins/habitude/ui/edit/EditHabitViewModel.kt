package com.janreins.habitude.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.domain.Counts
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.notify.ReminderScheduler
import com.janreins.habitude.ui.DayClock
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

val HabitEmojis = listOf(
    "💧", "🏃", "📚", "🧘", "🥗", "😴", "✍️", "🌱",
    "🚬", "📱", "🍷", "🍬", "☕", "🎮", "🛒", "💸",
)

data class EditHabitState(
    val isNew: Boolean = true,
    val name: String = "",
    val emoji: String = HabitEmojis.first(),
    val type: HabitType = HabitType.BUILD,
    val schedule: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val createdOn: LocalDate = LocalDate.now(),
    /** Minutes after midnight, or null for no reminder. */
    val reminderMinutes: Int? = null,
    val archived: Boolean = false,
    /** Times a week (any days) instead of set days, for a build habit. */
    val weeklyTarget: Int? = null,
    /** How many make a day done, for a build habit counted through the day. Null for a single tick. */
    val dailyTarget: Int? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && (type == HabitType.BREAK || weeklyTarget != null || schedule.isNotEmpty())
}

class EditHabitViewModel(
    private val repository: HabitRepository,
    private val reminders: ReminderScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<Long>(ARG_ID) ?: 0L

    /** The habit as saved, when editing one, so its schedule history carries over. */
    private var loaded: Habit? = null

    var state by mutableStateOf(
        EditHabitState(
            type = savedStateHandle.get<String>(ARG_TYPE)?.let(HabitType::valueOf) ?: HabitType.BUILD,
        ).let { if (it.type == HabitType.BREAK) it.copy(emoji = HabitEmojis[8]) else it },
    )
        private set

    /** The form as it was opened, to tell whether leaving would lose anything. */
    private var initial by mutableStateOf(state)

    val hasChanges: Boolean get() = state != initial

    /**
     * True from the first tap on Save, Archive or Delete. Further taps are ignored, so a quick
     * double tap can't save a new habit twice or leave the screen twice.
     */
    var busy by mutableStateOf(false)
        private set

    init {
        if (habitId != 0L) {
            viewModelScope.launch {
                repository.getHabit(habitId)?.let { habit ->
                    loaded = habit
                    state = EditHabitState(
                        isNew = false,
                        name = habit.name,
                        emoji = habit.emoji,
                        type = habit.type,
                        schedule = habit.schedule,
                        createdOn = habit.createdOn,
                        reminderMinutes = habit.reminderMinutes,
                        archived = habit.archived,
                        weeklyTarget = habit.weeklyTarget,
                        dailyTarget = habit.dailyTarget,
                    )
                    initial = state
                }
            }
        }
    }

    fun setName(name: String) { state = state.copy(name = name.take(40)) }
    fun setEmoji(emoji: String) { state = state.copy(emoji = emoji) }
    fun setType(type: HabitType) { if (state.isNew) state = state.copy(type = type) }

    fun setReminder(minutes: Int?) { state = state.copy(reminderMinutes = minutes) }

    /** "Clean since" for a break habit. Never later than today. */
    fun setCreatedOn(day: LocalDate) { state = state.copy(createdOn = minOf(day, DayClock.today())) }

    /** A times-a-week goal (1 to 6), or null to go back to set days. */
    fun setWeeklyTarget(times: Int?) { state = state.copy(weeklyTarget = times?.coerceIn(1, 6)) }

    /** How many a day makes it done. 1 means a single tick. */
    fun setDailyTarget(times: Int) {
        state = state.copy(dailyTarget = times.coerceIn(1, Counts.MAX_TARGET).takeIf { it > 1 })
    }

    fun toggleDay(day: DayOfWeek) {
        val days = state.schedule
        state = state.copy(schedule = if (day in days) days - day else days + day)
    }

    /** Saves the form; [archived] also archives or restores the habit. */
    fun save(onSaved: () -> Unit, archived: Boolean = state.archived) {
        val s = state
        if (!s.canSave || busy) return
        busy = true
        viewModelScope.launch {
            val schedule = if (s.type == HabitType.BUILD) s.schedule else DayOfWeek.entries.toSet()
            val dailyTarget = s.dailyTarget.takeIf { s.type == HabitType.BUILD }
            // A changed schedule or daily goal applies from today; earlier days keep the one they had.
            val today = DayClock.today()
            val base = loaded?.withSchedule(schedule, today)?.withDailyTarget(dailyTarget, today)
                ?: Habit(id = habitId, name = "", emoji = "", type = s.type, schedule = schedule, createdOn = s.createdOn)
            val habit = base.copy(
                name = s.name.trim(),
                emoji = s.emoji,
                reminderMinutes = s.reminderMinutes,
                archived = archived,
                weeklyTarget = s.weeklyTarget.takeIf { s.type == HabitType.BUILD },
                dailyTarget = dailyTarget,
                // A break habit's start is its "clean since" date, which can be moved.
                createdOn = if (s.type == HabitType.BREAK) s.createdOn else base.createdOn,
            )
            val id = repository.save(habit)
            // A new goal decides whether today's count so far makes it done.
            if (habit.dailyTarget != null) repository.recheckCount(id, today)
            reminders.schedule(habit.copy(id = id))
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            repository.delete(habitId)
            reminders.cancel(habitId)
            onDeleted()
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_TYPE = "type"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HabitudeApplication
                EditHabitViewModel(app.repository, app.reminders, createSavedStateHandle())
            }
        }
    }
}
