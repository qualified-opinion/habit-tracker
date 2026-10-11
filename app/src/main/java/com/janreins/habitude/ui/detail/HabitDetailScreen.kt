package com.janreins.habitude.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.habitude.domain.DayMark
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.Milestones
import com.janreins.habitude.domain.Notes
import com.janreins.habitude.domain.Stats
import com.janreins.habitude.domain.Streaks
import com.janreins.habitude.ui.HabitsViewModel
import com.janreins.habitude.ui.charts.CalendarHeatmap
import com.janreins.habitude.ui.charts.ChartCard
import com.janreins.habitude.ui.charts.HeatCell
import com.janreins.habitude.ui.charts.Legend
import com.janreins.habitude.ui.charts.LegendItem
import com.janreins.habitude.ui.charts.StatTile
import com.janreins.habitude.ui.charts.StreakHistory
import com.janreins.habitude.ui.charts.WeeklyBars
import com.janreins.habitude.ui.charts.chartPalette
import com.janreins.habitude.ui.dayCaption
import com.janreins.habitude.ui.percent
import com.janreins.habitude.ui.plural
import com.janreins.habitude.ui.rememberToday
import com.janreins.habitude.ui.weekLabel
import java.time.LocalDate
import kotlin.math.ceil

private const val HEATMAP_WEEKS = 20
private const val BAR_WEEKS = 12

@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val item = habits?.firstOrNull { it.habit.id == habitId }

    if (habits != null && item == null) {
        // The habit was deleted while we were looking at it.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    if (item == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                "${item.habit.emoji}  ${item.habit.name}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit habit")
            }
        }
        if (item.habit.archived) {
            Text(
                "Archived. Tap the pencil to restore it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HabitStats(
            item,
            onSetEntry = { day, present -> viewModel.setEntry(habitId, day, present) },
            onAddCount = { day, delta -> viewModel.addCount(habitId, day, delta) },
            onSetNote = { day, text -> viewModel.setNote(habitId, day, text) },
        )
    }
}

@Composable
internal fun HabitStats(
    item: HabitWithEntries,
    onSetEntry: (LocalDate, Boolean) -> Unit,
    onAddCount: (LocalDate, Int) -> Unit,
    onSetNote: (LocalDate, String) -> Unit,
) {
    val today = rememberToday()
    var noteFor by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val palette = chartPalette()
    val habit = item.habit
    val isBuild = habit.type == HabitType.BUILD
    val color = if (isBuild) palette.build else palette.breakHabit
    val monthAgo = today.minusDays(29)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isBuild) {
            StatTile(
                "${Streaks.current(item, today)}",
                if (habit.isWeekly) "Weeks 🔥" else "Streak 🔥",
                Modifier.weight(1f),
            )
            StatTile(
                "${Streaks.best(item, today)}",
                if (habit.isWeekly) "Best weeks" else "Best",
                Modifier.weight(1f),
            )
            StatTile(percent(Stats.completionRate(item, monthAgo, today, today)), "Last 30 days", Modifier.weight(1f))
        } else {
            StatTile("${Streaks.daysClean(item.entries, habit.createdOn, today)}", "Days clean", Modifier.weight(1f))
            StatTile("${Streaks.bestCleanRun(item.entries, habit.createdOn, today)}", "Best run", Modifier.weight(1f))
            StatTile(
                "${item.entries.count { !it.isBefore(monthAgo) && !it.isAfter(today) }}",
                "Slips, 30 days",
                Modifier.weight(1f),
            )
        }
    }

    ChartCard("Last $HEATMAP_WEEKS weeks") {
        CalendarHeatmap(
            today = today,
            weeks = HEATMAP_WEEKS,
            cell = { day ->
                when (Stats.dayMark(item, day, today)) {
                    DayMark.DONE, DayMark.CLEAN -> HeatCell(palette.build)
                    DayMark.SLIP -> HeatCell(palette.breakHabit, dot = true)
                    DayMark.MISSED -> HeatCell(palette.missed)
                    DayMark.PENDING -> HeatCell(palette.missed, outlined = true)
                    DayMark.REST -> HeatCell(palette.faint)
                    DayMark.NONE -> HeatCell(null)
                }
            },
            describe = { day ->
                val mark = Stats.dayMark(item, day, today)
                // Any day counts towards a weekly goal, so an empty day isn't a "rest day".
                val caption = if (habit.isWeekly && mark == DayMark.REST) "${dayCaption(day, mark).substringBefore(" · ")} · Not done"
                else dayCaption(day, mark)
                val target = habit.dailyTarget
                if (target != null && mark != DayMark.NONE) "$caption · ${item.countOn(day)} of $target" else caption
            },
            hint = "Tap a day to see or change it",
            action = { day ->
                // Forgot to log a day? Fix it here. Days before the habit started stay empty.
                val target = habit.dailyTarget
                if (!day.isBefore(habit.createdOn)) {
                    IconButton(onClick = { noteFor = day }) {
                        Icon(
                            Icons.Outlined.EditNote,
                            contentDescription = if (day in item.notes) "Edit note" else "Add a note",
                        )
                    }
                }
                if (!day.isBefore(habit.createdOn) && target != null) {
                    val count = item.countOn(day)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onAddCount(day, -1) }, enabled = count > 0) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Take one off")
                        }
                        IconButton(onClick = { onAddCount(day, 1) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add one")
                        }
                    }
                } else if (!day.isBefore(habit.createdOn)) {
                    val logged = day in item.entries
                    TextButton(onClick = { onSetEntry(day, !logged) }) {
                        Text(
                            when {
                                isBuild && logged -> "Undo done"
                                isBuild -> "Mark done"
                                logged -> "Remove slip"
                                else -> "Log a slip"
                            },
                        )
                    }
                }
            },
            details = { day ->
                item.notes[day]?.let { note ->
                    Text(
                        "“$note”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
            },
        )
        Legend(
            if (habit.isWeekly) {
                listOf(
                    LegendItem("Done", palette.build),
                    LegendItem("Not done", palette.faint),
                )
            } else if (isBuild) {
                listOf(
                    LegendItem("Done", palette.build),
                    LegendItem("Missed", palette.missed),
                    LegendItem("Rest day", palette.faint),
                )
            } else {
                listOf(
                    LegendItem("Clean", palette.build),
                    LegendItem("Slipped", palette.breakHabit, dot = true),
                )
            },
        )
    }

    val weeks = remember(item, today) { Stats.weekly(item, today, BAR_WEEKS) }
    if (isBuild) {
        val target = habit.weeklyTarget.takeIf { habit.isWeekly }
        ChartCard(if (target != null) "Weekly goal" else "Weekly completion") {
            WeeklyBars(
                weeks = weeks,
                color = color,
                maxValue = 1f,
                ticks = listOf(0f, 0.5f, 1f),
                tickLabel = { percent(it) },
                describe = {
                    if (target != null) {
                        val done = Math.round((it.value ?: 0f) * target)
                        "${weekLabel(it.weekStart)} · $done of $target"
                    } else {
                        "${weekLabel(it.weekStart)} · ${percent(it.value)} of planned days"
                    }
                },
            )
        }
    } else {
        val most = ceil(weeks.maxOf { it.value ?: 0f }).coerceAtLeast(2f)
        ChartCard("Slips per week") {
            WeeklyBars(
                weeks = weeks,
                color = color,
                maxValue = most,
                ticks = listOf(0f, most),
                tickLabel = { "${it.toInt()}" },
                describe = { "${weekLabel(it.weekStart)} · ${plural(it.value?.toInt() ?: 0, "slip")}" },
            )
        }
    }

    MilestonesCard(item, today)

    NotesCard(item, today, isBuild, onOpen = { noteFor = it })

    val runs = remember(item, today) { Stats.runs(item, today) }
    ChartCard(
        when {
            habit.isWeekly -> "Streak history, in weeks"
            isBuild -> "Streak history"
            else -> "Clean runs"
        },
    ) {
        if (runs.isEmpty()) {
            Text(
                if (isBuild) "Your first streak starts with one tick." else "Your first clean day is on its way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StreakHistory(runs, color)
        }
    }

    noteFor?.let { day ->
        NoteDialog(
            day = day,
            initial = item.notes[day].orEmpty(),
            placeholder = if (isBuild) "How did it go?" else "What led to it? Where were you, how did you feel?",
            onSave = { text ->
                onSetNote(day, text)
                noteFor = null
            },
            onDismiss = { noteFor = null },
        )
    }
}

/** The latest notes on this habit, newest first. Tap one to change it. */
@Composable
private fun NotesCard(item: HabitWithEntries, today: LocalDate, isBuild: Boolean, onOpen: (LocalDate) -> Unit) {
    val notes = remember(item) { Notes.recent(item) }
    ChartCard("Notes") {
        if (notes.isEmpty()) {
            Text(
                if (isBuild) {
                    "Tap a day on the calendar, then the note button, to jot down how it went."
                } else {
                    "Tap a slip on the calendar, then the note button, to write what led to it. Patterns show up over time."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                notes.forEach { (day, note) ->
                    val mark = Stats.dayMark(item, day, today)
                    Surface(
                        onClick = { onOpen(day) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                dayCaption(day, mark),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The milestones this habit's best run has reached, and the ones still ahead. */
@Composable
private fun MilestonesCard(item: HabitWithEntries, today: LocalDate) {
    val habit = item.habit
    val best = remember(item, today) { Streaks.best(item, today) }
    val steps = Milestones.stepsFor(habit)
    ChartCard("Milestones") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEach { step ->
                val reached = best >= step
                val label = when {
                    habit.isWeekly -> plural(step, "week")
                    step == 365 -> "1 year"
                    else -> plural(step, "day")
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (reached) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (reached) "Reached" else "Not yet"
                        },
                ) {
                    Column(
                        Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (reached) "🏅" else "○", style = MaterialTheme.typography.titleMedium)
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
