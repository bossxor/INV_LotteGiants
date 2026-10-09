package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.NamedBases
import com.bossxor.lottegiants.domain.basesKey
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.dhSuffix
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.parseRacePulse
import com.bossxor.lottegiants.domain.planRosterNotifications
import com.bossxor.lottegiants.domain.rosterNotifyKey
import com.bossxor.lottegiants.domain.raceChangeAlert
import com.bossxor.lottegiants.domain.racePulse
import com.bossxor.lottegiants.domain.shouldSendRosterNoneAlert
import com.bossxor.lottegiants.domain.resolvedAtBat
import com.bossxor.lottegiants.domain.LiveEventCursor
import com.bossxor.lottegiants.domain.ScoreLedger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val dispatcher = AlertDispatcher(store)
    suspend fun process(context: Context, game: LotteGameInfo?) = gameMutex.withLock {
        if (game == null) return@withLock
        val stored = LiveEventCursor.decode(store.liveEventCursor())
        val resume = stored.gameId == game.gameId && stored.status != null
        val previous = if (resume) stored else initialCursor(game)
        if (resume && game.status == GameStatus.LIVE &&
            game.recentTexts.maxOfOrNull { it.seqno }?.let { it < previous.seqno } == true) return@withLock
        val batch = dispatcher.batch(context, game.status == GameStatus.LIVE ||
            (game.status == GameStatus.ENDED && previous.status == GameStatus.LIVE))
        if (game.status == GameStatus.CANCELED) notifyCanceled(batch, game)
        if (game.status == GameStatus.LIVE) maybeNotifyGameStart(batch, game)
        lineupMutex.withLock { maybeNotifyLineup(batch, game) }
        var next = previous.copy(status = game.status)
        if (resume && (game.status == GameStatus.LIVE || game.status == GameStatus.ENDED)) {
            val plan = com.bossxor.lottegiants.domain.GameEventReducer.reduce(previous, game,
                batch.policy.favorites, batch.policy.chanceAtBatChange,
                store.notifiedEighthKey(), store.notifiedExtraKey())
            batch.dispatch(plan.alerts, plan.canceled)
            plan.highlight?.let { store.setHighlight(it) }
            if (plan.eighthKey != store.notifiedEighthKey()) store.setNotifiedEighthKey(plan.eighthKey)
            if (plan.extraKey != store.notifiedExtraKey()) store.setNotifiedExtraKey(plan.extraKey)
            next = plan.cursor
        } else if (game.status != GameStatus.LIVE && game.status != GameStatus.ENDED) {
            next = next.copy(focusScore = game.lotteScore, opponentScore = game.opponentScore,
                pitcherCode = game.currentPitcherCode.ifBlank { next.pitcherCode })
        }
        if (game.status == GameStatus.ENDED) notifyEnded(batch, game)
        store.setLiveEventCursor(next.encode())
    }

    private fun initialCursor(game: LotteGameInfo): LiveEventCursor {
        val pool = if (game.isLotteBatting) game.lotteLineup + game.lotteBenchBatters else game.opponentLineup + game.opponentBenchBatters
        fun runner(occupied: Boolean, name: String, code: String, order: Int): String? {
            if (!occupied) return null
            if (name.isNotBlank()) return name
            pool.firstOrNull { code.isNotBlank() && it.playerCode == code }?.let { return it.name }
            return pool.filter { order > 0 && it.batOrder == order }.map { it.name }.filter { it.isNotBlank() }.distinct().singleOrNull()
        }
        val bases = NamedBases(runner(game.onBase1, game.runnerOn1Name, game.runnerOn1Code, game.runnerOn1Order),
            runner(game.onBase2, game.runnerOn2Name, game.runnerOn2Code, game.runnerOn2Order),
            runner(game.onBase3, game.runnerOn3Name, game.runnerOn3Code, game.runnerOn3Order))
        val batter = resolvedAtBat(game, bases)
        return LiveEventCursor(
        gameId = game.gameId, seqno = game.recentTexts.maxOfOrNull { it.seqno } ?: -1,
        focusScore = game.lotteScore, opponentScore = game.opponentScore, status = game.status,
        pitcherCode = game.currentPitcherCode, basesKey = basesKey(game.onBase1, game.onBase2, game.onBase3),
        favoriteBatterCode = pool.firstOrNull { it.name == batter }?.playerCode.orEmpty(),
        chanceBatter = batter, chanceBases = bases,
        seenPitcherCodes = setOf(game.currentPitcherCode).filter { it.isNotBlank() }.toSet(),
        inning = game.inning, isTop = game.isTopInning,
        focusScores = ScoreLedger(game.lotteScore), opponentScores = ScoreLedger(game.opponentScore),
    )
    }

    suspend fun processLineup(context: Context, game: LotteGameInfo?) = lineupMutex.withLock {
        if (game != null) maybeNotifyLineup(dispatcher.batch(context, game.status == GameStatus.LIVE), game)
    }

    private suspend fun maybeNotifyLineup(context: AlertBatch, game: LotteGameInfo) {
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
        val fingerprint = game.gameId + ":" + order.sortedBy { it.batOrder }.joinToString("|") {
            "${it.batOrder}:${it.playerCode}:${it.name}:${it.position}"
        }
        val stored = store.notifiedLineupState()
        val previousFingerprint = store.notifiedLineupFingerprint()
        val already = stored == key || stored == fullKey
        val changed = already && hasOrder && game.status == GameStatus.BEFORE &&
            previousFingerprint.isNotBlank() && previousFingerprint != fingerprint
        if (already && !changed) {
            if (hasOrder && previousFingerprint.isBlank()) store.setNotifiedLineupFingerprint(fingerprint)
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
                if (changed) "선발 라인업 변경" else "선발 라인업 등록", "$matchup\n$pitchers\n$lines",
                gameId = game.gameId, detailTab = "lineup", silentUpdate = changed,
                eventKey = "${game.gameId}:lineup:full",
            )
        } else {
            maybeNotify(
                context, NotificationType.LINEUP, ID_LINEUP_FLAG,
                "라인업 발표", "$matchup\n$pitchers",
                gameId = game.gameId, detailTab = "lineup",
            )
        }
        store.setNotifiedLineupState(key)
        if (hasOrder) store.setNotifiedLineupFingerprint(fingerprint)
        // '변화 없음'은 다음 정상 등말소 조회에서만 확정한다.
    }

    /**
     * 당일 등말소 공시가 없을 때 하루 1회.
     * 라인업 알림과 같은 시점이 기본이고, 경기가 없으면 14시 이후 첫 빈 폴링에서 보낸다.
     */
    private suspend fun maybeNotifyRosterNone(context: AlertBatch, allowWithoutLineup: Boolean = false) {
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

    private suspend fun notifyEnded(context: AlertBatch, game: LotteGameInfo) {
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

    private suspend fun notifyCanceled(context: AlertBatch, game: LotteGameInfo) {
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
        GameSchedulerWorker.cancelGameAlarms(context.context, game.gameId)
    }

    /**
     * 등말소 공시 알림. 새 공시만 골라 알리고, 이미 알린 키는 DataStore에 남겨 중복을 막는다.
     * 여러 명이 한꺼번에 공시되면 알림이 쏟아지지 않게 한 건으로 묶는다.
     */
    suspend fun processRosterMoves(context: Context, moves: List<RosterMove>) = rosterMutex.withLock {
        processRosterMovesLocked(dispatcher.batch(context, false), moves)
    }

    private suspend fun processRosterMovesLocked(context: AlertBatch, moves: List<RosterMove>) {
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

        val favorites = context.policy.favorites
        val byCode = favorites.filter { it.code.isNotBlank() }.associateBy { it.code }
        val byName = favorites.filter { it.name.isNotBlank() }.associateBy { it.name }
        val favoriteAlertsEnabled = NotificationType.FAVORITE_ROSTER in context.policy.enabled
        val others = mutableListOf<RosterMove>()
        for (m in fresh) {
            val fav = m.playerCode.takeIf { it.isNotBlank() }?.let { byCode[it] } ?: byName[m.playerName]
            if (fav == null || !favoriteAlertsEnabled) {
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

    private suspend fun maybeNotifyGameStart(context: AlertBatch, game: LotteGameInfo) {
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

    private suspend fun maybeNotify(context: AlertBatch, type: NotificationType, id: Int, title: String,
        text: String, gameId: String = "", detailTab: String? = null, silentUpdate: Boolean = false, eventKey: String = "") =
        context.dispatch(listOf(com.bossxor.lottegiants.domain.PlannedAlert(type, id, title, text, gameId, detailTab, silentUpdate, eventKey)))
    companion object {
        private val gameMutex = Mutex()
        private val lineupMutex = Mutex()
        private val rosterMutex = Mutex()
        private val raceMutex = Mutex()
    }

    suspend fun processRace(
        context: Context,
        standings: List<TeamStanding>,
        recentGames: List<com.bossxor.lottegiants.domain.MiniGame> = emptyList(),
    ) = raceMutex.withLock {
        val teamCode = store.myTeamCode()
        val now = racePulse(standings, teamCode) ?: return@withLock
        val prev = parseRacePulse(store.lastRaceFingerprint())
        val alert = raceChangeAlert(prev, now, standings, recentGames, teamCode)
        store.setLastRaceFingerprint(now.fingerprint())
        if (alert != null) {
            maybeNotify(dispatcher.batch(context, false), NotificationType.RACE_NUMBER, ID_RACE, alert.first, alert.second)
        }
    }
}
