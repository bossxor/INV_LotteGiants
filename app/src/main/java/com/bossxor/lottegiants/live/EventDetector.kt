package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.atBatForChance
import com.bossxor.lottegiants.domain.basesKey
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.describePlayHow
import com.bossxor.lottegiants.domain.formatConcedeTitle
import com.bossxor.lottegiants.domain.formatHomerunTitle
import com.bossxor.lottegiants.domain.formatLotteScoreTitle
import com.bossxor.lottegiants.domain.formatScoringChanceAlert
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.leadChangeTitle
import com.bossxor.lottegiants.domain.parseBasesKey
import com.bossxor.lottegiants.domain.pickAdvanceRelay
import com.bossxor.lottegiants.domain.pickPlayerName
import com.bossxor.lottegiants.domain.pickScoringRelay
import com.bossxor.lottegiants.domain.runnersLabel
import com.bossxor.lottegiants.domain.scoringBody
import com.bossxor.lottegiants.domain.shouldEmitAlert
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.parseRacePulse
import com.bossxor.lottegiants.domain.raceChangeAlert
import com.bossxor.lottegiants.domain.racePulse
import java.time.LocalTime

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

    suspend fun process(context: Context, game: LotteGameInfo?) {
        if (game == null) return
        emittingForLive = game.status == GameStatus.LIVE

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
            } else if (game.status == GameStatus.ENDED) {
                notifyEnded(context, game)
            } else {
                maybeNotifyLineup(context, game)
            }
            return
        }

        val prevStatus = lastStatus
        if (prevStatus != null && prevStatus != game.status) {
            when (game.status) {
                GameStatus.LIVE -> maybeNotify(
                    context, NotificationType.GAME_START, 2001,
                    "경기 시작!", "${game.opponentName}전 시작 · ${game.stadium}"
                )
                GameStatus.ENDED -> notifyEnded(context, game)
                GameStatus.CANCELED -> notifyCanceled(context, game)
                else -> {}
            }
        }
        lastStatus = game.status

        maybeNotifyLineup(context, game)

        if (game.status != GameStatus.LIVE && game.status != GameStatus.ENDED) {
            seedScores(game)
            if (game.currentPitcherCode.isNotBlank()) lastPitcherCode = game.currentPitcherCode
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
                "즐겨찾기 타석", "$favName · ${game.inningLabel}"
            )
        }
        if (batterCode.isNotBlank()) lastFavoriteBatterCode = batterCode
        else if (game.currentBatterName.isBlank()) lastFavoriteBatterCode = ""

        val newTexts = game.recentTexts.filter { it.seqno > lastSeqno }.sortedBy { it.seqno }
        val lotteScored = lastLotteScore >= 0 && game.lotteScore > lastLotteScore
        val oppScored = lastOppScore >= 0 && game.opponentScore > lastOppScore
        if (lotteScored || oppScored) {
            val play = pickScoringRelay(newTexts)
            val text = play?.text.orEmpty()
            val how = describePlayHow(text)
            val seq = play?.seqno ?: newTexts.maxOfOrNull { it.seqno } ?: 0
            val lotteRuns = (game.lotteScore - lastLotteScore).coerceAtLeast(1)
            val oppRuns = (game.opponentScore - lastOppScore).coerceAtLeast(1)
            val score = "${game.lotteScore}:${game.opponentScore}"
            val lotteWho = pickPlayerName(text, play?.batterTitle.orEmpty(), lotteRosterNames(game))
            val body = scoringBody(text, lotteWho, how, game.inningLabel)
            if (lotteScored) {
                val lotteHr = text.contains("홈런") && isLotteHomerun(game, text)
                if (lotteHr) {
                    val runs = parseHrRuns(text) ?: lotteRuns
                    val title = formatHomerunTitle(lotteWho, runs, score, how)
                    maybeNotify(
                        context, NotificationType.HOMERUN, 3_000_000 + seq, title, body,
                        gameId = game.gameId, detailTab = "relay",
                    )
                    store.setHighlight(title)
                    WearBridge.sendScoreEvent(context, title)
                } else {
                    val title = formatLotteScoreTitle(lotteWho, lotteRuns, score, how)
                    maybeNotify(
                        context, NotificationType.SCORE, 1_000_000 + seq, title, body,
                        gameId = game.gameId, detailTab = "relay",
                    )
                    store.setHighlight(title)
                    WearBridge.sendScoreEvent(context, "$title · ${game.inningLabel}")
                }
            }
            if (oppScored) {
                val oppWho = pickPlayerName(text, play?.batterTitle.orEmpty(), oppRosterNames(game))
                val title = formatConcedeTitle(oppWho, game.opponentName, oppRuns, score, how)
                maybeNotify(
                    context, NotificationType.CONCEDING, 2_000_000 + seq, title,
                    scoringBody(text, oppWho, how, game.inningLabel),
                    gameId = game.gameId, detailTab = "relay",
                )
                store.setHighlight(title)
                WearBridge.sendScoreEvent(context, "$title · ${game.inningLabel}")
            }
            leadChangeTitle(
                lastLotteScore, lastOppScore, game.lotteScore, game.opponentScore, game.opponentName,
            )?.let { title ->
                maybeNotify(
                    context, NotificationType.LEAD_CHANGE, 4_000_000 + seq,
                    title, score, gameId = game.gameId, detailTab = "relay",
                )
            }
            seedScores(game)
        }
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
                maybeNotify(
                    context, NotificationType.PITCHER_CHANGE, 2401,
                    "투수 교체", "$pitcherName 등판",
                    gameId = game.gameId, detailTab = "relay",
                )
            }
        }
        if (newPitcherCode.isNotBlank()) lastPitcherCode = newPitcherCode

        if (game.status == GameStatus.LIVE &&
            lastInning > 0 &&
            (game.inning != lastInning || game.isTopInning != (lastTop == true))
        ) {
            if (game.out == 0 || game.inning != lastInning) {
                maybeNotify(
                    context, NotificationType.INNING_CHANGE, 2501,
                    game.inningLabel, "중간 스코어 롯데 ${game.lotteScore}:${game.opponentScore}"
                )
            }
        }

        // 8회말
        if (game.inning == 8 && !game.isTopInning &&
            eighthNotifiedFor != "${game.gameId}-8b"
        ) {
            maybeNotify(
                context, NotificationType.EIGHTH_INNING, 2510,
                "8회말!", "롯데 ${game.lotteScore}:${game.opponentScore} · ${game.opponentName}"
            )
            eighthNotifiedFor = "${game.gameId}-8b"
        }

        // 연장
        if (game.inning >= 10 && extraNotifiedFor != game.gameId) {
            maybeNotify(
                context, NotificationType.EXTRA_INNINGS, 2520,
                "연장 시작!", "${game.inningLabel} · ${game.lotteScore}:${game.opponentScore}"
            )
            extraNotifiedFor = game.gameId
        }

        lastInning = game.inning
        lastTop = game.isTopInning

        if (game.isLotteBatting) {
            val key = basesKey(game.onBase1, game.onBase2, game.onBase3)
            if (key != lastBasesKey) {
                val (was1, was2, was3) = parseBasesKey(lastBasesKey)
                val wasChance = was2 || was3
                val nowChance = game.onBase2 || game.onBase3
                val wasLoaded = was1 && was2 && was3
                val nowLoaded = game.onBase1 && game.onBase2 && game.onBase3
                val play = pickAdvanceRelay(newTexts)
                val how = describePlayHow(play?.text.orEmpty())
                val who = pickPlayerName(
                    play?.text.orEmpty(),
                    play?.batterTitle.orEmpty(),
                    lotteRosterNames(game),
                )
                val runners = runnersLabel(
                    first = if (game.onBase1) lineupNameByOrder(game.lotteLineup, game.runnerOn1Order) else null,
                    second = if (game.onBase2) lineupNameByOrder(game.lotteLineup, game.runnerOn2Order) else null,
                    third = if (game.onBase3) lineupNameByOrder(game.lotteLineup, game.runnerOn3Order) else null,
                )
                val atBat = atBatForChance(game.currentBatterName, game.nextBatterName, who)
                val alert = formatScoringChanceAlert(
                    loaded = nowLoaded,
                    who = who,
                    how = how,
                    runners = runners,
                    batterNow = atBat,
                    inningLabel = game.inningLabel,
                    outs = game.out,
                )
                when {
                    nowLoaded && !wasLoaded -> maybeNotify(
                        context, NotificationType.SCORING_CHANCE, 2601,
                        alert.title, alert.text,
                        gameId = game.gameId, detailTab = "relay",
                    )
                    nowChance && !wasChance -> maybeNotify(
                        context, NotificationType.SCORING_CHANCE, 2602,
                        alert.title, alert.text,
                        gameId = game.gameId, detailTab = "relay",
                    )
                }
                lastBasesKey = key
            }
        } else {
            lastBasesKey = ""
        }
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
            )
        } else {
            maybeNotify(
                context, NotificationType.LINEUP, ID_LINEUP_FLAG,
                "라인업 발표", "$matchup\n$pitchers",
            )
        }
        lineupNotifiedState = key
        store.setNotifiedLineupState(key)
    }

    private suspend fun notifyEnded(context: Context, game: LotteGameInfo) {
        if (store.notifiedEndGameId() == game.gameId) return
        val today = kboToday().toString()
        if (game.gameDate.isNotBlank() && game.gameDate != today) return
        val result = when {
            game.lotteScore > game.opponentScore -> "롯데 승리!"
            game.lotteScore < game.opponentScore -> "롯데 패배"
            else -> "무승부"
        }
        maybeNotify(
            context, NotificationType.GAME_END, 2002,
            result, "최종 ${game.lotteScore}:${game.opponentScore} vs ${game.opponentName}"
        )
        store.setNotifiedEndGameId(game.gameId)
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
        maybeNotify(context, NotificationType.CANCELED, 2003, title, text)
        store.setNotifiedCancelGameId(game.gameId)
        GameSchedulerWorker.cancelGameAlarms(context, game.gameId)
    }

    /**
     * 등말소 공시 알림. 새 공시만 골라 알리고, 이미 알린 키는 DataStore에 남겨 중복을 막는다.
     * 여러 명이 한꺼번에 공시되면 알림이 쏟아지지 않게 한 건으로 묶는다.
     */
    suspend fun processRosterMoves(context: Context, moves: List<RosterMove>) {
        if (moves.isEmpty()) return
        val stored = store.notifiedRosterKeys().toMutableSet()
        if (stored.isEmpty()) {
            // 설치 직후엔 과거 공시가 한꺼번에 뜨지 않게 기준선만 저장한다.
            store.setNotifiedRosterKeys(pruneRosterKeys(moves.map(::rosterKeyOf).toSet()))
            return
        }

        val cutoff = kboToday().minusDays(1).toString()
        val fresh = mutableListOf<RosterMove>()
        var changed = false
        for (m in moves) {
            if (!stored.add(rosterKeyOf(m))) continue
            changed = true
            if (m.moveDate >= cutoff) fresh.add(m)
        }
        if (changed) store.setNotifiedRosterKeys(pruneRosterKeys(stored))
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
                ID_FAVORITE_ROSTER_BASE + (rosterKeyOf(m).hashCode() and 0xFFFF),
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
                ID_ROSTER_BASE + (rosterKeyOf(m).hashCode() and 0xFFFF),
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

    private fun rosterKeyOf(m: RosterMove) =
        "${m.moveDate}:${m.playerCode}:${m.moveType}:${m.playerName}"

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
        initialized = false
    }

    private fun seed(game: LotteGameInfo) {
        lastSeqno = game.recentTexts.maxOfOrNull { it.seqno } ?: -1
        lastPitcherCode = game.currentPitcherCode
        seedScores(game)
        lastInning = game.inning
        lastTop = game.isTopInning
        lastStatus = game.status
        lastBasesKey = basesKey(game.onBase1, game.onBase2, game.onBase3)
        lastFavoriteBatterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
            .firstOrNull { it.name == game.currentBatterName }?.playerCode.orEmpty()
    }

    private fun seedScores(game: LotteGameInfo) {
        lastLotteScore = game.lotteScore
        lastOppScore = game.opponentScore
    }

    private fun lotteRosterNames(game: LotteGameInfo): List<String> =
        (game.lotteLineup + game.lotteBenchBatters).map { it.name }

    private fun oppRosterNames(game: LotteGameInfo): List<String> =
        (game.opponentLineup + game.opponentBenchBatters).map { it.name }

    private fun lineupNameByOrder(lineup: List<com.bossxor.lottegiants.domain.LineupSlot>, order: Int): String? {
        if (order <= 0) return null
        return lineup.firstOrNull { it.batOrder == order }?.name?.takeIf { it.isNotBlank() }
    }

    private fun isLotteHomerun(game: LotteGameInfo, text: String): Boolean {
        if (game.lotteLineup.any { it.name.isNotBlank() && text.contains(it.name) }) return true
        if (game.lotteBenchBatters.any { it.name.isNotBlank() && text.contains(it.name) }) return true
        if (text.contains("롯데") && !text.contains(game.opponentName)) return true
        return game.isLotteBatting && !text.contains(game.opponentName)
    }

    private fun parseHrRuns(text: String): Int? {
        if (text.contains("솔로")) return 1
        if (text.contains("만루") || text.contains("그랜드슬램") || text.contains("그랜드 슬램")) return 4
        Regex("""(\d)\s*점\s*홈런""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(\d)\s*타점""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
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
        }
    }

    suspend fun processRace(
        context: Context,
        standings: List<TeamStanding>,
        recentGames: List<com.bossxor.lottegiants.domain.MiniGame> = emptyList(),
    ) {
        val now = racePulse(standings) ?: return
        val prev = parseRacePulse(store.lastRaceFingerprint())
        val alert = raceChangeAlert(prev, now, standings, recentGames)
        store.setLastRaceFingerprint(now.fingerprint())
        if (alert != null) {
            val live = emittingForLive
            emittingForLive = false
            maybeNotify(context, NotificationType.RACE_NUMBER, ID_RACE, alert.first, alert.second)
            emittingForLive = live
        }
    }
}
