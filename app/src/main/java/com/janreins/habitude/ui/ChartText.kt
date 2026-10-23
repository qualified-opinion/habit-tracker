package com.janreins.habitude.ui

import com.janreins.habitude.domain.DayMark
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val shortFormat = DateTimeFormatter.ofPattern("d MMM")

/** A time of day given in minutes after midnight, in the phone's short time format. */
fun formatMinutes(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

fun percent(share: Float?) = share?.let { "${(it * 100).roundToInt()}%" } ?: "–"

fun dayCaption(day: LocalDate, mark: DayMark): String {
    val what = when (mark) {
        DayMark.DONE -> "Done"
        DayMark.MISSED -> "Missed"
        DayMark.REST -> "Rest day"
        DayMark.PENDING -> "Not done yet"
        DayMark.CLEAN -> "Clean"
        DayMark.SLIP -> "Slipped"
        DayMark.NONE -> "Before you started"
    }
    return "${day.format(dayFormat)} · $what"
}

fun weekLabel(weekStart: LocalDate) = "Week of ${weekStart.format(shortFormat)}"
