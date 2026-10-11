package com.janreins.habitude.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.habitude.domain.DayProgress
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.Notes
import com.janreins.habitude.domain.PastDays
import com.janreins.habitude.ui.DayClock
import com.janreins.habitude.ui.greetingFor
import com.janreins.habitude.ui.screens.EmptyStateCard
import com.janreins.habitude.ui.lock.LockAwareDialogHost
import com.janreins.habitude.ui.lock.LocalAppLocked
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onAddHabit: (HabitType) -> Unit,
    onOpenHabit: (Long) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by DayClock.now.collectAsStateWithLifecycle()
    val locked = LocalAppLocked.current
    LaunchedEffect(locked) { if (locked) viewModel.backToToday() }
    var confirmSlipDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var confirmSlipFor by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 20.dp)) {
                    Text(
                        (state.day ?: now.toLocalDate()).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::previousDay, enabled = state.canGoBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous day")
                        }
                        Text(
                            state.label,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = viewModel::nextDay, enabled = state.canGoForward, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }
                    Text(greetingFor(now.hour), style = MaterialTheme.typography.displaySmall)
                    if (state.progress.due > 0) {
                        Spacer(Modifier.height(16.dp))
                        DayProgressLine(state.progress, state.day, state.isToday)
                    }
                }
            }

            if (!state.isToday) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Viewing ${state.day?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))}")
                            TextButton(onClick = viewModel::backToToday) { Text("Back to today") }
                        }
                    }
                }
            }

            if (!state.loading && state.isEmpty) {
                item { EmptyToday(onAddHabit) }
            }

            if (state.building.isNotEmpty()) {
                item { SectionLabel("Building") }
                items(state.building, key = { it.id }) { card ->
                    HabitCard(
                        state = card,
                        // Slides into place when ticking moves it between to-do and done.
                        modifier = Modifier.animateItem(),
                        onOpen = { onOpenHabit(card.id) },
                        onToggleDone = { viewModel.setLogged(card.id, card.day, !card.loggedOnDay) },
                        onSlip = {},
                        onUndoSlip = {},
                        onAddCount = { delta -> viewModel.addCount(card.id, card.day, delta) },
                    )
                }
            }

            if (state.breaking.isNotEmpty()) {
                item { SectionLabel("Breaking") }
                items(state.breaking, key = { it.id }) { card ->
                    HabitCard(
                        state = card,
                        modifier = Modifier.animateItem(),
                        onOpen = { onOpenHabit(card.id) },
                        onToggleDone = {},
                        onSlip = {
                            confirmSlipFor = card.id
                            confirmSlipDay = card.day
                        },
                        onUndoSlip = { viewModel.setLogged(card.id, card.day, false) },
                    )
                }
            }
        }

        if (!state.isEmpty) {
            ExtendedFloatingActionButton(
                onClick = { onAddHabit(HabitType.BUILD) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New habit") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )
        }
    }

    confirmSlipFor?.let { id ->
        val name = state.breaking.firstOrNull { it.id == id }?.name.orEmpty()
        var note by rememberSaveable(id) { mutableStateOf("") }
        LockAwareDialogHost {
            AlertDialog(
                onDismissRequest = { confirmSlipFor = null },
                title = { Text("Log a slip?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This resets your clean run for \"$name\". Be kind to yourself, it's one day.")
                        // Writing down what led to it helps spot the triggers over time.
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(Notes.MAX_LENGTH) },
                            label = { Text("What led to it? (optional)") },
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmSlipDay?.let { viewModel.logSlip(id, it, note) }
                        confirmSlipFor = null
                    }) { Text("Log slip") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSlipFor = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
internal fun DayProgressLine(progress: DayProgress, day: LocalDate? = null, isToday: Boolean = true) {
    val allDone = progress.done == progress.due
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when {
                !isToday && allDone && progress.due == 1 -> "Done on ${day?.let(PastDays::date)} 🎉"
                !isToday && allDone -> "All ${progress.due} done on ${day?.let(PastDays::date)} 🎉"
                !isToday -> "${progress.done} of ${progress.due} done on ${day?.let(PastDays::date)}"
                allDone && progress.due == 1 -> "Done for today 🎉"
                allDone -> "All ${progress.due} done today 🎉"
                else -> "${progress.done} of ${progress.due} done today"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.due },
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyToday(onAddHabit: (HabitType) -> Unit) {
    EmptyStateCard(
        title = "A blank page",
        body = "Start a habit you want to keep, or one you're ready to let go of. Your streaks will live here.",
    ) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { onAddHabit(HabitType.BUILD) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.Spa, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Build one")
            }
            FilledTonalButton(
                onClick = { onAddHabit(HabitType.BREAK) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Break one")
            }
        }
    }
}
