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

/** 중계 병합·루타·존 차트용 순수 변환. GiantsRepository에서 떼어 냈다. */

private const val RELAY_TAG = "GiantsRepo"

internal data class RutaHighlightClip(val text: String, val url: String)

internal fun LotteGameInfo.fillSuspendFromRelay(): LotteGameInfo =
    withSuspendFilled(recentTexts.joinToString(" ") { it.text })

internal fun extractRutaHighlight(obj: JsonObject): RutaHighlightClip {
    val data = obj["data"] as? JsonObject ?: obj
    val textKeys = listOf("text", "message", "title", "highlight", "content")
    val urlKeys = listOf("url", "link", "videoUrl", "video", "hls", "src")
    var text = ""
    var url = ""
    for (k in textKeys) {
        val el = data[k] ?: continue
        val p = el as? kotlinx.serialization.json.JsonPrimitive ?: continue
        p.content.takeIf { it.isNotBlank() }?.let { text = it; break }
    }
    for (k in urlKeys) {
        val el = data[k] ?: continue
        val p = el as? kotlinx.serialization.json.JsonPrimitive ?: continue
        val v = p.content.trim()
        if (v.startsWith("http")) { url = v; break }
    }
    if (url.isBlank()) {
        fun walk(o: kotlinx.serialization.json.JsonObject) {
            if (url.isNotBlank()) return
            o.forEach { (_, v) ->
                when (v) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        val s = v.content.trim()
                        if (url.isBlank() && (s.startsWith("http://") || s.startsWith("https://"))) url = s
                    }
                    is kotlinx.serialization.json.JsonObject -> walk(v)
                    is kotlinx.serialization.json.JsonArray -> v.forEach { el ->
                        (el as? kotlinx.serialization.json.JsonObject)?.let { walk(it) }
                    }
                }
            }
        }
        walk(data)
    }
    return RutaHighlightClip(text, url)
}

/** 네이버 relay metricOption → 롯데 승리확률 시계열 */
internal fun buildWinProbFromRelay(relay: TextRelayData, isHome: Boolean): List<WinProbPoint> {
    fun rateOf(m: MetricOptionDto?): Double? =
        WinProb.focusWinProb(m?.homeTeamWinRate, m?.awayTeamWinRate, isHome)
    val fromPlates = relay.textRelays.mapIndexedNotNull { idx, tr ->
        val r = rateOf(tr.metricOption) ?: return@mapIndexedNotNull null
        WinProbPoint(seq = idx, label = "${tr.inn}회", homeProb = r)
    }
    if (fromPlates.isNotEmpty()) return fromPlates
    val last = rateOf(relay.lastValidMetricOption) ?: return emptyList()
    return listOf(WinProbPoint(seq = 0, label = "현재", homeProb = last))
}

internal fun mergeRelay(base: LotteGameInfo, relay: TextRelayData): LotteGameInfo = runCatching {
    mergeRelayUnsafe(base, relay)
}.onFailure {
    Log.e(RELAY_TAG, "mergeRelay ${base.gameId}", it)
}.getOrDefault(base)

internal fun mergeRelayUnsafe(base: LotteGameInfo, relay: TextRelayData): LotteGameInfo {
    val isHome = base.isHome
    val lotteLineupDto = if (isHome) relay.homeLineup else relay.awayLineup
    val oppLineupDto = if (isHome) relay.awayLineup else relay.homeLineup

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

    fun LineupDto.currentByOrder(): Map<Int, LineupBatterDto> =
        batter.filter { it.batOrder in 1..9 }
            .groupBy { it.batOrder }
            .mapNotNull { (order, list) ->
                list.maxByOrNull { it.seqno }?.let { order to it }
            }
            .toMap()

    fun LineupDto.startersByOrder(): Map<Int, LineupBatterDto> {
        val withOrder = batter.filter { it.batOrder in 1..9 }
            .groupBy { it.batOrder }
            .mapNotNull { (order, list) ->
                list.minByOrNull { it.seqno }?.let { order to it }
            }
            .toMap()
        if (withOrder.isNotEmpty()) return withOrder
        val starters = batter.filter { it.seqno <= 1 }
            .ifEmpty { batter }
            .distinctBy { it.pcode.ifBlank { it.name } }
            .take(9)
        return starters.mapIndexed { idx, b -> (idx + 1) to b }.toMap()
    }

    fun bestSeasonAvg(b: LineupBatterDto, peers: List<LineupBatterDto> = emptyList()): Double? {
        b.seasonHra?.takeIf { it > 0.0 }?.let { return it }
        peers.firstOrNull { it.pcode == b.pcode }?.seasonHra?.takeIf { it > 0.0 }?.let { return it }
        return peers.mapNotNull { it.seasonHra }.firstOrNull { it > 0.0 }
    }

    fun mapBatter(order: Int, b: LineupBatterDto, peers: List<LineupBatterDto> = emptyList()) = LineupSlot(
        batOrder = order,
        name = b.name,
        position = b.posName.orEmpty(),
        seasonAvg = bestSeasonAvg(b, peers),
        todayHits = b.hit,
        todayAtBats = b.ab,
        todayPa = b.pa,
        todayRbi = b.rbi,
        todayRun = b.run,
        todayAvg = b.todayHra,
        isSubstitute = b.seqno > 1,
        playerCode = b.pcode,
        backNumber = b.backnum.orEmpty(),
        hitType = b.hitType.orEmpty(),
    )

    fun LineupDto.substituteBatters(): List<LineupSlot> =
        batter.filter { it.batOrder in 1..9 }
            .groupBy { it.batOrder }
            .flatMap { (order, list) ->
                val starterSeq = list.minOfOrNull { it.seqno } ?: return@flatMap emptyList()
                list.filter { it.seqno > starterSeq }
                    .sortedBy { it.seqno }
                    .map { mapBatter(order, it, list).copy(isSubstitute = true) }
            }
            .sortedWith(compareBy({ it.batOrder }, { it.name }))

    fun mapPitchers(dto: LineupDto?): List<PitcherLine> =
        dto?.pitcher.orEmpty().sortedBy { it.seqno }.map { p ->
            PitcherLine(
                name = p.name,
                playerCode = p.pcode,
                backNumber = p.backnum.orEmpty(),
                innings = p.inn.orEmpty(),
                hits = p.hit ?: 0,
                runs = p.run ?: 0,
                earnedRuns = p.er ?: 0,
                strikeouts = p.kk ?: p.so ?: 0,
                walks = p.bb ?: 0,
                pitchCount = p.pitchCount ?: p.pitchcnt ?: p.ballCount ?: 0,
                battersFaced = p.bf ?: 0,
                homeRunsAllowed = p.hr ?: 0,
                seasonEra = p.seasonEra.orEmpty(),
                seqno = p.seqno,
            )
        }

    fun extractPitchLocations(): List<com.bossxor.lottegiants.domain.PitchLocation> {
        return relay.textRelays.flatMap { tr ->
            val stuffByCount = tr.textOptions
                .mapNotNull { opt ->
                    val stuff = opt.stuff?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // textOptions는 시간순; ballcount 매칭이 어려워 순서 보조로 stuff만 보관
                    stuff
                }
            tr.ptsOptions.mapIndexed { idx, pts ->
                val x = pts.crossPlateX?.toFloat() ?: return@mapIndexed null
                val yRaw = pts.crossPlateY
                val yFromPhysics = estimatePlateHeightFt(pts)
                // 네이버 일부 경기는 crossPlateY가 고정값(0.7083)으로 깨져 있음 → 궤적 추정값 사용
                val yBroken = yRaw == null || yRaw < 1.0 || yRaw > 5.0
                val y = when {
                    !yBroken -> yRaw!!.toFloat()
                    yFromPhysics != null -> yFromPhysics
                    else -> return@mapIndexed null
                }
                val speedKmh = pts.vy0?.let { kotlin.math.abs(it) * 1.09728 }?.toInt() ?: 0
                val pitchType = stuffByCount.getOrNull(idx)
                    ?: stuffByCount.lastOrNull().orEmpty()
                com.bossxor.lottegiants.domain.PitchLocation(
                    x = x,
                    y = y,
                    speed = speedKmh,
                    pitchType = pitchType,
                    result = "",
                    inning = if (pts.inn > 0) pts.inn else tr.inn,
                    topSz = (pts.topSz ?: 3.5).toFloat(),
                    bottomSz = (pts.bottomSz ?: 1.5).toFloat(),
                )
            }.filterNotNull()
        }
    }

    val lotteStarters = lotteLineupDto?.startersByOrder().orEmpty()
    val lotteLineup = lotteStarters.entries.sortedBy { it.key }.map { (order, b) ->
        val peers = lotteLineupDto?.batter.orEmpty().filter { it.batOrder == order }
        mapBatter(order, b, peers)
    }
    val oppStarters = oppLineupDto?.startersByOrder().orEmpty()
    val opponentLineup = oppStarters.entries.sortedBy { it.key }.map { (order, b) ->
        val peers = oppLineupDto?.batter.orEmpty().filter { it.batOrder == order }
        mapBatter(order, b, peers)
    }
    val lotteBenchBatters = lotteLineupDto?.substituteBatters().orEmpty()
    val opponentBenchBatters = oppLineupDto?.substituteBatters().orEmpty()
    val lottePitchers = mapPitchers(lotteLineupDto)
    val opponentPitchers = mapPitchers(oppLineupDto)
    val pitchLocations = extractPitchLocations()

    val latestTextState = relay.textRelays.filter { it.inn == relay.inn && it.homeOrAway == relay.homeOrAway }
        .flatMap { it.textOptions }.filter { it.currentGameState != null }.maxByOrNull { it.seqno }?.currentGameState
    val relayState = relay.currentGameState
    // textOptions는 과거 사건의 상태다. 현재 타석/카운트/점수 정정을 덮지 않는다.
    val state = relayState ?: latestTextState
    val isTop = relay.homeOrAway != "1"
    val isLotteBatting = if (isHome) !isTop else isTop

    val battingLineupDto = if (isTop) {
        if (isHome) oppLineupDto else lotteLineupDto
    } else {
        if (isHome) lotteLineupDto else oppLineupDto
    }
    val battingOrder = battingLineupDto?.currentByOrder().orEmpty()
    val batterCode = state?.batter.orEmpty()
    val currentBatter = battingLineupDto?.batter.orEmpty().firstOrNull { it.pcode == batterCode }
    val nextOrder = currentBatter?.let { com.bossxor.lottegiants.domain.nextBatOrder(it.batOrder) }
    val nextBatter = nextOrder?.let { battingOrder[it] }

    val inningScores = relay.inningScore
    fun Map<String, String>.ordered(): List<String> =
        entries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
            .sortedBy { it.first }.map { it.second }

    val texts = relay.textRelays
        .flatMap { tr ->
            val isTop = when (tr.homeOrAway) {
                "1" -> false
                "0", "2" -> true
                else -> if (tr.homeOrAway.isBlank()) null else tr.homeOrAway != "1"
            }
            tr.textOptions.map {
                val st = it.currentGameState
                RelayText(
                    seqno = it.seqno,
                    text = it.text,
                    type = it.type,
                    inning = tr.inn,
                    isTopInning = isTop,
                    out = st?.out?.toIntOrNull(),
                    ball = st?.ball?.toIntOrNull(),
                    strike = st?.strike?.toIntOrNull(),
                    batterTitle = tr.title.orEmpty().trim(),
                    batterCode = st?.batter.orEmpty(),
                    homeScore = st?.homeScore?.toIntOrNull(),
                    awayScore = st?.awayScore?.toIntOrNull(),
                    base1Code = st?.base1,
                    base2Code = st?.base2,
                    base3Code = st?.base3,
                )
            }
        }
        .filter { it.type != 99 && it.text.isNotBlank() }
        .sortedByDescending { it.seqno }

    fun runnerName(raw: String?): String {
        if (!runnerOccupied(raw)) return ""
        val order = raw?.toIntOrNull()?.takeIf { it in 1..9 }
        if (order != null) return battingOrder[order]?.name.orEmpty()
        return names[runnerPlayerCodeFromRelay(raw)].orEmpty()
    }
    val apiBases = com.bossxor.lottegiants.domain.NamedBases(runnerName(state?.base1).ifBlank { null },
        runnerName(state?.base2).ifBlank { null }, runnerName(state?.base3).ifBlank { null })
    // 타순이 유지되는 대주자 교체도 이름을 명시한 문구로 반영한다.
    val currentHalfTexts = texts.filter { it.inning == relay.inn && (it.isTopInning == null || it.isTopInning == isTop) }
    val namedBases = com.bossxor.lottegiants.domain.advanceNamedRunners(apiBases,
        currentHalfTexts.filter {
            val sub = com.bossxor.lottegiants.domain.parseSubstitution(it.text)
            val raw = when (sub?.base) { 1 -> state?.base1; 2 -> state?.base2; 3 -> state?.base3; else -> null }
            val order = battingLineupDto?.batter.orEmpty().firstOrNull { it.name == sub?.outgoing }?.batOrder
            sub?.role == com.bossxor.lottegiants.domain.SubstitutionRole.RUNNER &&
                ((order != null && order > 0 && raw?.toIntOrNull() == order) || runnerName(raw) == sub.outgoing || runnerName(raw) == sub.incoming)
        }, names.values.toList())
    val pitcherCode = state?.pitcher.orEmpty()
    // 종료·취소 후에도 중계 JSON에 마지막 주자/카운트가 남아 다이아몬드가 켜진 채로 보인다.
    val liveSituation = base.status == GameStatus.LIVE
    return base.copy(
        lotteScore = state?.run { if (isHome) homeScore else awayScore }?.toIntOrNull() ?: base.lotteScore,
        opponentScore = state?.run { if (isHome) awayScore else homeScore }?.toIntOrNull() ?: base.opponentScore,
        inning = if (relay.inn > 0) relay.inn else base.inning,
        isTopInning = if (relay.inn > 0) isTop else base.isTopInning,
        // relay가 볼카운트를 못 주면 KBO 공식 값을 유지한다 (0으로 덮지 않는다)
        strike = if (liveSituation) state?.strike?.toIntOrNull() ?: base.strike else 0,
        ball = if (liveSituation) state?.ball?.toIntOrNull() ?: base.ball else 0,
        out = if (liveSituation) state?.out?.toIntOrNull() ?: base.out else 0,
        onBase1 = liveSituation && mergeRunner(state?.base1, base.onBase1, state == null),
        onBase2 = liveSituation && mergeRunner(state?.base2, base.onBase2, state == null),
        onBase3 = liveSituation && mergeRunner(state?.base3, base.onBase3, state == null),
        // 네이버 baseN은 선수코드일 수 있다. 타순을 맞추지 않으면 득점권 알림에서 루 이름이 빠진다.
        runnerOn1Order = if (liveSituation) {
            runnerOrderFromRelay(
                state?.base1,
                battingOrder.mapValues { it.value.pcode to it.value.name },
                names,
                base.runnerOn1Order,
            )
        } else {
            0
        },
        runnerOn2Order = if (liveSituation) {
            runnerOrderFromRelay(
                state?.base2,
                battingOrder.mapValues { it.value.pcode to it.value.name },
                names,
                base.runnerOn2Order,
            )
        } else {
            0
        },
        runnerOn3Order = if (liveSituation) {
            runnerOrderFromRelay(
                state?.base3,
                battingOrder.mapValues { it.value.pcode to it.value.name },
                names,
                base.runnerOn3Order,
            )
        } else {
            0
        },
        runnerOn1Code = if (liveSituation) runnerPlayerCodeFromRelay(state?.base1) else "",
        runnerOn2Code = if (liveSituation) runnerPlayerCodeFromRelay(state?.base2) else "",
        runnerOn3Code = if (liveSituation) runnerPlayerCodeFromRelay(state?.base3) else "",
        currentPitcherName = names[pitcherCode]
            ?: listOf(lottePitchers, opponentPitchers).flatten()
                .firstOrNull { it.playerCode == pitcherCode }?.name
            ?: base.currentPitcherName,
        currentPitcherCode = pitcherCode,
        currentBatterName = names[batterCode]
            ?: currentBatter?.name
            ?: base.currentBatterName,
        runnerOn1Name = if (liveSituation) namedBases.first.orEmpty() else "",
        runnerOn2Name = if (liveSituation) namedBases.second.orEmpty() else "",
        runnerOn3Name = if (liveSituation) namedBases.third.orEmpty() else "",
        currentBatterCode = batterCode,
        currentBatterOrder = currentBatter?.batOrder ?: 0,
        nextBatterName = nextBatter?.name.orEmpty(),
        isLotteBatting = if (relay.inn > 0) isLotteBatting else base.isLotteBatting,
        lotteStartingPitcher = lotteLineupDto?.pitcher?.minByOrNull { it.seqno }?.name
            ?: base.lotteStartingPitcher,
        opponentStartingPitcher = oppLineupDto?.pitcher?.minByOrNull { it.seqno }?.name
            ?: base.opponentStartingPitcher,
        lotteLineup = lotteLineup,
        opponentLineup = opponentLineup,
        lotteBenchBatters = lotteBenchBatters,
        opponentBenchBatters = opponentBenchBatters,
        lottePitchers = lottePitchers,
        opponentPitchers = opponentPitchers,
        lotteInningScores = (if (isHome) inningScores?.home else inningScores?.away)
            ?.ordered().orEmpty().ifEmpty { base.lotteInningScores },
        opponentInningScores = (if (isHome) inningScores?.away else inningScores?.home)
            ?.ordered().orEmpty().ifEmpty { base.opponentInningScores },
        lotteHits = state?.run { if (isHome) homeHit else awayHit }?.toIntOrNull() ?: base.lotteHits,
        opponentHits = state?.run { if (isHome) awayHit else homeHit }?.toIntOrNull() ?: base.opponentHits,
        lotteErrors = state?.run { if (isHome) homeError else awayError }?.toIntOrNull() ?: base.lotteErrors,
        opponentErrors = state?.run { if (isHome) awayError else homeError }?.toIntOrNull() ?: base.opponentErrors,
        lotteBb = state?.run { if (isHome) homeBallFour else awayBallFour }?.toIntOrNull() ?: base.lotteBb,
        opponentBb = state?.run { if (isHome) awayBallFour else homeBallFour }?.toIntOrNull() ?: base.opponentBb,
        recentTexts = texts,
        pitchLocations = pitchLocations,
    )
}

/**
 * 투구 궤적(초기 위치·속도·가속도)으로 홈플레이트 높이(ft)를 추정한다.
 * 네이버 crossPlateY가 깨진 경기에서 존 차트용.
 */
internal fun estimatePlateHeightFt(p: PtsOptionDto): Float? {
    val y0 = p.y0 ?: return null
    val vy0 = p.vy0 ?: return null
    val ay = p.ay ?: return null
    val z0 = p.z0 ?: return null
    val vz0 = p.vz0 ?: return null
    val az = p.az ?: return null
    val yPlate = 1.417 // feet — 플레이트 앞면
    // y(t) = y0 + vy0*t + 0.5*ay*t^2 = yPlate
    val a = 0.5 * ay
    val b = vy0
    val c = y0 - yPlate
    val t = when {
        kotlin.math.abs(a) < 1e-6 -> {
            if (kotlin.math.abs(b) < 1e-6) return null
            -c / b
        }
        else -> {
            val disc = b * b - 4 * a * c
            if (disc < 0) return null
            val sqrt = kotlin.math.sqrt(disc)
            val t1 = (-b - sqrt) / (2 * a)
            val t2 = (-b + sqrt) / (2 * a)
            listOf(t1, t2).firstOrNull { it > 0.05 && it < 1.5 } ?: return null
        }
    }
    if (t <= 0) return null
    val z = z0 + vz0 * t + 0.5 * az * t * t
    return z.toFloat().takeIf { it in 0.5f..5.5f }
}

/**
 * 중계 base 필드 점유 여부만 병합한다. 타순 해석은 [com.bossxor.lottegiants.domain.runnerOrderFromRelay].
 */
internal fun mergeRunner(relayRaw: String?, kboValue: Boolean, noRelayState: Boolean): Boolean {
    if (noRelayState) return kboValue
    val raw = relayRaw ?: return kboValue
    return runnerOccupied(raw)
}

internal fun hotColdCellsFor(game: LotteGameInfo?): List<com.bossxor.lottegiants.domain.HotColdCell> {
    val preview = game?.preview ?: return emptyList()
    val currentName = game.currentBatterName
    val keyed = listOfNotNull(preview.lotteKeyBatter, preview.opponentKeyBatter)
    val fromCurrent = keyed.firstOrNull { currentName.isNotBlank() && it.name == currentName }?.hotCold.orEmpty()
    val zones = fromCurrent.ifEmpty {
        preview.lotteKeyBatter?.hotCold.orEmpty().ifEmpty {
            preview.opponentKeyBatter?.hotCold.orEmpty()
        }
    }
    return zones.map { it.toCell() }
}
