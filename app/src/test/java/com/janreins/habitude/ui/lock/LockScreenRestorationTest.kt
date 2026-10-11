package com.janreins.habitude.ui.lock

import android.app.Application
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LockScreenRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun interruptedPinCheckDoesNotRestoreDisabledKeypad() {
        var checks = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                LockScreen(
                    checkPin = { checks++; awaitCancellation() },
                    onUnlocked = {},
                    initialFailures = 0,
                    initialDeadlineElapsed = 0,
                    bootCount = 7,
                    onAttempt = { _, _, _ -> },
                )
            }
        }
        repeat(6) { compose.onNodeWithText("1").performClick() }
        compose.runOnIdle { assertEquals(1, checks) }
        restoration.emulateSavedInstanceStateRestore()
        repeat(6) { compose.onNodeWithText("1").performClick() }
        compose.runOnIdle { assertEquals(2, checks) }
    }

    @Test
    fun restorationUsesCooldownReloadedFromStorage() {
        var failures = 0
        var deadline = 0L
        var boot = 7
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                LockScreen(
                    checkPin = { false },
                    onUnlocked = {},
                    initialFailures = failures,
                    initialDeadlineElapsed = deadline,
                    bootCount = boot,
                    onAttempt = { _, _, _ -> },
                )
            }
        }
        // Preferences repaired after a reboot take precedence over the old saved UI state.
        compose.runOnIdle {
            failures = 5
            deadline = SystemClock.elapsedRealtime() + 30_000L
            boot = 8
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Too many tries. Wait 30s.").assertExists()
        compose.onNodeWithText("1").assertIsNotEnabled()
    }

}
