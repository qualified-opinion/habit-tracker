package com.janreins.habitude

import android.app.Application
import android.net.Uri
import com.janreins.habitude.backup.AutoBackup
import com.janreins.habitude.data.HabitRepository
import com.janreins.habitude.data.HabitudeDatabase
import com.janreins.habitude.notify.Notifications
import com.janreins.habitude.notify.ReminderScheduler
import com.janreins.habitude.notify.Settings
import com.janreins.habitude.widget.HabitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class HabitudeApplication : Application() {
    val repository: HabitRepository by lazy {
        HabitRepository(HabitudeDatabase.create(this))
    }
    val reminders: ReminderScheduler by lazy { ReminderScheduler(this) }
    val settings: Settings by lazy { Settings(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { rescheduleReminders() }
        appScope.launch {
            settings.autoBackupFolder?.let { folder ->
                if (!AutoBackup.hasWriteAccess(this@HabitudeApplication, Uri.parse(folder))) {
                    AutoBackup.markAccessLost(this@HabitudeApplication)
                }
                AutoBackup.schedule(this@HabitudeApplication, true)
            }
        }
        // Keep home-screen widgets in step with whatever changes in the app.
        appScope.launch {
            repository.habits.drop(1).collect {
                runCatching { HabitWidget.refresh(this@HabitudeApplication) }
            }
        }
    }

    suspend fun rescheduleReminders() {
        reminders.rescheduleAll(repository.snapshot().map { it.habit }, settings.eveningNudge)
    }
}
