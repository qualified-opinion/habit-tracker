package com.janreins.habitude.widget

import android.app.Application
import android.content.Context
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ToggleHabitActionTest {
    @Test
    fun staleWidgetWithPinReturnsBeforeAccessingRepository() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("pin_hash", "set").apply()
        // A plain Application has no repository: reaching the application cast would fail.
        ToggleHabitAction().onAction(
            context,
            AppWidgetId(1),
            actionParametersOf(ActionParameters.Key<Long>("habitId") to 1L),
        )
    }
}
