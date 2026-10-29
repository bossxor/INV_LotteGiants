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
    val ymd = com.bossxor.lottegiants.domain.naverGameIdYmd(gameId)
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

/**
 * 당일 포스트시즌이면 응원팀 대신 그 경기 홈팀을 포커스로 쓴다.
 * 정규시즌은 [storedFocus] + [pickKboLotte] 그대로.
 * 스냅샷 myTeamCode 는 덮지 말고 게임 focusTeamCode 에만 쓴다.
 */
internal fun pickPostseasonOrTeamGame(
    games: List<KboOfficialGame>,
    preferredId: String? = null,
    storedFocus: String = LOTTE_TEAM_CODE,
): Pair<String, KboOfficialGame?> {
    val stored = storedFocus.trim().uppercase().ifBlank { LOTTE_TEAM_CODE }
    val ps = games.filter { it.isPostseason() }
    if (ps.isEmpty()) return stored to pickKboLotte(games, preferredId, stored)
    val preferred = preferredId?.takeIf { it.isNotBlank() }?.let { id ->
        ps.firstOrNull { it.naverGameId() == id || it.gameId == id }
    }
    val game = preferred ?: ps.minWithOrNull(
        compareBy({ it.status().livePriority() }, { it.headerNo }, { it.startTime }),
    )
    val home = game?.homeId?.trim()?.uppercase().orEmpty()
    val focus = if (home.isNotBlank()) home else stored
    return focus to game
}

/**
 * 라이브 칩/기타 경기 목록. 포스트시즌 당일이면 전체 PS vs 나머지,
 * 정규시즌은 응원팀 vs 그 외.
 */
internal fun splitTodayLiveGames(
    games: List<KboOfficialGame>,
    storedFocus: String,
): Pair<List<KboOfficialGame>, List<KboOfficialGame>> {
    val ps = games.filter { it.isPostseason() }
    if (ps.isNotEmpty()) return ps to games.filter { !it.isPostseason() }
    val focus = storedFocus.trim().uppercase().ifBlank { LOTTE_TEAM_CODE }
    return games.filter { it.involvesTeam(focus) } to games.filter { !it.involvesTeam(focus) }
}

/**
 * 포스트시즌 기간(정규시즌 경기가 더 없음)이고 오늘 내 팀 경기가 없으면, 다가오는 포스트시즌 경기 → 없으면
 * 가장 최근 포스트시즌 경기의 홈팀을 포커스로 쓴다. 쉬는 날에도 라이브 바가 내 팀이 아닌 가을야구를 따라간다.
 */
internal fun postseasonPeriodFocus(
    range: List<KboOfficialGame>,
    todayIso: String,
    todayGames: List<KboOfficialGame>,
    stored: String,
): String? {
    if (todayGames.any { it.involvesTeam(stored) && !it.isPostseason() }) return null
    if (range.any { !it.isPostseason() && it.isoDate() >= todayIso && it.status() == GameStatus.BEFORE }) return null
    val ps = range.filter { it.isPostseason() && it.homeId.isNotBlank() && it.awayId.isNotBlank() }
    val next = ps.filter { it.isoDate() >= todayIso && it.status() == GameStatus.BEFORE }
        .minWithOrNull(compareBy({ it.isoDate() }, { it.startTime }))
    val last = ps.filter { it.isoDate() <= todayIso && it.status() == GameStatus.ENDED }
        .maxWithOrNull(compareBy({ it.isoDate() }, { it.startTime }))
    // 한국시리즈가 끝나 남은 포스트시즌 경기가 없으면 시즌이 끝난 것이니 내 팀 기준으로 돌아간다.
    // 우천 취소·진행 중인 한국시리즈 경기가 남아 있으면 아직 끝난 게 아니다.
    val ksOpen = range.any { it.isKoreanSeries() && it.isoDate() >= todayIso && it.status() != GameStatus.ENDED }
    if (next == null && last != null && last.isKoreanSeries() && !ksOpen) return null
    return (next ?: last)?.homeId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
}

internal fun KboOfficialGame.isKoreanSeries(): Boolean =
    srId == 7 || gameScNm.replace(" ", "").let { it.startsWith("KS", true) || it.contains("한국시리즈") }

/**
 * 내 팀이 시즌 중이 아니면 true. 시즌 중 = 일정 창(지난 3주~앞으로 2주)에 이미 치른 경기와 남은 경기가 모두 있을 때.
 * 개막 전(치른 경기 없음)·탈락 뒤(남은 경기 없음)·비시즌(일정 없음)은 모두 시즌 밖이다. 시범경기(srId 1)는 세지 않는다.
 */
internal fun teamSeasonOver(range: List<KboOfficialGame>, team: String, todayIso: String): Boolean {
    val games = range.filter { it.involvesTeam(team) && it.srId != 1 && it.status() != GameStatus.CANCELED }
    val started = games.any { it.isoDate() <= todayIso }
    val remaining = games.any { it.isoDate() >= todayIso }
    return !(started && remaining)
}

internal fun pickNaverLotte(games: List<GameDto>, preferredId: String? = null): GameDto? {
    if (games.isEmpty()) return null
    preferredId?.takeIf { it.isNotBlank() }?.let { id ->
        games.firstOrNull { it.gameId == id }?.let { return it }
    }
    return games.minWithOrNull(compareBy({ it.status().livePriority() }, { it.startTimeText() }))
}

internal fun GameDto.isPostseason(): Boolean =
    roundCode.orEmpty().startsWith("kbo_ps") ||
        com.bossxor.lottegiants.domain.isPostseasonGameLabel(specialMatchInfo)

internal fun pickNaverPostseasonOrTeamGame(
    games: List<GameDto>,
    preferredId: String? = null,
    storedFocus: String = LOTTE_TEAM_CODE,
): Pair<String, GameDto?> {
    val stored = storedFocus.trim().uppercase().ifBlank { LOTTE_TEAM_CODE }
    val kbo = games.filter { it.categoryId.isBlank() || it.categoryId == "kbo" }
    val ps = kbo.filter { it.isPostseason() }
    if (ps.isEmpty()) return stored to pickNaverLotte(kbo.filter { it.involvesTeam(stored) }, preferredId)
    val preferred = preferredId?.takeIf { it.isNotBlank() }?.let { id ->
        ps.firstOrNull { it.gameId == id }
    }
    val game = preferred ?: pickNaverLotte(ps, null)
    val home = game?.homeTeamCode?.trim()?.uppercase().orEmpty()
    val focus = if (home.isNotBlank()) home else stored
    return focus to game
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
        gameScLabel = if (isPostseason()) "포스트시즌" else "",
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
