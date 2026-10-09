package com.bossxor.lottegiants.live

/**
 * 감시 FGS를 07–24시 상시가 아니라 등말소 시간·라인업 창에만 켠다.
 */
object AlertWatchGate {
    const val ROSTER_START_HOUR = 8
    const val ROSTER_END_HOUR = 23
    const val LINEUP_BEFORE_MS = 6 * 60 * 60_000L
    const val LINEUP_AFTER_MS = 30 * 60_000L
    const val LINEUP_POLL_MS = 10_000L
    const val ROSTER_POLL_MS = 30_000L
    const val ROSTER_FAST_MS = 10_000L

    fun inLineupWindow(nowMillis: Long, gameStartMillis: Long?): Boolean {
        if (gameStartMillis == null) return false
        val from = gameStartMillis - LINEUP_BEFORE_MS
        val to = gameStartMillis + LINEUP_AFTER_MS
        return nowMillis in from..to
    }

    fun pollIntervalMs(nowMillis: Long, gameStartMillis: Long?): Long =
        if (inLineupWindow(nowMillis, gameStartMillis)) LINEUP_POLL_MS else ROSTER_POLL_MS

    fun rosterIntervalMs(hour: Int): Long = if (hour in 13..18) ROSTER_FAST_MS else ROSTER_POLL_MS

    fun lineupIntervalMs(gameId: String?, confirmedKey: String): Long =
        if (!gameId.isNullOrBlank() && confirmedKey == "$gameId:full") 60_000L else LINEUP_POLL_MS

    fun watchGame(snapshot: com.bossxor.lottegiants.domain.LiveSnapshot?): com.bossxor.lottegiants.domain.LotteGameInfo? {
        val games = listOfNotNull(snapshot?.lotteGame, snapshot?.nextLotteGame)
        return games.filter { it.status == com.bossxor.lottegiants.domain.GameStatus.BEFORE }
            .minByOrNull { "${it.gameDate} ${it.startTime}" }
            ?: games.firstOrNull { it.status == com.bossxor.lottegiants.domain.GameStatus.LIVE }
    }

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
