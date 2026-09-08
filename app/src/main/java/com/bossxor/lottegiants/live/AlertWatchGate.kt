package com.bossxor.lottegiants.live

/**
 * 감시 FGS를 07–24시 상시가 아니라 등말소 시간·라인업 창에만 켠다.
 */
object AlertWatchGate {
    const val ROSTER_START_HOUR = 8
    const val ROSTER_END_HOUR = 23
    const val LINEUP_BEFORE_MS = 6 * 60 * 60_000L
    const val LINEUP_AFTER_MS = 30 * 60_000L

    fun shouldWatch(
        nowHour: Int,
        nowMillis: Long,
        lineupEnabled: Boolean,
        rosterEnabled: Boolean,
        gameStartMillis: Long?,
    ): Boolean {
        if (rosterEnabled && nowHour in ROSTER_START_HOUR until ROSTER_END_HOUR) return true
        if (lineupEnabled && gameStartMillis != null) {
            val from = gameStartMillis - LINEUP_BEFORE_MS
            val to = gameStartMillis + LINEUP_AFTER_MS
            if (nowMillis in from..to) return true
        }
        return false
    }
}
