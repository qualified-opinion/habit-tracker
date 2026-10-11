package com.janreins.habitude.notify

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.janreins.habitude.domain.ReminderText
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val text = ReminderText("Read: 12 day streak", "Private habit details")

    @Test
    fun reminderWithPinHasGenericPublicVersionAndAuthenticatedAction() {
        val notification = reminder(true)
        assertPrivate(notification, "A habit reminder")
        assertEquals(true, notification.actions.single().isAuthenticationRequired)
        assertEquals(true, NotificationCompat.getAction(notification, 0)!!.isAuthenticationRequired)
    }

    @Test
    fun reminderWithoutPinKeepsExistingTextAndAction() {
        val notification = reminder(false)
        assertOriginal(notification)
        assertEquals(false, notification.actions.single().isAuthenticationRequired)
        assertEquals(false, NotificationCompat.getAction(notification, 0)!!.isAuthenticationRequired)
    }

    @Test
    fun nudgeWithPinHasGenericPublicVersion() {
        assertPrivate(Notifications.buildNudge(context, text, true), "A streak check")
    }

    @Test
    fun nudgeWithoutPinKeepsExistingText() {
        assertOriginal(Notifications.buildNudge(context, text, false))
    }

    @Test
    @Config(sdk = [30])
    fun actionAuthenticationIsANoOpBelowAndroid12() {
        assertEquals(false, NotificationCompat.getAction(reminder(true), 0)!!.isAuthenticationRequired)
    }

    private fun reminder(locked: Boolean) =
        Notifications.buildReminder(context, 1, LocalDate.of(2026, 1, 1), text, true, locked)

    private fun assertPrivate(notification: Notification, publicText: String) {
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("Habitude", notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(publicText, notification.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertNull(notification.publicVersion.actions)
        assertEquals(text.body, notification.extras.getString(Notification.EXTRA_TEXT))
    }

    private fun assertOriginal(notification: Notification) {
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility) // Existing builder default.
        assertNull(notification.publicVersion)
        assertEquals(text.title, notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(text.body, notification.extras.getString(Notification.EXTRA_TEXT))
    }
}
