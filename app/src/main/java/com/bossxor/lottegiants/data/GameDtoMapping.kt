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

/** 일정 DTO(GameDto/KBO) → 도메인 변환·선택 헬퍼(상태 없음). */

internal fun parseNaverGameIdDate(gameId: String): LocalDate? {
    val ymd = gameId.take(8)
    if (ymd.length < 8 || ymd.any { !it.isDigit() }) return null
    return runCatching {
        LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull()
}

internal fun GameDto.involvesTeam(code: String): Boolean {
    val c = code.trim()
    if (c.isBlank()) return false
    val name = teamCodeToName(c)
    return homeTeamCode.equals(c, true) || awayTeamCode.equals(c, true) ||
        (name.isNotBlank() && (homeTeamName.contains(name) || awayTeamName.contains(name)))
}

internal fun pickKboLotte(
    games: List<KboOfficialGame>,
    preferredId: String? = null,
    focus: String = LOTTE_TEAM_CODE,
): KboOfficialGame? {
    val lotte = games.filter { it.involvesTeam(focus) }
    if (lotte.isEmpty()) return null
    preferredId?.takeIf { it.isNotBlank() }?.let { id ->
        lotte.firstOrNull { it.naverGameId() == id || it.gameId == id }?.let { return it }
    }
    return lotte.minWithOrNull(
        compareBy({ it.status().livePriority() }, { it.headerNo }, { it.startTime }),
    )
}

internal fun pickNaverLotte(games: List<GameDto>, preferredId: String? = null): GameDto? {
    if (games.isEmpty()) return null
    preferredId?.takeIf { it.isNotBlank() }?.let { id ->
        games.firstOrNull { it.gameId == id }?.let { return it }
    }
    return games.minWithOrNull(compareBy({ it.status().livePriority() }, { it.startTimeText() }))
}

internal fun GameStatus.livePriority(): Int = when (this) {
    GameStatus.LIVE -> 0
    GameStatus.BEFORE -> 1
    GameStatus.ENDED -> 2
    GameStatus.CANCELED -> 3
}

internal fun GameDto.matchKey(): String =
    "${awayTeamCode.trim().uppercase()}_${homeTeamCode.trim().uppercase()}"

/** 일정 API가 전날 경기를 섞어 주면 어제 결과가 '오늘'로 남는다. */
internal fun List<KboOfficialGame>.forKboDate(date: LocalDate): List<KboOfficialGame> {
    val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val compact = date.format(DateTimeFormatter.BASIC_ISO_DATE)
    return filter { g ->
        val iso = g.isoDate()
        when {
            iso == key -> true
            iso.isBlank() -> g.gameId.contains(compact) || g.naverGameId().startsWith(compact)
            else -> false
        }
    }
}

internal fun GameDto.delayBlob(): String =
    listOfNotNull(statusInfo, specialMatchInfo).joinToString(" ")

internal fun GameDto.isSuspendedGame(): Boolean =
    (suspended && !cancel) || isDelayText(delayBlob())

internal fun GameDto.status(): GameStatus = when {
    isSuspendedGame() -> GameStatus.LIVE
    cancel -> GameStatus.CANCELED
    statusInfo?.contains("취소") == true && !isDelayText(statusInfo) -> GameStatus.CANCELED
    statusInfo?.contains("순연") == true && !isDelayText(statusInfo) -> GameStatus.CANCELED
    statusCode == "RESULT" || statusNum == 4 -> GameStatus.ENDED
    statusCode == "BEFORE" || statusNum == 1 -> GameStatus.BEFORE
    else -> GameStatus.LIVE
}

internal fun GameDto.startTimeText(): String = runCatching {
    LocalDateTime.parse(gameDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")

internal fun GameDto.toMiniGame(kboCancelLabel: String? = null): MiniGame {
    val st = status()
    val delayed = isSuspendedGame()
    val reason = if (st == GameStatus.CANCELED) {
        resolveCancelReason(kboCancelLabel?.takeIf { it.isNotBlank() } ?: statusInfo).orEmpty()
    } else {
        ""
    }
    val blob = delayBlob()
    val clock = parseResumeClock(blob)
    val label = if (st == GameStatus.CANCELED) cancelDisplayLabel(reason.ifBlank { null }) else ""
    return MiniGame(
        gameId = gameId,
        homeName = homeTeamName,
        awayName = awayTeamName,
        homeScore = homeTeamScore,
        awayScore = awayTeamScore,
        status = st,
        statusText = when {
            delayed -> suspendDisplayLabel(blob, clock)
            statusInfo?.isNotBlank() == true && st != GameStatus.CANCELED -> statusInfo!!
            st == GameStatus.BEFORE -> startTimeText()
            st == GameStatus.CANCELED -> label
            st == GameStatus.ENDED -> "종료"
            else -> "진행 중"
        },
        cancelReason = reason,
        isSuspended = delayed,
        resumeTime = clock,
        stadium = stadium.orEmpty(),
        startTime = startTimeText(),
        homeLogoUrl = teamLogoUrl(homeTeamCode),
        awayLogoUrl = teamLogoUrl(awayTeamCode),
        homeStarter = homeStarterName.orEmpty(),
        awayStarter = awayStarterName.orEmpty(),
        broadChannel = broadChannel.orEmpty(),
        winPitcherName = winPitcherName.orEmpty(),
        losePitcherName = losePitcherName.orEmpty(),
        gameDate = gameDate,
        homeTeamCode = homeTeamCode,
        awayTeamCode = awayTeamCode,
        doubleHeaderNo = doubleHeaderNoFromGameId(gameId),
    )
}

internal fun GameDto.toLotteBase(
    kboCancelLabel: String? = null,
    focusTeamCode: String = LOTTE_TEAM_CODE,
): LotteGameInfo {
    val focus = focusTeamCode.trim().uppercase().ifBlank { LOTTE_TEAM_CODE }
    val isHome = homeTeamCode.equals(focus, true)
    val oppCode = if (isHome) awayTeamCode else homeTeamCode
    val focusName = (if (isHome) homeTeamName else awayTeamName)
        .ifBlank { teamCodeToName(focus) }
    val delayed = isSuspendedGame()
    val blob = delayBlob()
    val clock = parseResumeClock(blob)
    val cancelReason = if (status() == GameStatus.CANCELED) {
        resolveCancelReason(kboCancelLabel?.takeIf { it.isNotBlank() } ?: statusInfo).orEmpty()
    } else {
        ""
    }
    return LotteGameInfo(
        gameId = gameId,
        gameDate = gameDate,
        startTime = startTimeText(),
        stadium = stadium.orEmpty(),
        isHome = isHome,
        opponentCode = oppCode,
        opponentName = if (isHome) awayTeamName else homeTeamName,
        opponentLogoUrl = teamLogoUrl(oppCode),
        lotteLogoUrl = teamLogoUrl(focus),
        lotteScore = if (isHome) homeTeamScore else awayTeamScore,
        opponentScore = if (isHome) awayTeamScore else homeTeamScore,
        status = status(),
        statusText = when {
            delayed -> suspendDisplayLabel(blob, clock)
            status() == GameStatus.CANCELED -> cancelDisplayLabel(cancelReason)
            else -> statusInfo.orEmpty()
        },
        cancelReason = cancelReason,
        isSuspended = delayed,
        resumeTime = clock,
        broadChannel = broadChannel.orEmpty(),
        lotteStartingPitcher = (if (isHome) homeStarterName else awayStarterName).orEmpty(),
        opponentStartingPitcher = (if (isHome) awayStarterName else homeStarterName).orEmpty(),
        currentPitcherName = (if (isHome) awayCurrentPitcherName else homeCurrentPitcherName).orEmpty(),
        winPitcherName = winPitcherName.orEmpty(),
        losePitcherName = losePitcherName.orEmpty(),
        doubleHeaderNo = doubleHeaderNoFromGameId(gameId),
        focusTeamCode = focus,
        focusTeamName = focusName.ifBlank { "롯데" },
    )
}
