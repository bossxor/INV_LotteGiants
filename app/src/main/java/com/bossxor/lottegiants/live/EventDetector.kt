package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.inningLabel

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
    private var lineupAnnouncedFor: String = ""
    private var eighthNotifiedFor: String = ""
    private var extraNotifiedFor: String = ""
    private var lastFavoriteBatterCode: String = ""
    private var lastGameId: String = ""
    private var initialized = false

    suspend fun process(context: Context, game: LotteGameInfo?) {
        if (game == null) return

        if (lastGameId.isNotBlank() && lastGameId != game.gameId) {
            // 새 경기: 인메모리 상태만 리셋 (취소 알림 DataStore 키는 유지)
            resetInMemory()
        }
        lastGameId = game.gameId

        if (!initialized) {
            seed(game)
            initialized = true
            if (game.lotteLineup.isNotEmpty()) lineupAnnouncedFor = game.gameId
            // 스케줄러는 매 실행 새 detector → 이미 끝난/취소된 경기도 DataStore 중복 방지 하에 1회 알림
            if (game.status == GameStatus.CANCELED) {
                notifyCanceled(context, game)
            } else if (game.status == GameStatus.ENDED) {
                notifyEnded(context, game)
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

        if (game.lotteLineup.size >= 9 && lineupAnnouncedFor != game.gameId) {
            val lineup = game.lotteLineup.joinToString(" ") { "${it.batOrder}${it.name}" }
            maybeNotify(
                context, NotificationType.LINEUP, 2010,
                "선발 라인업", "투수 ${game.lotteStartingPitcher.ifBlank { "미정" }} · $lineup"
            )
            lineupAnnouncedFor = game.gameId
        } else if (game.lineupAnnounced && game.lotteLineup.isEmpty() && lineupAnnouncedFor != game.gameId) {
            maybeNotify(
                context, NotificationType.LINEUP, 2010,
                "라인업 발표",
                "선발 ${game.lotteStartingPitcher.ifBlank { "미정" }}" +
                    if (game.opponentStartingPitcher.isNotBlank()) " vs ${game.opponentStartingPitcher}" else "",
            )
            lineupAnnouncedFor = game.gameId
        }

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
        for (t in newTexts) {
            val text = t.text
            val lotteScored = game.lotteScore > lastLotteScore
            val oppScored = game.opponentScore > lastOppScore
            val lotteRuns = (game.lotteScore - lastLotteScore).coerceAtLeast(0)
            val oppRuns = (game.opponentScore - lastOppScore).coerceAtLeast(0)
            val score = "${game.lotteScore}:${game.opponentScore}"
            val isHr = text.contains("홈런")
            val lotteHr = isHr && isLotteHomerun(game, text)

            if (lotteScored) {
                if (lotteHr) {
                    val runs = parseHrRuns(text) ?: lotteRuns.takeIf { it > 0 } ?: 1
                    val who = scorerName(game, text)
                    val title = buildString {
                        append("롯데 홈런! ${runs}점")
                        if (!who.isNullOrBlank()) append(" · $who")
                        append(" · $score")
                    }
                    maybeNotify(context, NotificationType.HOMERUN, 3_000_000 + t.seqno, title, text)
                    store.setHighlight(title)
                    WearBridge.sendScoreEvent(context, title)
                } else {
                    val whoHow = describeLotteScore(game, text)
                    val title = "롯데 득점! $whoHow · $score"
                    maybeNotify(context, NotificationType.SCORE, 1_000_000 + t.seqno, title, text)
                    store.setHighlight(title)
                    WearBridge.sendScoreEvent(context, "$title · ${game.inningLabel}")
                }
            }
            if (oppScored) {
                val n = oppRuns.takeIf { it > 0 } ?: 1
                val title = "${n}점 실점 · $score"
                maybeNotify(context, NotificationType.CONCEDING, 2_000_000 + t.seqno, title, "")
                store.setHighlight(title)
                WearBridge.sendScoreEvent(context, "$title · ${game.inningLabel}")
            }
            if (lotteScored || oppScored) {
                val prevLead = leadOf(lastLotteScore, lastOppScore)
                val nowLead = leadOf(game.lotteScore, game.opponentScore)
                if (prevLead != nowLead) {
                    val title = when (nowLead) {
                        0 -> "동점!"
                        1 -> "롯데 역전!"
                        else -> "${game.opponentName} 역전"
                    }
                    maybeNotify(
                        context, NotificationType.LEAD_CHANGE, 4_000_000 + t.seqno,
                        title, score
                    )
                }
                seedScores(game)
            }
        }
        if (newTexts.isNotEmpty()) lastSeqno = newTexts.maxOf { it.seqno }

        val newPitcherCode = game.currentPitcherCode
        if (newPitcherCode.isNotBlank() &&
            lastPitcherCode.isNotBlank() &&
            newPitcherCode != lastPitcherCode &&
            isBullpenPitcherEntry(game, newPitcherCode)
        ) {
            maybeNotify(
                context, NotificationType.PITCHER_CHANGE, 2401,
                "투수 교체", "${game.currentPitcherName.ifBlank { "투수" }} 등판"
            )
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
            val key = "${game.onBase1}${game.onBase2}${game.onBase3}"
            if (key != lastBasesKey) {
                when {
                    game.onBase1 && game.onBase2 && game.onBase3 ->
                        maybeNotify(
                            context, NotificationType.SCORING_CHANCE, 2601,
                            "만루 찬스!", "${game.currentBatterName.ifBlank { "타석" }} · ${game.inningLabel}"
                        )
                    game.onBase2 || game.onBase3 ->
                        maybeNotify(
                            context, NotificationType.SCORING_CHANCE, 2602,
                            "득점권 찬스", basesLabel(game) + " · ${game.currentBatterName}"
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

    private suspend fun notifyEnded(context: Context, game: LotteGameInfo) {
        if (store.notifiedEndGameId() == game.gameId) return
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString()
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
    }

    suspend fun processRosterMoves(context: Context, moves: List<com.bossxor.lottegiants.domain.RosterMove>) {
        if (moves.isEmpty()) return
        fun keyOf(m: com.bossxor.lottegiants.domain.RosterMove) =
            "${m.moveDate}:${m.playerCode}:${m.moveType}:${m.playerName}"
        val stored = store.notifiedRosterKeys().toMutableSet()
        if (stored.isEmpty()) {
            store.setNotifiedRosterKeys(moves.map(::keyOf).toSet())
            return
        }
        val from = java.time.LocalDate.now().minusDays(1).toString()
        val favorites = store.favoritePlayers()
        val byCode = favorites.associateBy { it.code }
        val byName = favorites.filter { it.name.isNotBlank() }.associateBy { it.name }
        var changed = false
        for (m in moves) {
            val key = keyOf(m)
            if (key in stored) continue
            stored.add(key)
            changed = true
            if (m.moveDate < from) continue
            val label = if (m.isRegister) "등록" else "말소"
            val name = m.playerName
            val fav = m.playerCode.takeIf { it.isNotBlank() }?.let { byCode[it] }
                ?: byName[name]
            if (fav != null) {
                maybeNotify(
                    context, NotificationType.FAVORITE_ROSTER, 2800 + (key.hashCode() and 0xFF),
                    "즐겨찾기 등말소", "${fav.name.ifBlank { name }} $label · ${m.moveDate}",
                )
            } else {
                maybeNotify(
                    context, NotificationType.ROSTER, 2820 + (key.hashCode() and 0xFF),
                    "엔트리 $label", "$name $label · ${m.moveDate}",
                )
            }
        }
        if (changed) store.setNotifiedRosterKeys(stored)
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
        lineupAnnouncedFor = ""
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
        lastBasesKey = "${game.onBase1}${game.onBase2}${game.onBase3}"
        lastFavoriteBatterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
            .firstOrNull { it.name == game.currentBatterName }?.playerCode.orEmpty()
    }

    private fun seedScores(game: LotteGameInfo) {
        lastLotteScore = game.lotteScore
        lastOppScore = game.opponentScore
    }

    private fun leadOf(lotte: Int, opp: Int) = when {
        lotte > opp -> 1
        lotte < opp -> -1
        else -> 0
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

    private fun scorerName(game: LotteGameInfo, text: String): String? =
        (game.lotteLineup + game.lotteBenchBatters)
            .firstOrNull { it.name.isNotBlank() && text.contains(it.name) }
            ?.name

    private fun describeLotteScore(game: LotteGameInfo, text: String): String {
        val who = scorerName(game, text)
        val how = when {
            text.contains("홈런") -> "홈런"
            text.contains("3루타") -> "3루타"
            text.contains("2루타") -> "2루타"
            text.contains("적시") -> "적시타"
            text.contains("희생플라이") || text.contains("희플") -> "희생플라이"
            text.contains("밀어내기") -> "밀어내기"
            text.contains("내야안타") -> "내야안타"
            text.contains("안타") -> "안타"
            text.contains("폭투") -> "폭투"
            text.contains("도루") -> "도루"
            text.contains("실책") -> "실책"
            else -> ""
        }
        return listOfNotNull(who, how.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { "득점" }
    }

    private fun basesLabel(g: LotteGameInfo): String = buildList {
        if (g.onBase1) add(if (g.runnerOn1Order > 0) "1루(${g.runnerOn1Order}번)" else "1루")
        if (g.onBase2) add(if (g.runnerOn2Order > 0) "2루(${g.runnerOn2Order}번)" else "2루")
        if (g.onBase3) add(if (g.runnerOn3Order > 0) "3루(${g.runnerOn3Order}번)" else "3루")
    }.joinToString("·").ifBlank { "주자 없음" }

    private suspend fun maybeNotify(
        context: Context,
        type: NotificationType,
        id: Int,
        title: String,
        text: String,
    ) {
        if (store.isNotificationEnabled(type)) {
            NotificationHelper.notifyEvent(context, type, title, text, id)
        }
    }
}
