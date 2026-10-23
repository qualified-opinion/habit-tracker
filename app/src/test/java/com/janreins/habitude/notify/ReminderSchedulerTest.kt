package com.janreins.habitude.notify

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.janreins.habitude.domain.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Suppress("DEPRECATION")
    private fun midnightAlarms() = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.action == ReminderReceiver.ACTION_MIDNIGHT
    }

    @Test fun reschedulingSetsOneWidgetRefreshJustAfterMidnight() {
        val expected = Days.justAfterMidnight(ZonedDateTime.now()).toInstant().toEpochMilli()
        val scheduler = ReminderScheduler(context)
        scheduler.rescheduleAll(emptyList(), nudge = false, nudgeMinutes = 20 * 60)
        scheduler.scheduleMidnight()

        val alarm = midnightAlarms().single()
        // Setting it twice keeps one alarm; a day later only if the test ran across midnight.
        assertTrue(alarm.triggerAtMs - expected in 0L..25 * 3_600_000L)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test fun theMidnightAlarmIsSetEvenWithEverythingElseOff() {
        ReminderScheduler(context).rescheduleAll(emptyList(), nudge = false, nudgeMinutes = 20 * 60)
        assertEquals(1, midnightAlarms().size)
    }
}
