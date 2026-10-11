package com.janreins.habitude.ui.lock

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.janreins.habitude.ui.detail.NoteDialog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLockTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun noteDialogIsHiddenWhileLockedAndReturnsWithDraft() {
        val locked = mutableStateOf(true)
        var saved: String? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppLocked provides locked.value) {
                    NoteDialog(LocalDate.of(2026, 1, 1), "Original", "Note", { saved = it }, {})
                }
            }
        }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.runOnIdle { locked.value = false }
        compose.onNodeWithText("Original").performTextReplacement("Draft")
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText("Draft").assertDoesNotExist()
        assertEquals(null, saved)
        compose.runOnIdle { locked.value = false }
        compose.onNodeWithText("Draft").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        assertEquals("Draft", saved)
    }

    @Test
    fun platformPickerHidesWithoutDismissingPendingStateOrAllowingActions() {
        val locked = mutableStateOf(false)
        lateinit var dialog: DatePickerDialog
        lateinit var canAct: () -> Boolean
        var dismissals = 0
        compose.setContent {
            val context = LocalContext.current
            CompositionLocalProvider(LocalAppLocked provides locked.value) {
                LockAwarePlatformDialog(onDismiss = { dismissals++ }) { guard ->
                    canAct = guard
                    DatePickerDialog(context, null, 2026, 0, 1).also { dialog = it }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(dialog.isShowing)
            dialog.updateDate(2026, 1, 2)
            locked.value = true
        }
        compose.runOnIdle {
            assertFalse(dialog.isShowing)
            assertFalse(canAct())
            assertEquals(0, dismissals)
            locked.value = false
        }
        compose.runOnIdle {
            assertTrue(dialog.isShowing)
            assertTrue(canAct())
            assertEquals(1, dialog.datePicker.month)
            assertEquals(2, dialog.datePicker.dayOfMonth)
            dialog.dismiss()
        }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
