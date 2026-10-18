package com.janreins.habitude.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Runs [block] off the main thread while keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires a habit reminder or the evening streak check, then sets tomorrow's alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HabitudeApplication
        work {
            val today = LocalDate.now()
            val items = app.repository.snapshot()
            when (intent.action) {
                ACTION_HABIT -> {
                    val id = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
                    val item = items.firstOrNull { it.habit.id == id } ?: return@work
                    Reminders.reminderFor(item, today)?.let { text ->
                        Notifications.showReminder(
                            context,
                            id,
                            today,
                            text,
                            offerDone = item.habit.type == HabitType.BUILD,
                            doneLabel = if (item.habit.isCount) "+1" else "Done ✓",
                        )
                    }
                    app.reminders.schedule(item.habit)
                }
                ACTION_NUDGE -> {
                    if (!app.settings.eveningNudge) return@work
                    Reminders.nudgeFor(items, today)?.let { Notifications.showNudge(context, it) }
                    app.reminders.scheduleNudge(true, app.settings.eveningNudgeMinutes)
                }
            }
        }
    }

    companion object {
        const val ACTION_HABIT = "com.janreins.habitude.REMIND_HABIT"
        const val ACTION_NUDGE = "com.janreins.habitude.STREAK_NUDGE"
    }
}

/** The "Done" button on a reminder: ticks the habit off for today, or adds one to a counted habit. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HabitudeApplication
        val id = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        if (id == 0L) return
        // The reminder's own day; reminders posted before this was added carry none.
        val day = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let(LocalDate::ofEpochDay)
            ?: LocalDate.now()
        work {
            val habit = app.repository.getHabit(id)
            if (habit?.isCount == true) {
                app.repository.addCount(id, day, 1)
            } else {
                app.repository.setEntry(id, day, present = true)
            }
            Notifications.cancel(context, id.toInt())
        }
    }
}

/**
 * Alarms don't survive a restart and are set for a fixed moment, so set them all again after
 * boot, an app update, or a change of clock or time zone.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val app = context.applicationContext as HabitudeApplication
        work { app.rescheduleReminders() }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
