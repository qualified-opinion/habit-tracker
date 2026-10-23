package com.janreins.habitude.ui.edit

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HabitPickersSemanticsTest {
    @get:Rule val compose = createComposeRule()
    private val locale = Locale.getDefault()

    @Before fun english() = Locale.setDefault(Locale.UK)
    @After fun restore() = Locale.setDefault(locale)

    @Test fun daysReadTheirFullNameAndWhetherTheyreSelected() {
        val toggled = mutableListOf<DayOfWeek>()
        compose.setContent {
            MaterialTheme { DayPicker(schedule = setOf(DayOfWeek.TUESDAY), onToggle = { toggled += it }) }
        }
        compose.onNodeWithContentDescription("Tuesday").assertIsSelected()
        compose.onNodeWithContentDescription("Thursday").assertIsNotSelected().performClick()
        assertEquals(listOf(DayOfWeek.THURSDAY), toggled)
        // The single letters aren't read out as well.
        compose.onAllNodesWithText("T", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun onlyTheChosenIconIsSelected() {
        var picked: String? = null
        compose.setContent {
            MaterialTheme { EmojiPicker(selected = HabitEmojis[1], isBuild = true, onPick = { picked = it }) }
        }
        compose.onNodeWithText(HabitEmojis[1]).assertIsSelected()
        compose.onNodeWithText(HabitEmojis[0]).assertIsNotSelected().performClick()
        assertEquals(HabitEmojis[0], picked)
    }
}
