package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.AlertHistoryItem
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.scoringPlays
import com.bossxor.lottegiants.domain.scoringDetailBody
import com.bossxor.lottegiants.domain.advanceNamedRunners
import com.bossxor.lottegiants.domain.onlyOccupied
import com.bossxor.lottegiants.domain.NamedBases
import com.bossxor.lottegiants.domain.atBatForChance
import com.bossxor.lottegiants.domain.basesKey
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.describePlayHow
import com.bossxor.lottegiants.domain.dhSuffix
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.formatConcedeTitle
import com.bossxor.lottegiants.domain.formatHomerunTitle
import com.bossxor.lottegiants.domain.formatLotteScoreTitle
import com.bossxor.lottegiants.domain.formatScoringChanceAlert
import com.bossxor.lottegiants.domain.inferBasesAfterAdvance
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.leadChangeTitle
import com.bossxor.lottegiants.domain.parseBasesKey
import com.bossxor.lottegiants.domain.pickPlayerName
import com.bossxor.lottegiants.domain.pickScoringRelay
import com.bossxor.lottegiants.domain.shouldEmitAlert
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.parseRacePulse
import com.bossxor.lottegiants.domain.planRosterNotifications
import com.bossxor.lottegiants.domain.rosterNotifyKey
import com.bossxor.lottegiants.domain.raceChangeAlert
import com.bossxor.lottegiants.domain.racePulse
import com.bossxor.lottegiants.domain.shouldSendRosterNoneAlert
import java.time.LocalTime
import java.time.ZonedDateTime

private const val LINEUP_STAGE_FLAG = "flag"
private const val LINEUP_STAGE_FULL = "full"

private const val ID_LINEUP_FLAG = 2010
private const val ID_LINEUP_FULL = 2011
private const val ID_ROSTER_DIGEST = 5_000_000
private const val ID_ROSTER_BASE = 5_100_000
private const val ID_FAVORITE_ROSTER_BASE = 5_200_000
private const val ID_RACE = 2810

/** 등말소 중복 방지 키를 보관할 기간 */
private const val ROSTER_KEY_KEEP_DAYS = 60L

/**
 * 이전 스냅샷과 비교해 이벤트 알림을 발생시킨다.
 */
class EventDetector(private val store: SnapshotStore) {

    private var lastSeqno: Int = -1
    private var lastPitcherCode: String = ""
    private var lastLotteScore: Int = -1
    private var lastOppScore: Int = -1
    private var lastInning: Int = -1
    private var lastTop: Boolean? = null
    private var lastStatus: GameStatus? = null
    private var lastBasesKey: String = ""
    private var lineupNotifiedState: String = ""
    private var eighthNotifiedFor: String = ""
    private var extraNotifiedFor: String = ""
    private var lastFavoriteBatterCode: String = ""
    private var lastGameId: String = ""
    private var initialized = false
    private var emittingForLive = false
    private var lastChanceBatter: String = ""
    /** 직전 득점권 알림의 1·2·3루 이름 — API 주자가 안 바뀌었을 때 진루 추정용 */
    private var lastChanceBases: NamedBases = NamedBases(null, null, null)
    private var seenPitcherCodes: MutableSet<String> = mutableSetOf()

    suspend fun process(context: Context, game: LotteGameInfo?) {
        if (game == null) return
        emittingForLive = game.status == GameStatus.LIVE ||
            (game.status == GameStatus.ENDED && lastStatus == GameStatus.LIVE)

        if (lastGameId.isNotBlank() && lastGameId != game.gameId) {
            // 새 경기: 인메모리 상태만 리셋 (취소 알림 DataStore 키는 유지)
            resetInMemory()
        }
        lastGameId = game.gameId

        if (!initialized) {
            seed(game)
            initialized = true
            // 스케줄러는 매 실행 새 detector → 이미 끝난/취소된 경기도 DataStore 중복 방지 하에 1회 알림
            if (game.status == GameStatus.CANCELED) {
                notifyCanceled(context, game)
            } else if (game.status == GameStatus.ENDED && lastStatus != GameStatus.LIVE) {
                notifyEnded(context, game)
            } else {
                if (game.status == GameStatus.LIVE) {
                    maybeNotifyGameStart(context, game)
                }
                maybeNotifyLineup(context, game)
            }
            if (game.status != GameStatus.ENDED || lastStatus != GameStatus.LIVE) return
        }
        emittingForLive = game.status == GameStatus.LIVE ||
            (game.status == GameStatus.ENDED && lastStatus == GameStatus.LIVE)

        val prevStatus = lastStatus
        if (prevStatus != null && prevStatus != game.status) {
            when (game.status) {
                GameStatus.LIVE -> maybeNotifyGameStart(context, game)
                GameStatus.ENDED -> {} // 마지막 득점부터 처리한 뒤 종료를 알린다.
                GameStatus.CANCELED -> notifyCanceled(context, game)
                else -> {}
            }
        }
        lastStatus = game.status

        maybeNotifyLineup(context, game)

        if (game.status != GameStatus.LIVE && game.status != GameStatus.ENDED) {
            seedScores(game)
            if (game.currentPitcherCode.isNotBlank()) lastPitcherCode = game.currentPitcherCode
            persistCursor(game)
            return
        }

        val favorites = store.favoritePlayers()
        val favCodes = favorites.map { it.code }.toSet()
        val batterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
            .firstOrNull { it.name == game.currentBatterName }?.playerCode.orEmpty()
            .ifBlank { "" }
        if (batterCode.isNotBlank() &&
            batterCode in favCodes &&
            batterCode != lastFavoriteBatterCode &&
            game.status == GameStatus.LIVE
        ) {
            val favName = favorites.firstOrNull { it.code == batterCode }?.name
                ?.ifBlank { game.currentBatterName } ?: game.currentBatterName
            maybeNotify(
                context, NotificationType.FAVORITE_AT_BAT, 2711,
                "즐겨찾기 타석", "$favName · ${game.inningLabel}",
                gameId = game.gameId, detailTab = "relay",
            )
        }
        if (batterCode.isNotBlank()) lastFavoriteBatterCode = batterCode
        else if (game.currentBatterName.isBlank()) lastFavoriteBatterCode = ""

        val newTexts = game.recentTexts.filter { it.seqno > lastSeqno }.sortedBy { it.seqno }
        val lotteScored = lastLotteScore >= 0 && game.lotteScore > lastLotteScore
        val oppScored = lastOppScore >= 0 && game.opponentScore > lastOppScore
        val lotteRunsDelta = if (lotteScored) (game.lotteScore - lastLotteScore).coerceAtLeast(1) else 0
        val sameHalf = game.inning == lastInning && game.isTopInning == lastTop
        if (!sameHalf) {
            lastChanceBases = NamedBases(null, null, null)
            lastChanceBatter = ""
            lastBasesKey = ""
        }
        val currentBases = resolveChanceBases(namedBasesFromGame(game), newTexts, game, lotteRunsDelta)
        if (lotteScored || oppScored) {
            val score = "${game.lotteScore}:${game.opponentScore}"
            suspend fun emitScores(focus: Boolean) {
                val plays = scoringPlays(game.recentTexts, lastSeqno,
                    if (focus) lastLotteScore else lastOppScore,
                    if (focus) game.lotteScore else game.opponentScore,
                    if (focus) game.isHome else !game.isHome,
                    if (focus) lotteRosterNames(game) else oppRosterNames(game))
                for (play in plays) {
                    val attackingNow = if (focus) game.isLotteBatting else !game.isLotteBatting
                    val isLive = game.status == GameStatus.LIVE && attackingNow &&
                        (play.inning == 0 || play.inning == game.inning) &&
                        (play.isTop == null || play.isTop == game.isTopInning)
                    val atBat = if (isLive) atBatForChance(game.currentBatterName,
                        game.nextBatterName, play.who, playText = play.how.orEmpty()) else ""
                    val body = scoringDetailBody(play, atBat,
                        if (isLive) currentBases else NamedBases(null, null, null),
                        if (play.inning > 0) "${play.inning}회${if (play.isTop == true) "초" else "말"}" else game.inningLabel)
                    val hr = play.how?.contains("홈런") == true
                    val title = when {
                        !focus -> formatConcedeTitle(play.who, game.opponentName, play.runs, score, play.how)
                        hr -> formatHomerunTitle(play.who, play.runs, score, play.how, game.focusName())
                        else -> formatLotteScoreTitle(play.who, play.runs, score, play.how,
                            game.focusName(), play.rbi)
                    }
                    val type = when { !focus -> NotificationType.CONCEDING; hr -> NotificationType.HOMERUN; else -> NotificationType.SCORE }
                    val baseId = when { !focus -> 2_000_000; hr -> 3_000_000; else -> 1_000_000 }
                    maybeNotify(context, type, baseId + play.seqno.coerceAtLeast(0), title, body,
                        gameId = game.gameId, detailTab = "relay")
                    store.setHighlight(title)
                }
            }
            if (lotteScored) emitScores(true)
            if (oppScored) emitScores(false)
            leadChangeTitle(lastLotteScore, lastOppScore, game.lotteScore, game.opponentScore,
                game.opponentName, game.focusName())?.let { title ->
                maybeNotify(context, NotificationType.LEAD_CHANGE,
                    4_000_000 + (newTexts.maxOfOrNull { it.seqno } ?: lastSeqno).coerceAtLeast(0),
                    title, score, gameId = game.gameId, detailTab = "relay")
            }
        }
        // 득점 정정으로 감소한 점수도 다음 비교에 반영한다.
        seedScores(game)
        if (newTexts.isNotEmpty()) lastSeqno = newTexts.maxOf { it.seqno }

        val newPitcherCode = game.currentPitcherCode
        if (newPitcherCode.isNotBlank() &&
            lastPitcherCode.isNotBlank() &&
            newPitcherCode != lastPitcherCode
        ) {
            val pitcherName = game.currentPitcherName.ifBlank { "투수" }
            if (newPitcherCode in favCodes) {
                val favName = favorites.firstOrNull { it.code == newPitcherCode }?.name
                    ?.ifBlank { pitcherName } ?: pitcherName
                maybeNotify(
                    context, NotificationType.FAVORITE_PITCHING, 2712,
                    "즐겨찾기 등판", "$favName · ${game.inningLabel}",
                    gameId = game.gameId, detailTab = "relay",
                )
            }
            if (isBullpenPitcherEntry(game, newPitcherCode)) {
                val teamName = when {
                    game.lottePitchers.any { it.playerCode == newPitcherCode } -> game.focusName()
                    game.opponentPitchers.any { it.playerCode == newPitcherCode } -> game.opponentName
                    !game.isLotteBatting -> game.focusName()
                    else -> game.opponentName
                }
                maybeNotify(
                    context, NotificationType.PITCHER_CHANGE, 2401,
                    "투수 교체", "$teamName - $pitcherName",
                    gameId = game.gameId, detailTab = "relay",
                )
            }
        }
        // 불펜 목록에 즐겨찾기가 새로 올라온 경우 (currentPitcher 경로를 놓친 등판)
        if (game.status == GameStatus.LIVE) {
            val poolCodes = (game.lottePitchers + game.opponentPitchers)
                .map { it.playerCode }.filter { it.isNotBlank() }.toSet()
            for (code in poolCodes) {
                if (code !in favCodes) continue
                if (code in seenPitcherCodes) continue
                if (code == lastPitcherCode || code == newPitcherCode) {
                    // currentPitcher 경로에서 이미 알렸을 수 있음 — seen만 맞춤
                    seenPitcherCodes.add(code)
                    continue
                }
                val line = (game.lottePitchers + game.opponentPitchers)
                    .firstOrNull { it.playerCode == code }
                val favName = favorites.firstOrNull { it.code == code }?.name
                    ?.ifBlank { line?.name.orEmpty() } ?: line?.name.orEmpty().ifBlank { "투수" }
                maybeNotify(
                    context, NotificationType.FAVORITE_PITCHING, 2713,
                    "즐겨찾기 등판", "$favName · ${game.inningLabel}",
                    gameId = game.gameId, detailTab = "relay",
                )
                seenPitcherCodes.add(code)
            }
            seenPitcherCodes.addAll(poolCodes)
        }
        if (newPitcherCode.isNotBlank()) lastPitcherCode = newPitcherCode

        if (game.status == GameStatus.LIVE &&
            lastInning > 0 &&
            (game.inning != lastInning || game.isTopInning != (lastTop == true))
        ) {
            if (game.out == 0 || game.inning != lastInning) {
                maybeNotify(
                    context, NotificationType.INNING_CHANGE, 2501,
                    game.inningLabel, "중간 스코어 ${game.focusName()} ${game.lotteScore}:${game.opponentScore}",
                    gameId = game.gameId,
                )
            }
        }

        // 8회말
        if (game.inning == 8 && !game.isTopInning &&
            eighthNotifiedFor != "${game.gameId}-8b"
        ) {
            val key = "${game.gameId}-8b"
            maybeNotify(
                context, NotificationType.EIGHTH_INNING, 2510,
                "8회말!", "${game.focusName()} ${game.lotteScore}:${game.opponentScore} · ${game.opponentName}",
                gameId = game.gameId,
            )
            eighthNotifiedFor = key
            store.setNotifiedEighthKey(key)
        }

        // 연장
        if (game.inning >= 10 && extraNotifiedFor != game.gameId) {
            maybeNotify(
                context, NotificationType.EXTRA_INNINGS, 2520,
                "연장 시작!", "${game.inningLabel} · ${game.lotteScore}:${game.opponentScore}",
                gameId = game.gameId,
            )
            extraNotifiedFor = game.gameId
            store.setNotifiedExtraKey(game.gameId)
        }

        lastInning = game.inning
        lastTop = game.isTopInning

        if (game.status != GameStatus.LIVE || !game.isLotteBatting) {
            lastBasesKey = ""
            lastChanceBatter = ""
            lastChanceBases = NamedBases(null, null, null)
        } else {
            val key = basesKey(game.onBase1, game.onBase2, game.onBase3)
            val (was1, was2, was3) = parseBasesKey(lastBasesKey)
            val nowChance = game.onBase2 || game.onBase3
            val loaded = game.onBase1 && game.onBase2 && game.onBase3
            val play = pickScoringRelay(newTexts)
            val maker = play?.let { pickPlayerName(it.text, it.batterTitle, lotteRosterNames(game)) }
            val atBat = atBatForChance(game.currentBatterName, game.nextBatterName, maker,
                listOfNotNull(currentBases.first, currentBases.second, currentBases.third),
                play?.text.orEmpty(), game.currentBatterOrder, game.runnerOn1Order)
            val entered = (loaded && !(was1 && was2 && was3)) || (nowChance && !(was2 || was3))
            val changedBatter = nowChance && atBat.isNotBlank() && lastChanceBatter.isNotBlank() &&
                atBat != lastChanceBatter && store.chanceAtBatChange()
            if (!game.isSuspended && (entered || changedBatter)) {
                val alert = formatScoringChanceAlert(loaded, currentBases.label(), atBat,
                    game.inningLabel, game.out, game.onBase1, game.onBase2, game.onBase3)
                maybeNotify(context, NotificationType.SCORING_CHANCE,
                    if (entered) { if (loaded) 2601 else 2602 } else 2603,
                    alert.title, alert.text, gameId = game.gameId, detailTab = "relay")
            }
            // 점유 루가 같아도 매번 갱신한다. 타석만 바뀐 경우와 득점 플레이를 구분한다.
            lastBasesKey = key
            lastChanceBatter = atBat
            lastChanceBases = currentBases
        }
        if (game.status == GameStatus.ENDED && prevStatus != GameStatus.ENDED) notifyEnded(context, game)
        persistCursor(game)
    }

    /**
     * 불펜 등판만 true.
     * 선발(각 팀 pitchers 중 seqno 최소 / 선발 이름 매칭 / 목록에 선발만 있는 첫 코드)은 false.
     */
    private fun isBullpenPitcherEntry(game: LotteGameInfo, pitcherCode: String): Boolean {
        val starterCodes = starterPitcherCodes(game)
        if (pitcherCode in starterCodes) return false

        fun find(pitchers: List<PitcherLine>): PitcherLine? =
            pitchers.firstOrNull { it.playerCode == pitcherCode }

        val line = find(game.lottePitchers) ?: find(game.opponentPitchers)
        if (line != null) {
            val pool = if (game.lottePitchers.any { it.playerCode == pitcherCode }) {
                game.lottePitchers
            } else {
                game.opponentPitchers
            }
            val minSeq = pool.filter { it.seqno > 0 }.minOfOrNull { it.seqno }
                ?: pool.minOfOrNull { it.seqno }
            if (minSeq != null && line.seqno > 0 && line.seqno <= minSeq) return false
            if (line.seqno >= 2) return true
            // seqno 미상: 이름/선발 매칭으로 이미 starterCodes 처리됨 → 목록에만 있고 2번째 이후면 불펜 취급
            val orderIndex = pool.indexOfFirst { it.playerCode == pitcherCode }
            if (orderIndex > 0) return true
            if (orderIndex == 0) return false
        }

        // 기록에 아직 안 올라온 신규 투수 코드 = 교체 등판으로 봄
        // 단, 선발 이름과 같으면 선발 코드 지연 갱신
        val name = game.currentPitcherName.trim()
        if (name.isNotBlank()) {
            val starters = listOf(game.lotteStartingPitcher, game.opponentStartingPitcher)
                .map { it.trim() }.filter { it.isNotBlank() }
            if (starters.any { it == name || name.contains(it) || it.contains(name) }) {
                return false
            }
        }
        return true
    }

    private fun starterPitcherCodes(game: LotteGameInfo): Set<String> {
        val codes = mutableSetOf<String>()
        fun addFrom(pitchers: List<PitcherLine>, starterName: String) {
            val byName = starterName.trim().takeIf { it.isNotBlank() }?.let { sn ->
                pitchers.firstOrNull {
                    it.name == sn || it.name.contains(sn) || sn.contains(it.name)
                }
            }
            if (byName != null && byName.playerCode.isNotBlank()) {
                codes.add(byName.playerCode)
            }
            val minSeq = pitchers.filter { it.seqno > 0 }.minOfOrNull { it.seqno }
            if (minSeq != null) {
                pitchers.filter { it.seqno == minSeq && it.playerCode.isNotBlank() }
                    .forEach { codes.add(it.playerCode) }
            } else {
                pitchers.firstOrNull { it.playerCode.isNotBlank() }
                    ?.playerCode?.let { codes.add(it) }
            }
        }
        addFrom(game.lottePitchers, game.lotteStartingPitcher)
        addFrom(game.opponentPitchers, game.opponentStartingPitcher)
        return codes
    }

    /**
     * 라인업은 경기 1~2시간 전에 올라오는데 그때는 라이브 폴링 서비스가 아직 없다.
     * 15분 워커가 매번 새 detector를 만들어도 한 번만 알리도록 단계를 DataStore에 남긴다.
     * 발표 여부만 확인된 단계(flag)에서 알린 뒤 타순이 채워지면(full) 한 번 더 알린다.
     */
    private suspend fun maybeNotifyLineup(context: Context, game: LotteGameInfo) {
        if (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED) return
        val today = kboToday().toString()
        if (game.gameDate.isNotBlank() && game.gameDate != today) return

        val order = game.lotteLineup
            .filterNot { it.isSubstitute }
            .filter { it.batOrder in 1..9 && it.name.isNotBlank() }
            .distinctBy { it.batOrder }
        val hasOrder = order.size >= 9
        if (!hasOrder && !game.lineupAnnounced) return

        val fullKey = "${game.gameId}:$LINEUP_STAGE_FULL"
        val key = if (hasOrder) fullKey else "${game.gameId}:$LINEUP_STAGE_FLAG"
        if (lineupNotifiedState == key || lineupNotifiedState == fullKey) return
        val stored = store.notifiedLineupState()
        if (stored == key || stored == fullKey) {
            lineupNotifiedState = stored
            return
        }

        val matchup = buildString {
            append("vs ${game.opponentName}")
            if (game.startTime.isNotBlank()) append(" · ${game.startTime}")
            if (game.stadium.isNotBlank()) append(" · ${game.stadium}")
        }
        val pitchers = buildString {
            append("선발 ${game.lotteStartingPitcher.ifBlank { "미정" }}")
            if (game.opponentStartingPitcher.isNotBlank()) {
                append(" vs ${game.opponentStartingPitcher}")
            }
        }
        if (hasOrder) {
            val lines = order.sortedBy { it.batOrder }.joinToString("\n") {
                "${it.batOrder}. ${it.name}" + if (it.position.isNotBlank()) " (${it.position})" else ""
            }
            maybeNotify(
                context, NotificationType.LINEUP, ID_LINEUP_FULL,
                "선발 라인업 등록", "$matchup\n$pitchers\n$lines",
                gameId = game.gameId, detailTab = "lineup",
            )
        } else {
            maybeNotify(
                context, NotificationType.LINEUP, ID_LINEUP_FLAG,
                "라인업 발표", "$matchup\n$pitchers",
                gameId = game.gameId, detailTab = "lineup",
            )
        }
        lineupNotifiedState = key
        store.setNotifiedLineupState(key)
        maybeNotifyRosterNone(context)
    }

    /**
     * 당일 등말소 공시가 없을 때 하루 1회.
     * 라인업 알림과 같은 시점이 기본이고, 경기가 없으면 14시 이후 첫 빈 폴링에서 보낸다.
     */
    private suspend fun maybeNotifyRosterNone(context: Context, allowWithoutLineup: Boolean = false) {
        val today = kboToday().toString()
        val snap = store.loadSnapshot()
        val todayGame = snap?.lotteGame?.takeIf { it.belongsToKboToday() }
            ?: snap?.nextLotteGame?.takeIf { it.gameDate.take(10) == today }
        val gameActive = todayGame != null &&
            todayGame.status != GameStatus.CANCELED &&
            todayGame.status != GameStatus.ENDED
        val lineupKey = store.notifiedLineupState()
        val lineupDoneForToday = todayGame != null && (
            lineupKey == "${todayGame.gameId}:$LINEUP_STAGE_FLAG" ||
                lineupKey == "${todayGame.gameId}:$LINEUP_STAGE_FULL"
            )
        val waitForLineup = allowWithoutLineup && gameActive && !lineupDoneForToday
        if (!shouldSendRosterNoneAlert(
                nowHour = ZonedDateTime.now(KBO_ZONE).hour,
                today = today,
                notifiedNoneDay = store.notifiedRosterNoneDay(),
                hasTodayRosterNotifyKey = store.notifiedRosterKeys().any { it.startsWith("$today:") },
                waitForLineup = waitForLineup,
            )
        ) {
            return
        }
        maybeNotify(
            context, NotificationType.ROSTER, ID_ROSTER_DIGEST - 1,
            "엔트리 등말소", "오늘 등말소 변화 없음",
        )
        store.setNotifiedRosterNoneDay(today)
    }

    private suspend fun notifyEnded(context: Context, game: LotteGameInfo) {
        if (store.notifiedEndGameId() == game.gameId) return
        val today = kboToday().toString()
        if (game.gameDate.isNotBlank() && game.gameDate != today) return
        val result = when {
            game.lotteScore > game.opponentScore -> "${game.focusName()} 승리!"
            game.lotteScore < game.opponentScore -> "${game.focusName()} 패배"
            else -> "무승부"
        }
        val dh = dhSuffix(game.doubleHeaderNo)
        maybeNotify(
            context, NotificationType.GAME_END, 2002,
            "$result$dh", "최종 ${game.lotteScore}:${game.opponentScore} vs ${game.opponentName}",
            gameId = game.gameId,
        )
        store.setNotifiedEndGameId(game.gameId)
        store.setWidgetEndedHold(game.gameId)
    }

    private suspend fun notifyCanceled(context: Context, game: LotteGameInfo) {
        val already = store.notifiedCancelGameId()
        if (already == game.gameId) return
        val title = game.cancelLabel
        val text = buildString {
            append(game.opponentName)
            append("전 · ")
            append(title)
            if (game.stadium.isNotBlank()) {
                append(" · ")
                append(game.stadium)
            }
        }
        maybeNotify(context, NotificationType.CANCELED, 2003, title, text, gameId = game.gameId)
        store.setNotifiedCancelGameId(game.gameId)
        GameSchedulerWorker.cancelGameAlarms(context, game.gameId)
    }

    /**
     * 등말소 공시 알림. 새 공시만 골라 알리고, 이미 알린 키는 DataStore에 남겨 중복을 막는다.
     * 여러 명이 한꺼번에 공시되면 알림이 쏟아지지 않게 한 건으로 묶는다.
     */
    suspend fun processRosterMoves(context: Context, moves: List<RosterMove>) {
        val today = kboToday().toString()
        if (moves.isEmpty()) {
            // 당일 경기가 없어 라인업이 안 뜨면, 14시 이후 첫 빈 폴링에서 1회
            maybeNotifyRosterNone(context, allowWithoutLineup = true)
            return
        }
        val plan = planRosterNotifications(
            moves,
            store.notifiedRosterKeys(),
            today,
        )
        if (plan.changed) store.setNotifiedRosterKeys(pruneRosterKeys(plan.stored))
        val fresh = plan.fresh
        if (fresh.isEmpty()) return

        val favorites = store.favoritePlayers()
        val byCode = favorites.filter { it.code.isNotBlank() }.associateBy { it.code }
        val byName = favorites.filter { it.name.isNotBlank() }.associateBy { it.name }
        val others = mutableListOf<RosterMove>()
        for (m in fresh) {
            val fav = m.playerCode.takeIf { it.isNotBlank() }?.let { byCode[it] } ?: byName[m.playerName]
            if (fav == null) {
                others.add(m)
                continue
            }
            val label = moveLabel(m)
            maybeNotify(
                context, NotificationType.FAVORITE_ROSTER,
                ID_FAVORITE_ROSTER_BASE + (rosterNotifyKey(m).hashCode() and 0xFFFF),
                "즐겨찾기 $label",
                "${fav.name.ifBlank { m.playerName }} $label · ${m.moveDate}",
            )
        }
        if (others.isEmpty()) return

        if (others.size == 1) {
            val m = others.first()
            val label = moveLabel(m)
            maybeNotify(
                context, NotificationType.ROSTER,
                ID_ROSTER_BASE + (rosterNotifyKey(m).hashCode() and 0xFFFF),
                "엔트리 $label", "${m.playerName} $label · ${m.moveDate}",
            )
            return
        }
        val body = others.groupBy { it.moveDate }
            .entries
            .sortedByDescending { it.key }
            .joinToString("\n") { (date, list) ->
                val registered = list.filter { it.isRegister }.map { it.playerName }
                val removed = list.filterNot { it.isRegister }.map { it.playerName }
                buildString {
                    append(date)
                    if (registered.isNotEmpty()) append("\n등록  ${registered.joinToString(" · ")}")
                    if (removed.isNotEmpty()) append("\n말소  ${removed.joinToString(" · ")}")
                }
            }
        maybeNotify(
            context, NotificationType.ROSTER, ID_ROSTER_DIGEST,
            "엔트리 등말소 ${others.size}건", body,
        )
    }

    private fun moveLabel(m: RosterMove) = if (m.isRegister) "등록" else "말소"

    /** 시즌 내내 키가 쌓이지 않게 최근 공시만 남긴다. */
    private fun pruneRosterKeys(keys: Set<String>): Set<String> {
        val cutoff = kboToday().minusDays(ROSTER_KEY_KEEP_DAYS).toString()
        val kept = keys.filterTo(mutableSetOf()) { it.substringBefore(':') >= cutoff }
        return if (kept.isEmpty()) keys else kept
    }

    private fun resetInMemory() {
        lastSeqno = -1
        lastPitcherCode = ""
        lastLotteScore = -1
        lastOppScore = -1
        lastInning = -1
        lastTop = null
        lastStatus = null
        lastBasesKey = ""
        lineupNotifiedState = ""
        eighthNotifiedFor = ""
        extraNotifiedFor = ""
        lastFavoriteBatterCode = ""
        lastChanceBatter = ""
        lastChanceBases = NamedBases(null, null, null)
        seenPitcherCodes = mutableSetOf()
        initialized = false
    }

    private suspend fun seed(game: LotteGameInfo) {
        val cursor = store.liveEventCursor()
        val parts = cursor.split('|')
        // gameId|seq|ls|os|bases|pitcher|favBatter|status|chanceBatter|seenPitchersComma
        val sameGame = parts.size >= 8 && parts[0] == game.gameId
        if (sameGame) {
            lastSeqno = parts[1].toIntOrNull() ?: (game.recentTexts.maxOfOrNull { it.seqno } ?: -1)
            lastLotteScore = parts[2].toIntOrNull() ?: game.lotteScore
            lastOppScore = parts[3].toIntOrNull() ?: game.opponentScore
            lastBasesKey = parts[4]
            lastPitcherCode = parts[5]
            lastFavoriteBatterCode = parts[6]
            lastStatus = runCatching { GameStatus.valueOf(parts[7]) }.getOrNull() ?: game.status
            lastChanceBatter = parts.getOrNull(8).orEmpty().ifBlank { game.currentBatterName.trim() }
            seenPitcherCodes = parts.getOrNull(9).orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableSet()
            lastChanceBases = parts.getOrNull(10)?.let { NamedBases.decode(it) }
                ?: namedBasesFromGame(game)
            if (seenPitcherCodes.isEmpty() && game.currentPitcherCode.isNotBlank()) {
                seenPitcherCodes.add(game.currentPitcherCode)
            }
        } else {
            lastSeqno = game.recentTexts.maxOfOrNull { it.seqno } ?: -1
            lastPitcherCode = game.currentPitcherCode
            seedScores(game)
            lastStatus = game.status
            lastBasesKey = basesKey(game.onBase1, game.onBase2, game.onBase3)
            lastFavoriteBatterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
                .firstOrNull { it.name == game.currentBatterName }?.playerCode.orEmpty()
            lastChanceBatter = game.currentBatterName.trim()
            lastChanceBases = namedBasesFromGame(game)
            // 전체 불펜 풀을 넣으면 재시작 후 즐겨찾기 등판을 놓친다. 현재 투수만.
            seenPitcherCodes = if (game.currentPitcherCode.isNotBlank()) {
                mutableSetOf(game.currentPitcherCode)
            } else {
                mutableSetOf()
            }
        }
        lastInning = if (sameGame) parts.getOrNull(11)?.toIntOrNull() ?: -1 else game.inning
        lastTop = if (sameGame) parts.getOrNull(12)?.toBooleanStrictOrNull() else game.isTopInning
        eighthNotifiedFor = store.notifiedEighthKey()
        extraNotifiedFor = store.notifiedExtraKey()
    }

    private suspend fun persistCursor(game: LotteGameInfo) {
        val raw = listOf(
            game.gameId,
            lastSeqno.toString(),
            lastLotteScore.toString(),
            lastOppScore.toString(),
            lastBasesKey,
            lastPitcherCode,
            lastFavoriteBatterCode,
            (lastStatus ?: game.status).name,
            lastChanceBatter,
            seenPitcherCodes.filter { it.isNotBlank() }.sorted().joinToString(","),
            lastChanceBases.encode(),
            lastInning.toString(),
            lastTop?.toString().orEmpty(),
        ).joinToString("|")
        store.setLiveEventCursor(raw)
    }

    private fun namedBasesFromGame(game: LotteGameInfo): NamedBases = NamedBases(
        first = runnerName(game, game.onBase1, game.runnerOn1Order, game.runnerOn1Code),
        second = runnerName(game, game.onBase2, game.runnerOn2Order, game.runnerOn2Code),
        third = runnerName(game, game.onBase3, game.runnerOn3Order, game.runnerOn3Code),
    )

    /** 새로운 API 선수코드와 명시된 중계 진루로 매 폴링 주자를 갱신한다. */
    private fun resolveChanceBases(
        api: NamedBases,
        newTexts: List<com.bossxor.lottegiants.domain.RelayText>,
        game: LotteGameInfo,
        runsJustScored: Int,
    ): NamedBases {
        val roster = if (game.isLotteBatting) lotteRosterNames(game) else oppRosterNames(game)
        val sameSideTexts = newTexts.filter { it.inning == game.inning &&
            (it.isTopInning == null || it.isTopInning == game.isTopInning) }
        val before = lastChanceBases
        val moved = advanceNamedRunners(before, sameSideTexts, roster)
        val cause = pickScoringRelay(sameSideTexts)
        val maker = cause?.let { pickPlayerName(it.text, it.batterTitle, roster) }
        val forced = if (maker != null) inferBasesAfterAdvance(before, maker,
            describePlayHow(cause?.text.orEmpty()), runsJustScored) else null
        val remembered = if (!moved.sameOccupants(before)) moved else forced ?: before
        // 새로운 API 선수코드는 우선한다. 이전 값이면 명시한 진루 기록으로 보정한다.
        val result = if (api.sameOccupants(before)) remembered else NamedBases(
            api.first ?: remembered.first,
            api.second ?: remembered.second,
            api.third ?: remembered.third,
        )
        return result.onlyOccupied(game.onBase1, game.onBase2, game.onBase3)
    }

    private fun seedScores(game: LotteGameInfo) {
        lastLotteScore = game.lotteScore
        lastOppScore = game.opponentScore
    }

    private suspend fun maybeNotifyGameStart(context: Context, game: LotteGameInfo) {
        if (store.notifiedGameStartId() == game.gameId) return
        // 중계 복구로 2회 이상에서 LIVE를 보면 시작 알림은 생략하고 키만 남긴다.
        if (game.inning >= 2) {
            store.setNotifiedGameStartId(game.gameId)
            return
        }
        val dh = dhSuffix(game.doubleHeaderNo)
        maybeNotify(
            context, NotificationType.GAME_START, 2001,
            "경기 시작!$dh",
            "${game.opponentName}전 시작$dh · ${game.stadium}",
            gameId = game.gameId,
        )
        store.setNotifiedGameStartId(game.gameId)
    }

    private fun lotteRosterNames(game: LotteGameInfo): List<String> =
        (game.lotteLineup + game.lotteBenchBatters).map { it.name }

    private fun oppRosterNames(game: LotteGameInfo): List<String> =
        (game.opponentLineup + game.opponentBenchBatters).map { it.name }

    private fun lineupNameByOrder(lineup: List<com.bossxor.lottegiants.domain.LineupSlot>, order: Int): String? {
        if (order <= 0) return null
        // 같은 타순에 교체 선수가 있으면 선발 또는 대타를 임의로 고르지 않는다.
        return lineup.filter { it.batOrder == order && it.name.isNotBlank() }
            .map { it.name }.distinct().singleOrNull()
    }

    private fun lineupNameByCode(
        lineup: List<com.bossxor.lottegiants.domain.LineupSlot>,
        code: String,
    ): String? {
        if (code.isBlank()) return null
        return lineup.firstOrNull { it.playerCode == code && it.name.isNotBlank() }?.name
    }

    /** 공격 팀 라인업에서 선수코드로 찾고, 타순만 있을 때는 한 명으로 확정될 때만 사용한다. */
    private fun runnerName(
        game: LotteGameInfo,
        onBase: Boolean,
        order: Int,
        playerCode: String = "",
    ): String? {
        if (!onBase) return null
        val pool = if (game.isLotteBatting) game.lotteLineup + game.lotteBenchBatters
            else game.opponentLineup + game.opponentBenchBatters
        lineupNameByCode(pool, playerCode)?.let { return it }
        lineupNameByOrder(pool, order)?.let { return it }
        if (order <= 0 && playerCode.isBlank()) {
            android.util.Log.w(
                "EventDetector",
                "runner on base without code/order (game=${game.gameId})",
            )
        }
        return null
    }

    private suspend fun maybeNotify(
        context: Context,
        type: NotificationType,
        id: Int,
        title: String,
        text: String,
        gameId: String = "",
        detailTab: String? = null,
    ) {
        val allow = shouldEmitAlert(
            typeEnabled = store.isNotificationEnabled(type),
            liveOnly = store.alertsLiveOnly(),
            gameIsLive = emittingForLive,
            quietEnabled = store.quietHoursEnabled(),
            quietStartHour = store.quietStartHour(),
            quietEndHour = store.quietEndHour(),
            now = LocalTime.now(KBO_ZONE),
            type = type,
        )
        if (allow) {
            NotificationHelper.notifyEvent(context, type, title, text, id, gameId, detailTab)
            store.appendAlertHistory(
                AlertHistoryItem(
                    millis = System.currentTimeMillis(),
                    type = type.name,
                    title = title,
                    text = text,
                ),
            )
        }
    }

    suspend fun processRace(
        context: Context,
        standings: List<TeamStanding>,
        recentGames: List<com.bossxor.lottegiants.domain.MiniGame> = emptyList(),
    ) {
        val teamCode = store.myTeamCode()
        val now = racePulse(standings, teamCode) ?: return
        val prev = parseRacePulse(store.lastRaceFingerprint())
        val alert = raceChangeAlert(prev, now, standings, recentGames, teamCode)
        store.setLastRaceFingerprint(now.fingerprint())
        if (alert != null) {
            val live = emittingForLive
            emittingForLive = false
            maybeNotify(context, NotificationType.RACE_NUMBER, ID_RACE, alert.first, alert.second)
            emittingForLive = live
        }
    }
}
