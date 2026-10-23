package com.janreins.habitude.domain

import java.time.Duration
import java.time.ZonedDateTime

object Days {
    /**
     * A second after the next midnight, so the date has surely changed. On a day when the
     * clocks skip midnight, it's a second after the day's first moment instead.
     */
    fun justAfterMidnight(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(1)

    /** Milliseconds from [now] until [justAfterMidnight]. */
    fun millisUntilTomorrow(now: ZonedDateTime): Long = Duration.between(now, justAfterMidnight(now)).toMillis()
}
