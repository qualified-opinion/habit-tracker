package com.janreins.habitude.ui.charts

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.janreins.habitude.domain.WeekStat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeeklyBarsSemanticsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun eachBarWithDataHasANode() {
        val mon = LocalDate.of(2026, 10, 5)
        val weeks = listOf(WeekStat(mon.minusWeeks(2), null), WeekStat(mon.minusWeeks(1), 0f), WeekStat(mon, 1f))
        compose.setContent {
            MaterialTheme {
                WeeklyBars(weeks, Color.Green, 1f, listOf(0f, 1f), { "$it" }, { "Week ${it.weekStart}" })
            }
        }
        compose.onNodeWithContentDescription("Week ${weeks[0].weekStart}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Week ${weeks[1].weekStart}").assertHasClickAction()
        val node = compose.onNodeWithContentDescription("Week $mon")
        node.performClick().assertIsSelected()
        compose.onNodeWithText("Week $mon").assertIsDisplayed()
        node.performTouchInput { click() }.assertIsNotSelected()
        compose.onNodeWithText("Tap a week to see it").assertIsDisplayed()
    }
}
