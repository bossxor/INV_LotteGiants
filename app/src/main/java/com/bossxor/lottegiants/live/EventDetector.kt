package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.inningLabel

/**
 * 이전 스냅샷과 비교해 이벤트 알림을 발생시킨다.
 * 중복 방지를 위해 마지막으로 처리한 중계 seqno / 상태를 기억한다.
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
    private var initialized = false

    suspend fun process(context: Context, game: LotteGameInfo?) {
        if (game == null) return

        if (!initialized) {
            seed(game)
            initialized = true
            // 라인업이 이미 있으면 발표 알림은 스킵 (앱 첫 기동 시 폭주 방지)
            if (game.lotteLineup.isNotEmpty()) lineupAnnouncedFor = game.gameId
            return
        }

        // 상태 변화: 시작/종료/취소
        val prevStatus = lastStatus
        if (prevStatus != null && prevStatus != game.status) {
            when (game.status) {
                GameStatus.LIVE -> maybeNotify(context, NotificationType.GAME_START_END, 2001,
                    "경기 시작!", "${game.opponentName}전 시작 · ${game.stadium}")
                GameStatus.ENDED -> {
                    val result = when {
                        game.lotteScore > game.opponentScore -> "롯데 승리!"
                        game.lotteScore < game.opponentScore -> "롯데 패배"
                        else -> "무승부"
                    }
                    maybeNotify(context, NotificationType.GAME_START_END, 2002,
                        result, "최종 ${game.lotteScore}:${game.opponentScore} vs ${game.opponentName}")
                }
                GameStatus.CANCELED -> maybeNotify(context, NotificationType.CANCELED, 2003,
                    "경기 취소", "${game.opponentName}전 취소/순연")
                else -> {}
            }
        }
        lastStatus = game.status

        // 라인업 발표
        if (game.lotteLineup.size >= 9 && lineupAnnouncedFor != game.gameId) {
            val lineup = game.lotteLineup.joinToString(" ") { "${it.batOrder}${it.name}" }
            maybeNotify(context, NotificationType.LINEUP, 2010,
                "선발 라인업", "투수 ${game.lotteStartingPitcher.ifBlank { "미정" }} · $lineup")
            lineupAnnouncedFor = game.gameId
        }

        if (game.status != GameStatus.LIVE && game.status != GameStatus.ENDED) {
            seedScores(game)
            return
        }

        // 득점 / 역전 / 홈런 (문자중계 텍스트 활용)
        val newTexts = game.recentTexts.filter { it.seqno > lastSeqno }.sortedBy { it.seqno }
        for (t in newTexts) {
            val text = t.text
            val lotteScored = game.lotteScore > lastLotteScore
            val oppScored = game.opponentScore > lastOppScore
            if (lotteScored || oppScored) {
                val who = if (lotteScored) "롯데" else game.opponentName
                maybeNotify(context, NotificationType.SCORE, 2100 + t.seqno % 100,
                    "$who 득점! ${game.lotteScore}:${game.opponentScore}", text)
                val prevLead = leadOf(lastLotteScore, lastOppScore)
                val nowLead = leadOf(game.lotteScore, game.opponentScore)
                if (prevLead != nowLead) {
                    val title = when (nowLead) {
                        0 -> "동점!"
                        1 -> "롯데 역전!"
                        else -> "${game.opponentName} 역전"
                    }
                    maybeNotify(context, NotificationType.LEAD_CHANGE, 2200 + t.seqno % 100,
                        title, "${game.lotteScore}:${game.opponentScore} · $text")
                }
                seedScores(game)
            }
            if (text.contains("홈런")) {
                val lotteHr = !text.contains(game.opponentName) || text.contains("롯데") ||
                    game.lotteLineup.any { text.contains(it.name) }
                maybeNotify(context, NotificationType.HOMERUN, 2300 + t.seqno % 100,
                    if (lotteHr) "롯데 홈런!" else "홈런", text)
            }
        }
        if (newTexts.isNotEmpty()) lastSeqno = newTexts.maxOf { it.seqno }

        // 투수 교체
        if (game.currentPitcherCode.isNotBlank() &&
            lastPitcherCode.isNotBlank() &&
            game.currentPitcherCode != lastPitcherCode
        ) {
            maybeNotify(context, NotificationType.PITCHER_CHANGE, 2401,
                "투수 교체", "${game.currentPitcherName} 등판")
        }
        if (game.currentPitcherCode.isNotBlank()) lastPitcherCode = game.currentPitcherCode

        // 이닝 교대
        if (lastInning > 0 && (game.inning != lastInning || game.isTopInning != (lastTop == true))) {
            if (game.out == 0 || game.inning != lastInning) {
                maybeNotify(context, NotificationType.INNING_CHANGE, 2501,
                    "${game.inningLabel}", "중간 스코어 롯데 ${game.lotteScore}:${game.opponentScore}")
            }
        }
        lastInning = game.inning
        lastTop = game.isTopInning

        // 득점권 찬스 (롯데 공격 + 2루/3루)
        if (game.isLotteBatting) {
            val key = "${game.onBase1}${game.onBase2}${game.onBase3}"
            if (key != lastBasesKey) {
                when {
                    game.onBase1 && game.onBase2 && game.onBase3 ->
                        maybeNotify(context, NotificationType.SCORING_CHANCE, 2601,
                            "만루 찬스!", "${game.currentBatterName.ifBlank { "타석" }} · ${game.inningLabel}")
                    game.onBase2 || game.onBase3 ->
                        maybeNotify(context, NotificationType.SCORING_CHANCE, 2602,
                            "득점권 찬스", basesLabel(game) + " · ${game.currentBatterName}")
                }
                lastBasesKey = key
            }
        } else {
            lastBasesKey = ""
        }
    }

    private fun seed(game: LotteGameInfo) {
        lastSeqno = game.recentTexts.maxOfOrNull { it.seqno } ?: -1
        lastPitcherCode = game.currentPitcherCode
        seedScores(game)
        lastInning = game.inning
        lastTop = game.isTopInning
        lastStatus = game.status
        lastBasesKey = "${game.onBase1}${game.onBase2}${game.onBase3}"
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

    private fun basesLabel(g: LotteGameInfo): String = buildList {
        if (g.onBase1) add("1루")
        if (g.onBase2) add("2루")
        if (g.onBase3) add("3루")
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
