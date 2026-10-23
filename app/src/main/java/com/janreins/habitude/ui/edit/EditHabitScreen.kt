package com.janreins.habitude.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.habitude.domain.Counts
import com.janreins.habitude.domain.HabitType
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import java.time.LocalDate
import android.text.format.DateFormat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import com.janreins.habitude.ui.rememberNotificationPermissionRequest
import com.janreins.habitude.ui.lock.LockAwareDialogHost
import com.janreins.habitude.ui.formatMinutes
import com.janreins.habitude.ui.lock.LockAwarePlatformDialog
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EditHabitViewModel = viewModel(factory = EditHabitViewModel.Factory),
) {
    val state = viewModel.state
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val isBuild = state.type == HabitType.BUILD
    val leave: () -> Unit = {
        if (viewModel.hasChanges) {
            confirmDiscard = true
        } else {
            onDone()
        }
    }

    // Back with unsaved changes asks first instead of quietly dropping them.
    BackHandler(enabled = viewModel.hasChanges) { confirmDiscard = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = leave) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (state.isNew) "New habit" else "Edit habit",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        if (state.isNew) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HabitType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, HabitType.entries.size),
                    ) {
                        Text(if (type == HabitType.BUILD) "Build" else "Break")
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text(if (isBuild) "I want to…" else "I want to stop…") },
            placeholder = { Text(if (isBuild) "Drink 2L of water" else "Doomscrolling in bed") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Icon")
            EmojiPicker(selected = state.emoji, isBuild = isBuild, onPick = viewModel::setEmoji)
        }

        if (isBuild) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.weeklyTarget == null,
                    onClick = { viewModel.setWeeklyTarget(null) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Set days") }
                SegmentedButton(
                    selected = state.weeklyTarget != null,
                    onClick = { if (state.weeklyTarget == null) viewModel.setWeeklyTarget(3) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Times a week") }
            }
        }

        if (isBuild && state.weeklyTarget != null) {
            WeeklyTargetRow(times = state.weeklyTarget, onChange = viewModel::setWeeklyTarget)
        } else if (isBuild) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("On these days")
                DayPicker(schedule = state.schedule, onToggle = viewModel::toggleDay)
            }
        }

        if (isBuild) {
            DailyTargetRow(times = state.dailyTarget ?: 1, onChange = viewModel::setDailyTarget)
        } else {
            Text(
                "Every day you don't slip adds to your clean run. If you do slip, log it honestly and the count starts again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CleanSinceRow(day = state.createdOn, onChange = viewModel::setCreatedOn)
        }

        ReminderRow(minutes = state.reminderMinutes, onChange = viewModel::setReminder)

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.save(onDone) },
            enabled = state.canSave && !viewModel.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (state.isNew) "Start this habit" else "Save changes")
        }

        if (!state.isNew) {
            OutlinedButton(
                onClick = { viewModel.save(onDone, archived = !state.archived) },
                enabled = state.canSave && !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.archived) "Restore habit" else "Archive habit")
            }
            Text(
                if (state.archived) {
                    "Archived: kept with its history, but hidden from Today and with no reminders."
                } else {
                    "Archiving keeps the habit and its history, but hides it from Today and stops its reminders."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Delete habit", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDiscard) {
        LockAwareDialogHost {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Discard changes?") },
                text = { Text("What you changed here won't be saved.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDone()
                    }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
                },
            )
        }
    }

    if (confirmDelete) {
        LockAwareDialogHost {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete \"${state.name}\"?") },
                text = { Text("Its whole history goes with it. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted)
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
                },
            )
        }
    }
}

/** The icon choices, eight to a row. Each one tells TalkBack whether it's the chosen icon. */
@Composable
internal fun EmojiPicker(selected: String, isBuild: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitEmojis.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { emoji ->
                    val chosen = emoji == selected
                    Surface(
                        selected = chosen,
                        onClick = { onPick(emoji) },
                        shape = CircleShape,
                        color = if (chosen) {
                            if (isBuild) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (chosen) {
                            BorderStroke(
                                2.dp,
                                if (isBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}

/**
 * One circle per weekday. The circles show one letter, so TalkBack reads the full day name
 * instead (otherwise Tuesday and Thursday both read "T"), plus whether the day is selected.
 */
@Composable
internal fun DayPicker(schedule: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in schedule
            val name = day.getDisplayName(TextStyle.FULL, locale)
            Surface(
                selected = selected,
                onClick = { onToggle(day) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .semantics { contentDescription = name },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(minutes: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val askPermission = rememberNotificationPermissionRequest()
    var picking by remember { mutableStateOf<Int?>(null) }
    picking?.let { initial ->
        LockAwarePlatformDialog(onDismiss = { picking = null }) { canAct ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    if (canAct()) {
                        onChange(hour * 60 + minute)
                        askPermission()
                    }
                },
                initial / 60,
                initial % 60,
                DateFormat.is24HourFormat(context),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Reminder")
        Surface(
            onClick = { picking = minutes ?: DEFAULT_REMINDER },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (minutes == null) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (minutes == null) "No reminder" else "Remind me at ${formatMinutes(minutes)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = minutes != null,
                    onCheckedChange = { on -> if (on) picking = DEFAULT_REMINDER else onChange(null) },
                )
            }
        }
    }
}

/** A "times a week" goal: any days count, and the streak is in weeks that met it. */
@Composable
private fun WeeklyTargetRow(times: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("How often")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(onClick = { onChange(times - 1) }, enabled = times > 1) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer times")
            }
            Text("$times", style = MaterialTheme.typography.headlineSmall)
            FilledTonalIconButton(onClick = { onChange(times + 1) }, enabled = times < 6) {
                Icon(Icons.Rounded.Add, contentDescription = "More times")
            }
            Text(
                if (times == 1) "time a week, any day" else "times a week, any days",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "Your streak counts the weeks in a row you reach this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How many a day makes it done: 1 is a plain tick, more is counted up through the day. */
@Composable
private fun DailyTargetRow(times: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Each day")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(onClick = { onChange(times - 1) }, enabled = times > 1) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer each day")
            }
            Text("$times", style = MaterialTheme.typography.headlineSmall)
            FilledTonalIconButton(onClick = { onChange(times + 1) }, enabled = times < Counts.MAX_TARGET) {
                Icon(Icons.Rounded.Add, contentDescription = "More each day")
            }
            Text(
                if (times == 1) "time, one tick" else "times, counted up",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (times > 1) {
            Text(
                "Tap the ring on Today once each time, like each glass of water. The day counts as done at $times.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** When a break habit's clean run started. Defaults to today; can be set back to when you quit. */
@Composable
private fun CleanSinceRow(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        LockAwarePlatformDialog(onDismiss = { picking = false }) { canAct ->
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    if (canAct()) onChange(LocalDate.of(year, month + 1, dayOfMonth))
                },
                day.year,
                day.monthValue - 1,
                day.dayOfMonth,
            ).apply { datePicker.maxDate = System.currentTimeMillis() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Clean since")
        Surface(
            onClick = { picking = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (day == LocalDate.now()) "Today" else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val DEFAULT_REMINDER = 9 * 60

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
