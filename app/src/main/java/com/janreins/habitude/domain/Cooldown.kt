package com.janreins.habitude.domain

/** A PIN wait measured since boot, with the values to keep for the next read. */
data class PinCooldown(val remainingMillis: Long, val deadlineElapsed: Long, val bootCount: Int)

/**
 * Wall-clock changes cannot affect this wait. A pending wait from another boot restarts in
 * full; a deadline too far ahead is shortened to the maximum earned by these failures.
 * A zero deadline means there is no pending wait, even after a reboot.
 */
fun pinCooldown(
    failures: Int,
    storedDeadlineElapsed: Long,
    storedBootCount: Int,
    nowElapsed: Long,
    currentBootCount: Int,
): PinCooldown {
    val maximum = Pin.lockoutSeconds(failures) * 1000L
    if (maximum == 0L || storedDeadlineElapsed <= 0L) return PinCooldown(0L, 0L, currentBootCount)
    val remaining = if (storedBootCount != currentBootCount) {
        maximum
    } else {
        (storedDeadlineElapsed - nowElapsed).coerceIn(0L, maximum)
    }
    val deadline = if (remaining == 0L) 0L else nowElapsed + remaining
    return PinCooldown(remaining, deadline, currentBootCount)
}
