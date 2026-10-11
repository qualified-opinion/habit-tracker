package com.janreins.habitude.ui.lock

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.janreins.habitude.domain.Pin
import com.janreins.habitude.domain.pinCooldown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Covers the whole app until the right PIN is entered. [initialFailures] and
 * [initialDeadlineElapsed] (milliseconds since boot) come from storage. Every attempt is reported
 * through [onAttempt], so closing the app doesn't reset the wait after too many wrong tries.
 */
@Composable
fun LockScreen(
    checkPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    initialFailures: Int,
    initialDeadlineElapsed: Long,
    bootCount: Int,
    onAttempt: (failures: Int, deadlineElapsed: Long, bootCount: Int) -> Unit,
    onUseBiometrics: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Storage already keeps these; restored UI state must not override a repaired reboot wait.
    var failures by remember { mutableIntStateOf(initialFailures) }
    var waitUntil by remember { mutableLongStateOf(initialDeadlineElapsed) }
    // Recompute derived time after recreation; do not restore a stale countdown.
    var secondsLeft by remember {
        val remaining = pinCooldown(failures, waitUntil, bootCount, SystemClock.elapsedRealtime(), bootCount).remainingMillis
        mutableIntStateOf(((remaining + 999) / 1000).toInt())
    }
    // The coroutine is cancelled on recreation, so its busy flag must start fresh too.
    var checking by remember { mutableStateOf(false) }
    var resetKey by rememberSaveable { mutableIntStateOf(0) }

    // With fingerprint or face unlock on, ask for it straight away, once each time the lock shows.
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (onUseBiometrics != null && !prompted) {
            prompted = true
            onUseBiometrics()
        }
    }

    // Back leaves the app rather than reaching the screens underneath.
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    LaunchedEffect(waitUntil) {
        while (true) {
            val left = pinCooldown(failures, waitUntil, bootCount, SystemClock.elapsedRealtime(), bootCount).remainingMillis
            secondsLeft = ((left + 999) / 1000).toInt()
            if (secondsLeft == 0) break
            delay(250)
        }
    }

    val message = when {
        secondsLeft > 0 -> "Too many tries. Wait ${secondsLeft}s."
        resetKey > 0 -> "Wrong PIN. Try again."
        else -> null
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Habitude",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinPad(
                    title = "Enter your PIN",
                    message = message,
                    isError = resetKey > 0 || secondsLeft > 0,
                    enabled = secondsLeft == 0 && !checking,
                    resetKey = resetKey,
                    onComplete = { pin ->
                        checking = true
                        scope.launch {
                            val ok = checkPin(pin)
                            checking = false
                            if (ok) {
                                failures = 0
                                waitUntil = 0L
                                onAttempt(0, 0L, bootCount)
                                onUnlocked()
                            } else {
                                failures++
                                resetKey++
                                val wait = Pin.lockoutSeconds(failures)
                                waitUntil = if (wait > 0) SystemClock.elapsedRealtime() + wait * 1000L else 0L
                                secondsLeft = wait
                                onAttempt(failures, waitUntil, bootCount)
                            }
                        }
                    },
                )
                if (onUseBiometrics != null) {
                    TextButton(onClick = onUseBiometrics) { Text("Use fingerprint or face") }
                }
            }
            Text(
                "Forgot your PIN? The only way back in is to clear Habitude's storage in Android settings, " +
                    "which also erases your habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
