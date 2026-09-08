package com.bossxor.lottegiants.live

/**
 * 감시 FGS를 07–24시 상시가 아니라 등말소 시간·라인업 창에만 켠다.
 */
object AlertWatchGate {
    const val ROSTER_START_HOUR = 14
    const val ROSTER_END_HOUR = 23
    const val LINEUP_BEFORE_MS = 6 * 60 * 60_000L
    const val LINEUP_AFTER_MS = 30 * 60_000L
    const val LINEUP_POLL_MS = 15_000L
    const val ROSTER_POLL_MS = 45_000L

    fun inLineupWindow(nowMillis: Long, gameStartMillis: Long?): Boolean {
        if (gameStartMillis == null) return false
        val from = gameStartMillis - LINEUP_BEFORE_MS
        val to = gameStartMillis + LINEUP_AFTER_MS
        return nowMillis in from..to
    }

    fun pollIntervalMs(nowMillis: Long, gameStartMillis: Long?): Long =
        if (inLineupWindow(nowMillis, gameStartMillis)) LINEUP_POLL_MS else ROSTER_POLL_MS

    fun shouldWatch(
        nowHour: Int,
        nowMillis: Long,
        lineupEnabled: Boolean,
        rosterEnabled: Boolean,
        gameStartMillis: Long?,
    ): Boolean {
        if (rosterEnabled && nowHour in ROSTER_START_HOUR until ROSTER_END_HOUR) return true
        if (lineupEnabled && inLineupWindow(nowMillis, gameStartMillis)) return true
        return false
    }
}
