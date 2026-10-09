package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType

internal data class PlannedAlert(val type: NotificationType, val id: Int, val title: String, val text: String,
    val gameId: String = "", val detailTab: String? = null, val silentUpdate: Boolean = false, val eventKey: String = "")
internal data class GameEventPlan(val cursor: LiveEventCursor, val alerts: List<PlannedAlert>,
    val canceled: List<Pair<Int, String>>, val highlight: String?, val eighthKey: String, val extraKey: String)

/** 직전 커서와 이번 경기로만 계산한다. Android·저장소·네트워크 호출이 없다. */
internal object GameEventReducer {
    fun reduce(previous: LiveEventCursor, game: LotteGameInfo, favorites: List<FavoritePlayer>,
        chanceAtBatChange: Boolean, eighthKey: String = "", extraKey: String = ""): GameEventPlan =
        GameEventReduction(previous, eighthKey, extraKey).reduce(game, favorites, chanceAtBatChange)
}

private class GameEventReduction(previous: LiveEventCursor, private var eighthNotifiedFor: String = "",
    private var extraNotifiedFor: String = "") {
    private var focusScores = previous.focusScores
    private var opponentScores = previous.opponentScores
    private var lastSeqno = previous.seqno
    private var lastPitcherCode = previous.pitcherCode
    private var lastLotteScore = previous.focusScore
    private var lastOppScore = previous.opponentScore
    private var lastInning = previous.inning ?: -1
    private var lastTop = previous.isTop
    private var lastBasesKey = previous.basesKey
    private var lastFavoriteBatterCode = previous.favoriteBatterCode
    private var lastChanceBatter = previous.chanceBatter
    private var lastChanceBases = previous.chanceBases
    private var seenPitcherCodes = previous.seenPitcherCodes.toMutableSet()
    private val alerts = mutableListOf<PlannedAlert>()
    private val canceled = mutableListOf<Pair<Int, String>>()
    private var highlight: String? = null
    fun reduce(game: LotteGameInfo, favorites: List<FavoritePlayer>, chanceAtBatChange: Boolean): GameEventPlan {
        val favCodes = favorites.map { it.code }.toSet()
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
        val atBatNow = resolvedAtBat(game, currentBases)
        val batterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
            .firstOrNull { it.name == atBatNow }?.playerCode.orEmpty()
            .ifBlank { "" }
        if (batterCode.isNotBlank() &&
            batterCode in favCodes &&
            batterCode != lastFavoriteBatterCode &&
            game.status == GameStatus.LIVE
        ) {
            val favName = favorites.firstOrNull { it.code == batterCode }?.name
                ?.ifBlank { atBatNow } ?: atBatNow
            emit(
                NotificationType.FAVORITE_AT_BAT, 2711,
                "즐겨찾기 타석", "$favName · ${game.inningLabel}",
                gameId = game.gameId, detailTab = "relay",
            )
        }
        if (batterCode.isNotBlank()) lastFavoriteBatterCode = batterCode
        else if (atBatNow.isBlank()) lastFavoriteBatterCode = ""

        run {
            val score = "${game.lotteScore}:${game.opponentScore}"
            fun emitScores(focus: Boolean) {
                val reconciled = reconcileScores(if (focus) focusScores else opponentScores,
                    game.recentTexts, lastSeqno,
                    if (focus) lastLotteScore else lastOppScore,
                    if (focus) game.lotteScore else game.opponentScore,
                    if (focus) game.isHome else !game.isHome,
                    if (focus) lotteRosterNames(game) else oppRosterNames(game))
                if (focus) focusScores = reconciled.ledger else opponentScores = reconciled.ledger
                for (id in reconciled.supersededIds) {
                    canceled += ((if (focus) 1_000_000 else 2_000_000) + id) to "${game.gameId}:score:$focus:$id"
                }
                for (update in reconciled.updates) {
                    if (update.record.id in reconciled.supersededIds) continue
                    val play = update.record.play
                    val attackingNow = if (focus) game.isLotteBatting else !game.isLotteBatting
                    val isLive = game.status == GameStatus.LIVE && attackingNow &&
                        (play.inning == 0 || play.inning == game.inning) &&
                        (play.isTop == null || play.isTop == game.isTopInning)
                    val atBat = if (isLive) atBatNow else ""
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
                    // ID는 득점 사건에 고정한다. 홈런 근거가 늦게 와도 기존 알림을 갱신한다.
                    val id = (if (focus) 1_000_000 else 2_000_000) + update.record.id
                    emit(type, id,
                        if (update.correction) "득점 정정 · $score" else title,
                        if (update.correction) "공식 점수가 정정되었습니다 · ${game.inningLabel}" else body,
                        gameId = game.gameId, detailTab = "relay", silentUpdate = update.silent,
                        eventKey = "${game.gameId}:score:$focus:${update.record.id}")
                    highlight = (if (update.correction) "득점 정정 · $score" else title)
                }
            }
            emitScores(true)
            emitScores(false)
            leadChangeTitle(lastLotteScore, lastOppScore, game.lotteScore, game.opponentScore,
                game.opponentName, game.focusName())?.let { title ->
                emit(NotificationType.LEAD_CHANGE,
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
                emit(
                    NotificationType.FAVORITE_PITCHING, 2712,
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
                emit(
                    NotificationType.PITCHER_CHANGE, 2401,
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
                emit(
                    NotificationType.FAVORITE_PITCHING, 2713,
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
                emit(
                    NotificationType.INNING_CHANGE, 2501,
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
            emit(
                NotificationType.EIGHTH_INNING, 2510,
                "8회말!", "${game.focusName()} ${game.lotteScore}:${game.opponentScore} · ${game.opponentName}",
                gameId = game.gameId,
            )
            eighthNotifiedFor = key
        }

        // 연장
        if (game.inning >= 10 && extraNotifiedFor != game.gameId) {
            emit(
                NotificationType.EXTRA_INNINGS, 2520,
                "연장 시작!", "${game.inningLabel} · ${game.lotteScore}:${game.opponentScore}",
                gameId = game.gameId,
            )
            extraNotifiedFor = game.gameId
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
            val atBat = atBatNow
            val entered = (loaded && !(was1 && was2 && was3)) || (nowChance && !(was2 || was3))
            val changedBatter = nowChance && atBat.isNotBlank() && lastChanceBatter.isNotBlank() &&
                atBat != lastChanceBatter && chanceAtBatChange
            if (!game.isSuspended && (entered || changedBatter)) {
                val alert = formatScoringChanceAlert(loaded, currentBases.label(), atBat,
                    game.inningLabel, game.out, game.onBase1, game.onBase2, game.onBase3)
                emit(NotificationType.SCORING_CHANCE,
                    if (entered) { if (loaded) 2601 else 2602 } else 2603,
                    alert.title, alert.text, gameId = game.gameId, detailTab = "relay")
            }
            // 점유 루가 같아도 매번 갱신한다. 타석만 바뀐 경우와 득점 플레이를 구분한다.
            lastBasesKey = key
            lastChanceBatter = atBat
            lastChanceBases = currentBases
        }
        val cursor = LiveEventCursor(gameId = game.gameId, seqno = lastSeqno,
            focusScore = lastLotteScore, opponentScore = lastOppScore, basesKey = lastBasesKey,
            pitcherCode = lastPitcherCode, favoriteBatterCode = lastFavoriteBatterCode, status = game.status,
            chanceBatter = lastChanceBatter, seenPitcherCodes = seenPitcherCodes.toSet(), chanceBases = lastChanceBases,
            inning = lastInning.takeIf { it > 0 }, isTop = lastTop, focusScores = focusScores, opponentScores = opponentScores)
        return GameEventPlan(cursor, alerts.toList(), canceled.toList(), highlight, eighthNotifiedFor, extraNotifiedFor)
    }
    private fun emit(type: NotificationType, id: Int, title: String, text: String, gameId: String = "",
        detailTab: String? = null, silentUpdate: Boolean = false, eventKey: String = "") {
        alerts += PlannedAlert(type, id, title, text, gameId, detailTab, silentUpdate, eventKey)
    }
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

    private fun namedBasesFromGame(game: LotteGameInfo): NamedBases = NamedBases(
        first = game.runnerOn1Name.takeIf { game.onBase1 && it.isNotBlank() } ?: runnerName(game, game.onBase1, game.runnerOn1Order, game.runnerOn1Code),
        second = game.runnerOn2Name.takeIf { game.onBase2 && it.isNotBlank() } ?: runnerName(game, game.onBase2, game.runnerOn2Order, game.runnerOn2Code),
        third = game.runnerOn3Name.takeIf { game.onBase3 && it.isNotBlank() } ?: runnerName(game, game.onBase3, game.runnerOn3Order, game.runnerOn3Code),
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
        return null
    }

}
