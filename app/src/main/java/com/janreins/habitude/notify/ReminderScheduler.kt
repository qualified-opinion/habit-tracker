package com.janreins.habitude.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.Reminders
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets one alarm per habit reminder, plus one for the evening streak check. Alarms are
 * "allow while idle" rather than exact, so they need no special permission and may land a
 * few minutes late when the phone is dozing. Each alarm re-arms itself for the next day.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(habit: Habit) {
        val minutes = habit.reminderMinutes
        if (minutes == null || habit.archived) {
            cancel(habit.id)
        } else {
            set(habitIntent(habit.id), minutes)
        }
    }

    fun cancel(habitId: Long) {
        alarms.cancel(habitIntent(habitId))
        Notifications.cancel(context, habitId.toInt())
    }

    /** Sets the evening streak check for [minutes] after midnight, or cancels it. */
    fun scheduleNudge(enabled: Boolean, minutes: Int) {
        if (enabled) set(nudgeIntent(), minutes) else alarms.cancel(nudgeIntent())
    }

    fun rescheduleAll(habits: List<Habit>, nudge: Boolean, nudgeMinutes: Int) {
        habits.forEach(::schedule)
        scheduleNudge(nudge, nudgeMinutes)
    }

    private fun set(intent: PendingIntent, minutes: Int) {
        val next = Reminders.nextTrigger(LocalDateTime.now(), minutes)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }

    private fun habitIntent(habitId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        habitId.toInt(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_HABIT)
            .putExtra(EXTRA_HABIT_ID, habitId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun nudgeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        Notifications.NUDGE_NOTIFICATION_ID,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_NUDGE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
