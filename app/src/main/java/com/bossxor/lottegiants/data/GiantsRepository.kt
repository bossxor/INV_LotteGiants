package com.bossxor.lottegiants.data

import android.content.Context
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.RelayText
import com.bossxor.lottegiants.domain.TeamStanding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GiantsRepository private constructor(context: Context) {

    private val api: NaverSportsApi = NaverSportsApi.create()
    val store = SnapshotStore(context.applicationContext)

    /**
     * 오늘(및 다음 7일) 일정을 조회하고, 롯데 경기가 있으면 relay를 합쳐
     * 스냅샷을 만들어 저장한 뒤 반환한다.
     */
    suspend fun refreshSnapshot(): LiveSnapshot {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val games = api.getGames(
            fromDate = today.format(fmt),
            toDate = today.plusDays(7).format(fmt),
        ).result?.games.orEmpty().filter { it.categoryId == "kbo" }

        val todayGames = games.filter { it.gameDate == today.format(fmt) }
        val lotteToday = todayGames.firstOrNull { it.involvesLotte() }
        val otherGames = todayGames.filter { !it.involvesLotte() }.map { it.toMiniGame() }

        var lotteInfo = lotteToday?.toLotteBase()
        if (lotteToday != null && lotteInfo != null) {
            // 경기 전이라도 라인업이 미리 뜰 수 있으니 relay는 항상 시도
            val relay = runCatching { api.getRelay(lotteToday.gameId).result?.textRelayData }.getOrNull()
            if (relay != null) {
                lotteInfo = mergeRelay(lotteInfo, relay)
            }
        }

        val nextLotte = games
            .filter { it.gameDate > today.format(fmt) && it.involvesLotte() && !it.cancel }
            .minByOrNull { it.gameDateTime }
            ?.toLotteBase()

        val snapshot = LiveSnapshot(
            updatedAtMillis = System.currentTimeMillis(),
            lotteGame = lotteInfo,
            nextLotteGame = nextLotte,
            otherGames = otherGames,
        )
        store.saveSnapshot(snapshot)
        return snapshot
    }

    suspend fun fetchStandings(): List<TeamStanding> {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        return api.getStandings(season.toString()).result?.seasonTeamStats.orEmpty()
            .map {
                TeamStanding(
                    teamId = it.teamId,
                    teamName = it.teamName,
                    ranking = it.ranking,
                    wra = it.wra,
                    gameCount = it.gameCount,
                    win = it.winGameCount,
                    draw = it.drawnGameCount,
                    lose = it.loseGameCount,
                    gameBehind = it.gameBehind,
                    streak = it.continuousGameResult.orEmpty(),
                    lastFive = it.lastFiveGames.orEmpty(),
                )
            }
            .sortedBy { it.ranking }
    }

    private fun GameDto.involvesLotte() =
        homeTeamCode == LOTTE_TEAM_CODE || awayTeamCode == LOTTE_TEAM_CODE

    private fun GameDto.status(): GameStatus = when {
        cancel -> GameStatus.CANCELED
        statusCode == "RESULT" || statusNum == 4 -> GameStatus.ENDED
        statusCode == "BEFORE" || statusNum == 1 -> GameStatus.BEFORE
        else -> GameStatus.LIVE
    }

    private fun GameDto.startTimeText(): String = runCatching {
        LocalDateTime.parse(gameDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")

    private fun GameDto.toMiniGame() = MiniGame(
        gameId = gameId,
        homeName = homeTeamName,
        awayName = awayTeamName,
        homeScore = homeTeamScore,
        awayScore = awayTeamScore,
        status = status(),
        statusText = statusInfo?.takeIf { it.isNotBlank() } ?: when (status()) {
            GameStatus.BEFORE -> startTimeText()
            GameStatus.CANCELED -> "취소"
            GameStatus.ENDED -> "종료"
            GameStatus.LIVE -> "진행 중"
        },
        stadium = stadium.orEmpty(),
        startTime = startTimeText(),
    )

    private fun GameDto.toLotteBase(): LotteGameInfo {
        val isHome = homeTeamCode == LOTTE_TEAM_CODE
        return LotteGameInfo(
            gameId = gameId,
            gameDate = gameDate,
            startTime = startTimeText(),
            stadium = stadium.orEmpty(),
            isHome = isHome,
            opponentCode = if (isHome) awayTeamCode else homeTeamCode,
            opponentName = if (isHome) awayTeamName else homeTeamName,
            lotteScore = if (isHome) homeTeamScore else awayTeamScore,
            opponentScore = if (isHome) awayTeamScore else homeTeamScore,
            status = status(),
            statusText = statusInfo.orEmpty(),
            broadChannel = broadChannel.orEmpty(),
            lotteStartingPitcher = (if (isHome) homeStarterName else awayStarterName).orEmpty(),
            opponentStartingPitcher = (if (isHome) awayStarterName else homeStarterName).orEmpty(),
            currentPitcherName = (if (isHome) awayCurrentPitcherName else homeCurrentPitcherName).orEmpty(),
            winPitcherName = winPitcherName.orEmpty(),
            losePitcherName = losePitcherName.orEmpty(),
        )
    }

    private fun mergeRelay(base: LotteGameInfo, relay: TextRelayData): LotteGameInfo {
        val isHome = base.isHome
        val lotteLineupDto = if (isHome) relay.homeLineup else relay.awayLineup
        val oppLineupDto = if (isHome) relay.awayLineup else relay.homeLineup

        // pcode -> 이름 매핑 (라인업 + 엔트리 전체)
        val names = buildMap {
            listOfNotNull(relay.homeLineup, relay.awayLineup).forEach { lu ->
                lu.batter.forEach { put(it.pcode, it.name) }
                lu.pitcher.forEach { put(it.pcode, it.name) }
            }
            listOfNotNull(relay.homeEntry, relay.awayEntry).forEach { e ->
                e.batter.forEach { put(it.pcode, it.name) }
                e.pitcher.forEach { put(it.pcode, it.name) }
            }
        }

        // 타순별 현재 타자 (교체 반영: seqno가 가장 큰 선수)
        fun LineupDto.currentByOrder(): Map<Int, LineupBatterDto> =
            batter.filter { it.batOrder in 1..9 }
                .groupBy { it.batOrder }
                .mapValues { (_, list) -> list.maxBy { it.seqno } }

        val lotteOrder = lotteLineupDto?.currentByOrder().orEmpty()
        val lotteLineup = lotteOrder.entries.sortedBy { it.key }.map { (order, b) ->
            LineupSlot(
                batOrder = order,
                name = b.name,
                position = b.posName.orEmpty(),
                seasonAvg = b.seasonHra,
                todayHits = b.hit,
                todayAtBats = b.ab,
                isSubstitute = b.seqno > 1,
            )
        }

        val state = relay.currentGameState
        val isTop = relay.homeOrAway != "1" // "0"=초(원정 공격), "1"=말(홈 공격)
        val isLotteBatting = if (isHome) !isTop else isTop

        // 현재 타자와 다음 타자 (공격 팀 라인업 기준)
        val battingLineupDto = if (isTop) {
            if (isHome) oppLineupDto else lotteLineupDto
        } else {
            if (isHome) lotteLineupDto else oppLineupDto
        }
        val battingOrder = battingLineupDto?.currentByOrder().orEmpty()
        val batterCode = state?.batter.orEmpty()
        val currentBatter = battingOrder.values.firstOrNull { it.pcode == batterCode }
        val nextBatter = currentBatter?.let { battingOrder[(it.batOrder % 9) + 1] }

        val inningScores = relay.inningScore
        fun Map<String, String>.ordered(): List<String> =
            entries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
                .sortedBy { it.first }.map { it.second }

        val texts = relay.textRelays
            .flatMap { tr -> tr.textOptions.map { RelayText(it.seqno, it.text, it.type, tr.inn) } }
            .filter { it.type != 99 && it.text.isNotBlank() }
            .sortedByDescending { it.seqno }
            .take(40)

        val pitcherCode = state?.pitcher.orEmpty()
        return base.copy(
            lotteScore = state?.run { if (isHome) homeScore else awayScore }?.toIntOrNull() ?: base.lotteScore,
            opponentScore = state?.run { if (isHome) awayScore else homeScore }?.toIntOrNull() ?: base.opponentScore,
            inning = relay.inn,
            isTopInning = isTop,
            strike = state?.strike?.toIntOrNull() ?: 0,
            ball = state?.ball?.toIntOrNull() ?: 0,
            out = state?.out?.toIntOrNull() ?: 0,
            onBase1 = state?.base1 == "1",
            onBase2 = state?.base2 == "1",
            onBase3 = state?.base3 == "1",
            currentPitcherName = names[pitcherCode] ?: base.currentPitcherName,
            currentPitcherCode = pitcherCode,
            currentBatterName = names[batterCode] ?: "",
            currentBatterOrder = currentBatter?.batOrder ?: 0,
            nextBatterName = nextBatter?.name.orEmpty(),
            isLotteBatting = isLotteBatting,
            lotteStartingPitcher = lotteLineupDto?.pitcher?.minByOrNull { it.seqno }?.name
                ?: base.lotteStartingPitcher,
            opponentStartingPitcher = oppLineupDto?.pitcher?.minByOrNull { it.seqno }?.name
                ?: base.opponentStartingPitcher,
            lotteLineup = lotteLineup,
            lotteInningScores = (if (isHome) inningScores?.home else inningScores?.away)?.ordered().orEmpty(),
            opponentInningScores = (if (isHome) inningScores?.away else inningScores?.home)?.ordered().orEmpty(),
            lotteHits = state?.run { if (isHome) homeHit else awayHit }?.toIntOrNull() ?: 0,
            opponentHits = state?.run { if (isHome) awayHit else homeHit }?.toIntOrNull() ?: 0,
            lotteErrors = state?.run { if (isHome) homeError else awayError }?.toIntOrNull() ?: 0,
            opponentErrors = state?.run { if (isHome) awayError else homeError }?.toIntOrNull() ?: 0,
            lotteBb = state?.run { if (isHome) homeBallFour else awayBallFour }?.toIntOrNull() ?: 0,
            opponentBb = state?.run { if (isHome) awayBallFour else homeBallFour }?.toIntOrNull() ?: 0,
            recentTexts = texts,
        )
    }

    companion object {
        @Volatile
        private var instance: GiantsRepository? = null

        fun get(context: Context): GiantsRepository =
            instance ?: synchronized(this) {
                instance ?: GiantsRepository(context.applicationContext).also { instance = it }
            }
    }
}
