package com.janreins.habitude.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.Stats
import com.janreins.habitude.domain.Streaks
import com.janreins.habitude.domain.TodayList
import com.janreins.habitude.ui.HabitsViewModel
import com.janreins.habitude.ui.charts.CalendarHeatmap
import com.janreins.habitude.ui.charts.ChartCard
import com.janreins.habitude.ui.charts.HeatCell
import com.janreins.habitude.ui.charts.RampLegend
import com.janreins.habitude.ui.charts.StatTile
import com.janreins.habitude.ui.charts.chartPalette
import com.janreins.habitude.ui.charts.rampColor
import com.janreins.habitude.ui.percent
import com.janreins.habitude.ui.plural
import com.janreins.habitude.ui.rememberToday
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val captionFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun ProgressScreen(
    onOpenHabit: (Long) -> Unit,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val today = rememberToday()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.displaySmall)

        val items = habits
        if (items != null && items.isEmpty()) {
            EmptyStateCard(
                title = "Nothing to chart yet",
                body = "Add a habit on the Today tab. Heatmaps, streak history and weekly trends will show up here.",
            )
        } else if (items != null) {
            ProgressContent(
                items = items.filter { !it.habit.archived },
                archived = items.filter { it.habit.archived },
                today = today,
                onOpenHabit = onOpenHabit,
                onMove = viewModel::move,
            )
        }
    }
}

@Composable
private fun ProgressContent(
    items: List<HabitWithEntries>,
    archived: List<HabitWithEntries>,
    today: LocalDate,
    onOpenHabit: (Long) -> Unit,
    onMove: (habitId: Long, delta: Int, shownIds: List<Long>) -> Unit,
) {
    var reordering by rememberSaveable { mutableStateOf(false) }
    val builds = items.filter { it.habit.type == HabitType.BUILD }
    val overall = remember(items, today) {
        Stats.dailyOverall(items, today.minusDays(7L * 20), today, today)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val progress = TodayList.progress(builds, today)
        val week = overall.filterKeys { !it.isBefore(today.minusDays(6)) }.values.filterNotNull()
        // In days, so weekly goals (counted in weeks) are left out.
        val bestGoing = items.filter { !it.habit.isWeekly }.maxOfOrNull { Streaks.current(it, today) } ?: 0
        StatTile("${progress.done}/${progress.due}", "Done today", Modifier.weight(1f))
        StatTile(percent(week.takeIf { it.isNotEmpty() }?.average()?.toFloat()), "Last 7 days", Modifier.weight(1f))
        StatTile("$bestGoing", "Longest going", Modifier.weight(1f))
    }

    if (builds.isNotEmpty()) {
        val palette = chartPalette()
        ChartCard("Every habit, last 20 weeks") {
            CalendarHeatmap(
                today = today,
                weeks = 20,
                cell = { day -> HeatCell(overall[day]?.let { palette.rampColor(it) } ?: palette.faint) },
                describe = { day ->
                    val share = overall[day]
                    "${day.format(captionFormat)} · " +
                        if (share == null) "Nothing planned" else "${percent(share)} of habits done"
                },
            )
            RampLegend(palette.ramp)
        }
    }

    if (items.isNotEmpty()) {
        ChartCard("Your habits") {
            if (items.size > 1) {
                TextButton(onClick = { reordering = !reordering }) {
                    Text(if (reordering) "Done" else "Reorder habits")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val shownIds = items.map { it.habit.id }
                items.forEachIndexed { index, item ->
                    if (reordering && items.size > 1) {
                        ReorderRow(
                            item,
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMove = { delta -> onMove(item.habit.id, delta, shownIds) },
                        )
                    } else {
                        HabitRow(item, today, onClick = { onOpenHabit(item.habit.id) })
                    }
                }
            }
            if (reordering) {
                Text(
                    "Today, Progress and the widget show your habits in this order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (archived.isNotEmpty()) {
        ChartCard("Archived") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                archived.forEach { item -> HabitRow(item, today, onClick = { onOpenHabit(item.habit.id) }) }
            }
        }
    }
}

/** A habit with buttons to move it up or down; buttons rather than dragging, so TalkBack can use them. */
@Composable
private fun ReorderRow(item: HabitWithEntries, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit) {
    val habit = item.habit
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text(habit.emoji, fontSize = 20.sp) }
            Text(
                habit.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${habit.name} up")
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${habit.name} down")
            }
        }
    }
}

@Composable
private fun HabitRow(item: HabitWithEntries, today: LocalDate, onClick: () -> Unit) {
    val palette = chartPalette()
    val habit = item.habit
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text(habit.emoji, fontSize = 20.sp) }
            Text(
                habit.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when (habit.type) {
                HabitType.BUILD -> {
                    val rate = Stats.completionRate(item, today.minusDays(29), today, today)
                    LinearProgressIndicator(
                        progress = { rate ?: 0f },
                        color = palette.build,
                        trackColor = palette.missed,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier
                            .width(64.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        percent(rate),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(40.dp),
                    )
                }
                HabitType.BREAK -> Text(
                    "${plural(Streaks.daysClean(item.entries, habit.createdOn, today), "day")} clean",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
