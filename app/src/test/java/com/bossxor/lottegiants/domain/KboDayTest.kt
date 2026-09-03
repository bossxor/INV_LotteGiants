package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class KboDayTest {

    private val zone: ZoneId = KBO_ZONE

    private fun at(date: String, time: String): ZonedDateTime =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone)

    private fun game(
        date: String,
        status: GameStatus,
    ) = LotteGameInfo(
        gameId = "1",
        gameDate = date,
        startTime = "18:30",
        stadium = "사직",
        isHome = true,
        opponentCode = "SS",
        opponentName = "삼성",
        status = status,
    )

    @Test
    fun todayStaysYesterdayUntil5am() {
        assertEquals(LocalDate.parse("2026-09-02"), kboToday(at("2026-09-03", "04:59:59")))
        assertEquals(LocalDate.parse("2026-09-03"), kboToday(at("2026-09-03", "05:00:00")))
        assertEquals(LocalDate.parse("2026-09-03"), kboToday(at("2026-09-03", "16:49:00")))
    }

    @Test
    fun snapshotFromBeforeRolloverIsStaleAfter5am() {
        val before = at("2026-09-03", "04:50").toInstant().toEpochMilli()
        assertFalse(snapshotStaleForKboDay(before, at("2026-09-03", "04:59")))
        assertTrue(snapshotStaleForKboDay(before, at("2026-09-03", "05:00")))
        val after = at("2026-09-03", "05:01").toInstant().toEpochMilli()
        assertFalse(snapshotStaleForKboDay(after, at("2026-09-03", "16:49")))
    }

    @Test
    fun endedYesterdayIsNotCurrentAfter5am() {
        val ended = game("2026-09-02", GameStatus.ENDED)
        assertTrue(ended.belongsToKboToday("2026-09-02"))
        assertFalse(ended.belongsToKboToday("2026-09-03"))
    }

    @Test
    fun liveOvernightStillCountsAsCurrent() {
        val live = game("2026-09-02", GameStatus.LIVE)
        assertTrue(live.belongsToKboToday("2026-09-03"))
    }
}
