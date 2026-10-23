package com.janreins.habitude.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.MainActivity
import com.janreins.habitude.domain.widgetHabits
import com.janreins.habitude.notify.Settings
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.TodayList
import com.janreins.habitude.ui.DayClock
import com.janreins.habitude.ui.theme.DarkColors
import com.janreins.habitude.ui.theme.LightColors
import java.time.LocalDate

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

private val HabitIdKey = ActionParameters.Key<Long>("habitId")

/**
 * A home-screen widget with today's build habits: tap one to tick it off (or untick it),
 * tap the header to open the app.
 */
class HabitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as HabitudeApplication
        val repository = app.repository
        val initial = repository.snapshot()
        provideContent {
            val items by repository.habits.collectAsState(initial)
            val hasPin by app.settings.pinChanges.collectAsState(app.settings.hasPin)
            val today by DayClock.today.collectAsState(DayClock.today())
            GlanceTheme(colors = WidgetColors) {
                val visible = widgetHabits(hasPin, items)
                if (visible == null) LockedWidget() else WidgetContent(visible, today)
            }
        }
    }

    companion object {
        /** Redraws every Habitude widget, e.g. after a habit changes in the app. */
        suspend fun refresh(context: Context) {
            DayClock.refresh()
            HabitWidget().updateAll(context)
        }
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()

    // The launcher's half-hourly update also moves the widget on to a new day.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DayClock.refresh()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/** Ticks or unticks a habit for today from the widget, or counts a counted habit up by one. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // A stale widget must never change data after the PIN is enabled.
        if (Settings(context).hasPin) return
        val habitId = parameters[HabitIdKey] ?: return
        val repository = (context.applicationContext as HabitudeApplication).repository
        // Tick the day the widget is about to show, never a stale one.
        DayClock.refresh()
        val today = DayClock.today()
        val item = repository.snapshot().firstOrNull { it.habit.id == habitId } ?: return
        if (Settings(context).hasPin) return
        // A counted habit adds one per tap; take one off in the app.
        if (item.habit.isCount) {
            repository.addCount(habitId, today, 1)
        } else {
            repository.setEntry(habitId, today, present = today !in item.entries)
        }
        HabitWidget().update(context, glanceId)
    }
}

@Composable
private fun LockedWidget() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Locked – open Habitude", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
    }
}

@Composable
private fun WidgetContent(items: List<HabitWithEntries>, today: LocalDate) {
    val builds = items.filter { !it.habit.archived && it.habit.type == HabitType.BUILD }
    val list = TodayList.order(builds, today).filter { TodayList.isDueToday(it, today) || today in it.entries }
    val progress = TodayList.progress(builds, today)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Today",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (progress.due > 0) {
                Text(
                    if (progress.done == progress.due) "All done 🎉" else "${progress.done} of ${progress.due}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (list.isEmpty()) {
            Text(
                if (builds.isEmpty()) "Add a habit in Habitude to see it here." else "Nothing due today. Enjoy the rest.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            LazyColumn {
                items(list, itemId = { it.habit.id }) { item ->
                    HabitRow(item, done = today in item.entries, count = item.countOn(today))
                }
            }
        }
    }
}

@Composable
private fun HabitRow(item: HabitWithEntries, done: Boolean, count: Int) {
    val target = item.habit.dailyTarget
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to item.habit.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.habit.emoji, style = TextStyle(fontSize = 18.sp))
        Spacer(GlanceModifier.width(8.dp))
        Text(
            item.habit.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (target != null) {
            Text(
                "$count/$target",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .cornerRadius(14.dp)
                .background(if (done) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Text(
                    "✓",
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            } else if (target != null) {
                Text(
                    "+",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
