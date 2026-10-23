package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DaysTest {
    private val london = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId = london) = ZonedDateTime.of(LocalDateTime.parse(text), zone)

    private val hour = 3_600_000L

    @Test fun anOrdinaryEvening() {
        assertEquals(hour + 1_000, Days.millisUntilTomorrow(at("2026-10-23T23:00")))
    }

    @Test fun exactlyAtMidnightWaitsForTheNextOne() {
        assertEquals(24 * hour + 1_000, Days.millisUntilTomorrow(at("2026-10-23T00:00")))
    }

    @Test fun theDayTheClocksGoForwardIsAnHourShorter() {
        // 29 March 2026: 1am jumps to 2am in the UK.
        assertEquals(22 * hour + hour / 2 + 1_000, Days.millisUntilTomorrow(at("2026-03-29T00:30")))
    }

    @Test fun theDayTheClocksGoBackIsAnHourLonger() {
        // 25 October 2026: 2am goes back to 1am in the UK.
        assertEquals(24 * hour + hour / 2 + 1_000, Days.millisUntilTomorrow(at("2026-10-25T00:30")))
    }

    @Test fun whenMidnightIsSkippedItWaitsForTheDaysFirstMoment() {
        // 4 November 2018 in São Paulo: midnight jumped straight to 1am.
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val next = Days.justAfterMidnight(at("2018-11-03T22:00", saoPaulo))
        assertEquals(LocalDateTime.parse("2018-11-04T01:00:01"), next.toLocalDateTime())
        assertEquals(2 * hour + 1_000, Days.millisUntilTomorrow(at("2018-11-03T22:00", saoPaulo)))
    }

    @Test fun justAfterMidnightIsAlwaysTomorrow() {
        val now = at("2026-12-31T23:59:59")
        assertEquals(LocalDate.of(2027, 1, 1), Days.justAfterMidnight(now).toLocalDate())
    }
}
