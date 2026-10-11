package com.janreins.habitude.ui.charts

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.Stats
import com.janreins.habitude.ui.DayClock
import com.janreins.habitude.ui.detail.HabitStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalendarHeatmapSemanticsTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 8)
    private val firstDay = Stats.weekStart(today).minusWeeks(1)
    private fun render() {
        compose.setContent {
            MaterialTheme {
                CalendarHeatmap(today, 2, { HeatCell(Color.Green) }, { "Day $it" }, action = { Text("Action $it") })
            }
        }
    }
    @Test fun eachPastDayHasADescribedNode() {
        render()
        for (offset in 0L..10L) {
            compose.onNodeWithContentDescription("Day ${firstDay.plusDays(offset)}").assertHasClickAction()
        }
        compose.onNodeWithContentDescription("Day ${firstDay.minusDays(1)}").assertDoesNotExist()
    }
    @Test fun futureDaysHaveNoNode() {
        render()
        for (offset in 1L..3L) compose.onNodeWithContentDescription("Day ${today.plusDays(offset)}").assertDoesNotExist()
    }
    @Test fun clickingANodeSelectsItAndShowsTheAction() {
        render()
        val node = compose.onNodeWithContentDescription("Day $today")
        node.performClick().assertIsSelected()
        compose.onNodeWithText("Action $today").assertIsDisplayed()
        // Touch goes through the same overlay and toggles the same selection as TalkBack.
        node.performTouchInput { click() }.assertIsNotSelected()
        compose.onNodeWithText("Action $today").assertDoesNotExist()
    }
    @Test fun detailStatsWithTwentyWeeksScrollsPastDayNodesToContentBelow() {
        val realToday = DayClock.today()
        val first = Stats.weekStart(realToday).minusWeeks(19)
        val item = HabitWithEntries(
            Habit(1, "Read", "📚", HabitType.BUILD, createdOn = realToday.minusDays(200)),
            setOf(realToday.minusDays(1)),
        )
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HabitStats(item, { _, _ -> }, { _, _ -> }, { _, _ -> })
                }
            }
        }
        val dayCells = SemanticsMatcher("heatmap day cell") {
            it.config.contains(SemanticsProperties.CollectionItemInfo) &&
                it.config[SemanticsProperties.ContentDescription].single().contains(" · ") &&
                !it.config[SemanticsProperties.ContentDescription].single().contains("planned days")
        }
        // The real detail content hosts the 20-week grid, not a reduced test-only chart.
        val expected = java.time.temporal.ChronoUnit.DAYS.between(first, realToday).toInt() + 1
        val heatmapCells = compose.onAllNodes(dayCells).fetchSemanticsNodes().filter {
            it.config[SemanticsProperties.CollectionItemInfo].rowIndex in 0..6 &&
                it.config[SemanticsProperties.CollectionItemInfo].columnIndex in 0..19
        }
        org.junit.Assert.assertEquals(expected, heatmapCells.size)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("STREAK HISTORY"))
        compose.onNodeWithText("STREAK HISTORY").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("NOTES"))
        compose.onNodeWithText("NOTES").assertIsDisplayed()
    }
}
