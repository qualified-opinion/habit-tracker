package com.janreins.habitude.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.janreins.habitude.MainActivity
import com.janreins.habitude.R
import com.janreins.habitude.domain.ReminderText
import java.time.LocalDate

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_NUDGES = "nudges"
    const val CHANNEL_BACKUPS = "backups"
    const val NUDGE_NOTIFICATION_ID = Int.MAX_VALUE
    const val BACKUP_NOTIFICATION_ID = Int.MAX_VALUE - 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The daily reminder you set on each habit"
                },
                NotificationChannel(CHANNEL_NUDGES, "Streak check", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "An evening heads-up when a streak is about to break"
                },
                NotificationChannel(CHANNEL_BACKUPS, "Backups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Problems with the weekly backup folder"
                },
            ),
        )
    }

    /** True on Android 13+ when the notification permission hasn't been granted yet. */
    fun needsRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean =
        !needsRuntimePermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * A habit reminder for [day]. Build habits get a "Done" button that ticks them off without
     * opening the app, for that day even if it's tapped after midnight.
     */
    fun showReminder(
        context: Context,
        habitId: Long,
        day: LocalDate,
        text: ReminderText,
        offerDone: Boolean,
        doneLabel: String = "Done ✓",
    ) {
        val builder = base(context, CHANNEL_REMINDERS, text)
        if (offerDone) {
            val done = PendingIntent.getBroadcast(
                context,
                habitId.toInt(),
                Intent(context, DoneReceiver::class.java)
                    .putExtra(EXTRA_HABIT_ID, habitId)
                    .putExtra(EXTRA_EPOCH_DAY, day.toEpochDay()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_notification, doneLabel, done)
        }
        post(context, habitId.toInt(), builder)
    }

    fun showNudge(context: Context, text: ReminderText) {
        post(context, NUDGE_NOTIFICATION_ID, base(context, CHANNEL_NUDGES, text))
    }

    fun showBackupProblem(context: Context) {
        val text = ReminderText(
            "Weekly backups have stopped",
            "Habitude lost access to the backup folder. Pick it again in Settings to keep weekly backups going.",
        )
        post(context, BACKUP_NOTIFICATION_ID, base(context, CHANNEL_BACKUPS, text))
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun base(context: Context, channel: String, text: ReminderText): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.sage_dark))
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}

const val EXTRA_HABIT_ID = "habit_id"
const val EXTRA_EPOCH_DAY = "epoch_day"
