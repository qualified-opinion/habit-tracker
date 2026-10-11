package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CooldownTest {
    @Test
    fun countdownUsesOnlyElapsedTime() {
        assertEquals(PinCooldown(20_000, 40_000, 7), pinCooldown(5, 40_000, 7, 20_000, 7))
        assertEquals(PinCooldown(10_000, 40_000, 7), pinCooldown(5, 40_000, 7, 30_000, 7))
        assertEquals(PinCooldown(0, 0, 7), pinCooldown(5, 40_000, 7, 40_000, 7))
    }

    @Test
    fun wallClockForwardCannotSkipWait() {
        // The deadline and all inputs are since boot; no wall-clock value enters the calculation.
        val before = pinCooldown(5, 40_000, 7, 20_000, 7)
        val after = pinCooldown(5, 40_000, 7, 20_000, 7)
        assertEquals(20_000, after.remainingMillis)
        assertEquals(before, after)
    }

    @Test
    fun rebootRestartsFullWaitAndPersistsNewBoot() {
        assertEquals(PinCooldown(60_000, 65_000, 8), pinCooldown(6, 40_000, 7, 5_000, 8))
    }

    @Test
    fun firstFourFailuresNeverWait() {
        assertEquals(PinCooldown(0, 0, 8), pinCooldown(4, 40_000, 7, 5_000, 8))
    }

    @Test
    fun excessiveDeadlineIsCappedAndRepaired() {
        assertEquals(PinCooldown(30_000, 50_000, 7), pinCooldown(5, Long.MAX_VALUE, 7, 20_000, 7))
    }

    @Test
    fun clearedWaitStaysClearedAfterReboot() {
        assertEquals(PinCooldown(0, 0, 8), pinCooldown(5, 0, 7, 5_000, 8))
    }
}
