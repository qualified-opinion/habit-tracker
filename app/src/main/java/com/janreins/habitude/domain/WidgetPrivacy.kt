package com.janreins.habitude.domain

/** Null means the widget must show only its lock message; an empty list means no habits yet. */
fun widgetHabits(hasPin: Boolean, habits: List<HabitWithEntries>): List<HabitWithEntries>? =
    if (hasPin) null else habits
