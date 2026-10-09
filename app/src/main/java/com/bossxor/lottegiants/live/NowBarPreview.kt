package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.GamePreview
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.PreviewTeamLine

/** 설정의 「미리보기」: 경기가 없어도 라이브 바 세 가지 모양을 알림으로 띄워 본다. */
object NowBarPreview {
    enum class State { BEFORE, LIVE, ENDED }

    private fun base(status: GameStatus) = LotteGameInfo(
        gameId = "preview", gameDate = "2026-10-03", startTime = "14:00", stadium = "광주", isHome = false,
        opponentCode = "HT", opponentName = "KIA", status = status,
        lotteStartingPitcher = "박세웅", opponentStartingPitcher = "네일", lotteRank = 8, opponentRank = 4,
        preview = GamePreview(
            lotteStanding = PreviewTeamLine(wra = 0.480),
            opponentStanding = PreviewTeamLine(wra = 0.540),
        ),
    )

    suspend fun post(context: Context, state: State, home: Boolean = false,
        awayScore: Int? = null, homeScore: Int? = null) {
        val app = context.applicationContext
        NotificationHelper.createChannels(app)
        val next = base(GameStatus.BEFORE).copy(
            gameId = "preview-next", gameDate = "2026-10-04", opponentStartingPitcher = "올러", lotteStartingPitcher = "데이비슨",
        )
        val game0 = when (state) {
            State.BEFORE -> base(GameStatus.BEFORE)
            State.LIVE -> base(GameStatus.LIVE).copy(
                lotteScore = 3, opponentScore = 2, inning = 7, isTopInning = true, isLotteBatting = false,
                out = 2, ball = 2, strike = 1, onBase1 = true, onBase3 = true,
                runnerOn1Order = 3, runnerOn3Order = 5,
                currentPitcherName = "박세웅", currentPitcherPitchCount = 85,
                currentBatterName = "최형우", currentBatterOrder = 4,
                lotteInningScores = listOf("0", "0", "1", "0", "0", "2", "0"),
                opponentInningScores = listOf("0", "1", "0", "0", "0", "0", "1"),
            )
            State.ENDED -> base(GameStatus.ENDED).copy(
                lotteScore = 4, opponentScore = 2,
                winPitcherName = "박세웅", losePitcherName = "네일", savePitcherName = "김원중",
                lotteInningScores = listOf("0", "0", "1", "0", "0", "2", "0", "1", "0"),
                opponentInningScores = listOf("0", "1", "0", "0", "0", "0", "1", "0", "0"),
            )
        }
        // 롯데가 홈이면 원정=상대(KIA)가 왼쪽에 오는지 확인용.
        val oriented = if (home) game0.copy(isHome = true) else game0
        val game = oriented.copy(
            lotteScore = (if (home) homeScore else awayScore)?.coerceIn(0, 99) ?: oriented.lotteScore,
            opponentScore = (if (home) awayScore else homeScore)?.coerceIn(0, 99) ?: oriented.opponentScore,
        )
        NotificationHelper.warmLiveLogos(app, game)
        val n = NotificationHelper.buildLiveNotification(app, game, LiveDisplayMode.LOCK_NOW, nextGame = next)
        NotificationHelper.notifyLive(app, n, "preview-${state.name}", force = true)
    }
}
