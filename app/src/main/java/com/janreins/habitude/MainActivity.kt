package com.janreins.habitude

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.janreins.habitude.domain.Days
import com.janreins.habitude.ui.CelebrationHost
import com.janreins.habitude.ui.DayClock
import com.janreins.habitude.ui.lock.Biometrics
import com.janreins.habitude.ui.lock.LockScreen
import com.janreins.habitude.ui.lock.LocalAppLocked
import com.janreins.habitude.ui.navigation.HabitudeApp
import com.janreins.habitude.ui.theme.HabitudeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/**
 * Lock state that survives screen rotation but not the app being closed, so a restart
 * always asks for the PIN again.
 */
class LockState : ViewModel() {
    var locked by mutableStateOf<Boolean?>(null)

    /** When the app last went to the background, for the lock timeout. */
    var leftAt = 0L
}

/** A FragmentActivity (still a ComponentActivity) so it can show the fingerprint or face prompt. */
class MainActivity : FragmentActivity() {
    private val settings by lazy { (application as HabitudeApplication).settings }
    private val lock: LockState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (lock.locked == null) lock.locked = settings.hasPin
        // Keep "today" current: refresh whenever the app is in front, and again at each midnight.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    DayClock.refresh()
                    delay(Days.millisUntilTomorrow(ZonedDateTime.now()))
                }
            }
        }
        setContent {
            HabitudeTheme {
                Box {
                    // The app stays composed underneath so you land back where you were.
                    CompositionLocalProvider(LocalAppLocked provides (lock.locked == true)) {
                        HabitudeApp()
                    }
                    // Milestones wait until the app is unlocked, so they never show over the PIN screen.
                    if (lock.locked == false) CelebrationHost()
                    if (lock.locked == true) {
                        val cooldown = remember { settings.readPinCooldown() }
                        LockScreen(
                            checkPin = { pin -> withContext(Dispatchers.Default) { settings.checkPin(pin) } },
                            onUnlocked = { lock.locked = false },
                            initialFailures = settings.pinFailures,
                            initialDeadlineElapsed = cooldown.deadlineElapsed,
                            bootCount = cooldown.bootCount,
                            onAttempt = settings::savePinCooldown,
                            onUseBiometrics = if (settings.biometricUnlock && Biometrics.available(this@MainActivity)) {
                                {
                                    Biometrics.prompt(this@MainActivity, "Unlock Habitude") {
                                        settings.savePinCooldown(0, 0L, cooldown.bootCount)
                                        lock.locked = false
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hideFromRecents(settings.hasPin)
        val away = SystemClock.elapsedRealtime() - lock.leftAt
        if (settings.hasPin && lock.leftAt != 0L && away > LOCK_AFTER_MS) lock.locked = true
    }

    override fun onPause() {
        // Checked again here in case the PIN was just turned on or off in Settings.
        hideFromRecents(settings.hasPin)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lock.leftAt = SystemClock.elapsedRealtime()
    }

    /** With a PIN set, the recent-apps screen shows a blank card instead of your habits. */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        /** A short grace period, so popping out to pick a backup file doesn't lock you out. */
        const val LOCK_AFTER_MS = 60_000L
    }
}
