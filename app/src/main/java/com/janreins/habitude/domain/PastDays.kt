package com.janreins.habitude.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PastDays {
    const val MAX_DAYS_BACK = 7
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun earliest(today: LocalDate): LocalDate = today.minusDays(MAX_DAYS_BACK.toLong())

    fun clamp(day: LocalDate, today: LocalDate): LocalDate = day.coerceIn(earliest(today), today)

    fun label(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date(day)
    }

    fun date(day: LocalDate): String = day.format(dateFormat)
}
