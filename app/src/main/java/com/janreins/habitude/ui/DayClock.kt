package com.janreins.habitude.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The current date and time as the screens and the widget see it. The app refreshes it
 * whenever it comes back to the front and at midnight, so nothing keeps showing yesterday.
 */
object DayClock {
    private val current = MutableStateFlow(LocalDateTime.now())

    val now: StateFlow<LocalDateTime> = current.asStateFlow()

    val today: Flow<LocalDate> = current.map { it.toLocalDate() }.distinctUntilChanged()

    fun today(): LocalDate = current.value.toLocalDate()

    fun refresh(now: LocalDateTime = LocalDateTime.now()) {
        current.value = now
    }
}

/** Today's date, kept up to date by [DayClock]. */
@Composable
fun rememberToday(): LocalDate {
    val today by DayClock.today.collectAsState(DayClock.today())
    return today
}
