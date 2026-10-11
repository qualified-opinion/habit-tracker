package com.janreins.habitude.notify

import android.app.Application
import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.test.core.app.ApplicationProvider
import com.janreins.habitude.domain.PinCooldown
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsCooldownTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val settings = Settings(context)

    @Test
    fun legacyWaitRestartsOnlyOnceThenCountsDown() {
        prefs.edit().putInt("pin_failures", 5).putLong("pin_locked_until", 9_999_999L).apply()
        AndroidSettings.Global.putInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, 7)
        val first = settings.readPinCooldown()
        assertEquals(30_000, first.remainingMillis)
        assertEquals(false, prefs.contains("pin_locked_until"))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        assertEquals(20_000, settings.readPinCooldown().remainingMillis)
    }

    @Test
    fun legacyWaitUnderFiveFailuresIsCleared() {
        prefs.edit().putInt("pin_failures", 4).putLong("pin_locked_until", 9_999_999L).apply()
        assertEquals(0, settings.readPinCooldown().remainingMillis)
        assertEquals(false, prefs.contains("pin_locked_until"))
    }

    @Test
    fun rebootRepairIsPersistedWithoutRestartingAgain() {
        settings.savePinCooldown(6, 100_000, 7)
        AndroidSettings.Global.putInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, 8)
        val repaired = settings.readPinCooldown()
        assertEquals(60_000, repaired.remainingMillis)
        assertEquals(8, repaired.bootCount)
        assertEquals(repaired, settings.readPinCooldown())
    }

    @Test
    fun successfulAuthenticationClearsWaitAcrossBoots() {
        settings.savePinCooldown(0, 0, 7)
        AndroidSettings.Global.putInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, 8)
        assertEquals(PinCooldown(0, 0, 8), settings.readPinCooldown())
    }
}
