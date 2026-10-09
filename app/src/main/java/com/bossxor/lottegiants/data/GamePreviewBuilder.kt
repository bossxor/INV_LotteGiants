package com.bossxor.lottegiants.data

import android.content.Context
import com.bossxor.lottegiants.domain.DayEntryChanges
import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.GamePreview
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.KeyPlay
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.MatchupRecord
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.PlayerDetail
import com.bossxor.lottegiants.domain.HotColdZone
import com.bossxor.lottegiants.domain.PreviewBatter
import com.bossxor.lottegiants.domain.PreviewPitcher
import com.bossxor.lottegiants.domain.PreviewTeamLine
import com.bossxor.lottegiants.domain.RecentFormGame
import com.bossxor.lottegiants.domain.RelayText
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.StadiumWeather
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.WinProb
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.cancelDisplayLabel
import com.bossxor.lottegiants.domain.estimateLotteWinProb
import com.bossxor.lottegiants.domain.isDelayText
import com.bossxor.lottegiants.domain.parseResumeClock
import com.bossxor.lottegiants.domain.resolveCancelReason
import com.bossxor.lottegiants.domain.suspendDisplayLabel
import com.bossxor.lottegiants.domain.withSuspendFilled
import com.bossxor.lottegiants.domain.isPitcherPosition
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.domain.runnerOccupied
import com.bossxor.lottegiants.domain.runnerOrderFromRelay
import com.bossxor.lottegiants.domain.runnerPlayerCodeFromRelay
import com.bossxor.lottegiants.domain.resolveStadiumCoord
import com.bossxor.lottegiants.domain.teamCodeToName
import com.bossxor.lottegiants.domain.teamHomeStadiumName
import com.bossxor.lottegiants.domain.teamKeuboId
import com.bossxor.lottegiants.domain.teamKeuboSlug
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.remainingGames
import com.bossxor.lottegiants.domain.seasonLength
import com.bossxor.lottegiants.domain.widgetRaceLine
import com.bossxor.lottegiants.domain.gameCountdownLabel
import com.bossxor.lottegiants.domain.isCanceledGame
import com.bossxor.lottegiants.domain.matchesTeam
import com.bossxor.lottegiants.domain.doubleHeaderNoFromGameId
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.snapshotStaleForKboDay
import com.bossxor.lottegiants.domain.normalizedIfCanceled
import com.bossxor.lottegiants.domain.weatherSummaryKo
import com.bossxor.lottegiants.domain.toCell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** 네이버 프리뷰 → GamePreview·라인업·주요장면 변환(상태 없음). */

internal fun buildGamePreview(
    game: LotteGameInfo,
    dto: PreviewData?,
    standings: List<TeamStanding>,
    seasonGames: List<KboOfficialGame>,
    kbo: KboOfficialGame? = null,
): GamePreview {
    val homeStarter = dto?.homeStarter
    val awayStarter = dto?.awayStarter
    val lotteStarterBlock = if (game.isHome) homeStarter else awayStarter
    val oppStarterBlock = if (game.isHome) awayStarter else homeStarter
    val lotteTop = if (game.isHome) dto?.homeTopPlayer else dto?.awayTopPlayer
    val oppTop = if (game.isHome) dto?.awayTopPlayer else dto?.homeTopPlayer

    val focus = game.focusTeamCode.ifBlank { LOTTE_TEAM_CODE }
    val focusName = game.focusName()
    val lotteSt = standings.firstOrNull { it.teamId.equals(focus, true) }
    val oppSt = standings.firstOrNull { it.teamId == game.opponentCode }

    val matchups = seasonGames
        .filter {
            it.involvesTeam(focus) &&
                (it.homeId.equals(game.opponentCode, true) || it.awayId.equals(game.opponentCode, true)) &&
                it.status() == GameStatus.ENDED
        }
        .sortedByDescending { it.gameDate }
    var w = 0
    var d = 0
    var l = 0
    matchups.forEach { m ->
        val lotteHome = m.homeId.equals(focus, true)
        val ls = if (lotteHome) m.homeScore else m.awayScore
        val os = if (lotteHome) m.awayScore else m.homeScore
        when {
            ls > os -> w++
            ls < os -> l++
            else -> d++
        }
    }

    val lotteRank = game.lotteRank.takeIf { it > 0 } ?: lotteSt?.ranking ?: 0
    val oppRank = game.opponentRank.takeIf { it > 0 } ?: oppSt?.ranking ?: 0

    return GamePreview(
        gameDate = game.gameDate,
        startTime = game.startTime,
        stadium = dto?.gameInfo?.stadium?.takeIf { it.isNotBlank() } ?: game.stadium,
        broadChannel = game.broadChannel,
        lotteStarter = toPreviewPitcher(lotteStarterBlock, game.lotteStartingPitcher),
        opponentStarter = toPreviewPitcher(oppStarterBlock, game.opponentStartingPitcher),
        lotteKeyBatter = toPreviewBatter(lotteTop),
        opponentKeyBatter = toPreviewBatter(oppTop),
        lotteStanding = PreviewTeamLine(
            teamCode = focus,
            teamName = focusName,
            rank = lotteRank,
            win = lotteSt?.win ?: 0,
            draw = lotteSt?.draw ?: 0,
            lose = lotteSt?.lose ?: 0,
            wra = lotteSt?.wra ?: 0.0,
        ),
        opponentStanding = PreviewTeamLine(
            teamCode = game.opponentCode,
            teamName = game.opponentName,
            rank = oppRank,
            win = oppSt?.win ?: 0,
            draw = oppSt?.draw ?: 0,
            lose = oppSt?.lose ?: 0,
            wra = oppSt?.wra ?: 0.0,
        ),
        seasonMatchup = MatchupRecord(
            wins = w,
            draws = d,
            losses = l,
            label = when {
                matchups.isEmpty() -> "시즌 맞대결 없음"
                kbo != null && kbo.vsGameCn > 0 ->
                    "시즌 ${kbo.vsGameCn}차전 · 상대전 ${w}승 ${d}무 ${l}패"
                else -> "시즌 상대전 ${w}승 ${d}무 ${l}패"
            },
        ),
        recentMatchups = matchups.take(5).map { it.toMiniGame() },
        lotteRecentForm = toRecentForm(
            if (game.isHome) dto?.homeTeamPreviousGames else dto?.awayTeamPreviousGames,
            focus,
        ),
        opponentRecentForm = toRecentForm(
            if (game.isHome) dto?.awayTeamPreviousGames else dto?.homeTeamPreviousGames,
            game.opponentCode,
        ),
        hotColdAvailable = lotteTop?.hotColdZone.orEmpty().isNotEmpty() ||
            oppTop?.hotColdZone.orEmpty().isNotEmpty(),
    )
}

internal fun toPreviewPitcher(block: PreviewPlayerBlock?, fallbackName: String): PreviewPitcher {
    val info = block?.playerInfo
    val stats = block?.currentSeasonStats
    return PreviewPitcher(
        name = info?.name?.takeIf { it.isNotBlank() } ?: stats?.playerName.orEmpty().ifBlank { fallbackName },
        playerCode = info?.pCode ?: block?.playerCode ?: stats?.playerCode.orEmpty(),
        era = stats?.era.orEmpty(),
        wins = stats?.w ?: 0,
        losses = stats?.l ?: 0,
        strikeouts = stats?.kk ?: 0,
        innings = stats?.inn.orEmpty(),
        whip = stats?.whip.orEmpty(),
        games = stats?.gameCount ?: 0,
    )
}

internal fun toPreviewBatter(block: PreviewPlayerBlock?): PreviewBatter {
    val info = block?.playerInfo
    val stats = block?.currentSeasonStats
    val recent = block?.recentFiveGamesStats
    val vsOpp = block?.currentSeasonStatsOnOpponents
    return PreviewBatter(
        name = info?.name.orEmpty(),
        playerCode = info?.pCode ?: block?.playerCode.orEmpty(),
        avg = stats?.hra.orEmpty(),
        hits = stats?.hit ?: 0,
        hr = stats?.hr ?: 0,
        rbi = stats?.rbi ?: 0,
        games = stats?.gameCount ?: 0,
        ops = stats?.let { it.ops ?: it.obp?.let { obp -> it.slg?.let { slg -> obp + slg } } }
            ?.let { String.format(java.util.Locale.US, "%.3f", it) }.orEmpty(),
        recentAvg = recent?.hra.orEmpty(),
        recentHits = recent?.hit ?: 0,
        recentRbi = recent?.rbi ?: 0,
        vsOpponentAvg = vsOpp?.hra.orEmpty(),
        vsOpponentHits = vsOpp?.hit ?: 0,
        vsOpponentHr = vsOpp?.hr ?: 0,
        hotCold = block?.hotColdZone.orEmpty().map { it.toDomain() },
    )
}

internal fun HotColdZoneDto.toDomain() = HotColdZone(
    zone = zone,
    avg = hra.orEmpty(),
    heat = hraStep?.toIntOrNull()?.coerceIn(1, 5) ?: 3,
    kRate = kk,
)

/** 중계 라인업이 아직 없으면 네이버 프리뷰 fullLineUp(타순)으로 채운다. */
internal fun fillLineupFromPreview(game: LotteGameInfo, dto: PreviewData?): LotteGameInfo {
    if (dto == null) return game
    val homeSlots = lineupSlotsFromPreview(dto.homeTeamLineUp)
    val awaySlots = lineupSlotsFromPreview(dto.awayTeamLineUp)
    val lotteSlots = if (game.isHome) homeSlots else awaySlots
    val oppSlots = if (game.isHome) awaySlots else homeSlots
    val lotteLineup = if (game.lotteLineup.size >= 9) game.lotteLineup else lotteSlots.ifEmpty { game.lotteLineup }
    val oppLineup = if (game.opponentLineup.size >= 9) game.opponentLineup else oppSlots.ifEmpty { game.opponentLineup }
    if (lotteLineup === game.lotteLineup && oppLineup === game.opponentLineup) return game
    return game.copy(
        lotteLineup = lotteLineup,
        opponentLineup = oppLineup,
        lineupAnnounced = game.lineupAnnounced || lotteLineup.size >= 9,
    )
}

internal fun lineupSlotsFromPreview(block: PreviewTeamLineUp?): List<LineupSlot> {
    val batters = block?.fullLineUp.orEmpty().filter { p ->
        p.position != "1" && p.positionName != "선발투수"
    }
    if (batters.size < 9) return emptyList()
    return batters.take(9).mapIndexed { i, p ->
        LineupSlot(
            batOrder = i + 1,
            name = p.playerName.orEmpty(),
            position = p.positionName.orEmpty().ifBlank { previewPosName(p.position) },
            playerCode = p.playerCode.orEmpty(),
            backNumber = p.backnum.orEmpty(),
            hitType = p.hitType.orEmpty().ifBlank { p.batsThrows.orEmpty() },
        )
    }
}

internal fun previewPosName(pos: String?): String = when (pos) {
    "0" -> "지명타자"
    "2" -> "포수"
    "3" -> "1루수"
    "4" -> "2루수"
    "5" -> "3루수"
    "6" -> "유격수"
    "7" -> "좌익수"
    "8" -> "중견수"
    "9" -> "우익수"
    else -> ""
}

internal fun toRecentForm(games: List<PreviewPreviousGame>?, teamCode: String): List<RecentFormGame> {
    if (games.isNullOrEmpty() || teamCode.isBlank()) return emptyList()
    return games.map { g ->
        val home = g.hCode.equals(teamCode, ignoreCase = true)
        val date = g.gdate.takeIf { it > 0 }?.toString().orEmpty().let { raw ->
            if (raw.length == 8) "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}" else raw
        }
        RecentFormGame(
            gameId = g.gameId.orEmpty(),
            date = date,
            opponentName = if (home) g.aName.orEmpty() else g.hName.orEmpty(),
            isHome = home,
            teamScore = if (home) g.hScore else g.aScore,
            oppScore = if (home) g.aScore else g.hScore,
            result = g.result.orEmpty(),
        )
    }
}

internal fun extractKeyPlays(texts: List<RelayText>): List<KeyPlay> {
    val keywords = listOf("홈런", "득점", "타점", "끝내기", "역전", "동점", "만루", "적시")
    return texts
        .filter { t -> keywords.any { k -> t.text.contains(k) } }
        .sortedByDescending { it.seqno }
        .take(12)
        .map {
            KeyPlay(
                inning = it.inning,
                isTop = it.isTopInning,
                text = it.text,
                isScoring = it.text.contains("득점") || it.text.contains("홈런") || it.text.contains("타점"),
            )
        }
}

internal fun provisionalMvp(game: LotteGameInfo): Pair<String, String> {
    val lotteBatters = game.lotteLineup + game.lotteBenchBatters
    val best = lotteBatters.maxWithOrNull(
        compareBy<LineupSlot> { it.todayRbi }.thenBy { it.todayHits }.thenBy { it.todayRun },
    ) ?: return "" to ""
    if (best.todayHits <= 0 && best.todayRbi <= 0) return "" to ""
    return best.name to buildString {
        append("${best.todayHits}안타")
        if (best.todayRbi > 0) append(" ${best.todayRbi}타점")
        if (best.todayRun > 0) append(" ${best.todayRun}득점")
        append(" (${best.todayHits}/${best.todayAtBats})")
    }
}
