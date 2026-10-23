package com.janreins.habitude.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.domain.HabitWithEntries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Every habit with its full history, for the charts. Null until the first load. */
class HabitsViewModel(private val repository: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<HabitWithEntries>?> =
        repository.habits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ticks or unticks a past day (done for a build habit, slipped for a break habit). */
    fun setEntry(habitId: Long, day: LocalDate, present: Boolean) {
        viewModelScope.launch { repository.setEntry(habitId, day, present) }
    }

    /** Writes a note on a day, or removes it when [text] is blank. */
    fun setNote(habitId: Long, day: LocalDate, text: String) {
        viewModelScope.launch { repository.setNote(habitId, day, text) }
    }

    /** Counts a counted habit up or down on a past day. */
    fun addCount(habitId: Long, day: LocalDate, delta: Int) {
        viewModelScope.launch { repository.addCount(habitId, day, delta) }
    }

    /** Moves a habit one place up (-1) or down (+1) among [shownIds]. */
    fun move(habitId: Long, delta: Int, shownIds: List<Long>) {
        viewModelScope.launch { repository.move(habitId, delta, shownIds) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HabitsViewModel((this[APPLICATION_KEY] as HabitudeApplication).repository)
            }
        }
    }
}
