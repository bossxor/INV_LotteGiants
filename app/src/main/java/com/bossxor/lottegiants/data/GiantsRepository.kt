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
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.MatchupRecord
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.PlayerDetail
import com.bossxor.lottegiants.domain.PreviewBatter
import com.bossxor.lottegiants.domain.PreviewPitcher
import com.bossxor.lottegiants.domain.PreviewTeamLine
import com.bossxor.lottegiants.domain.RelayText
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.StadiumWeather
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.cancelDisplayLabel
import com.bossxor.lottegiants.domain.normalizeCancelReason
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.domain.resolveStadiumCoord
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.weatherSummaryKo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GiantsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val api: NaverSportsApi = NaverSportsApi.create()
    private val weatherApi: WeatherApi = WeatherApi.create()
    private val kboApi: KboMobileApi = KboMobileApi.create()
    private val keuboApi: KeuboApi = KeuboApi.create()
    private val rutaApi: RutaApi = RutaApi.create()
    val store = SnapshotStore(appContext)

    /** 종료된 이닝 문자중계 캐시 (gameId → inning → relays). 현재 이닝은 매번 재조회. */
    private val relayInningCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<TextRelayDto>>>()

    /**
     * 어제~다음 7일 일정을 조회하고, 롯데 경기가 있으면 relay를 합쳐
     * 스냅샷을 만들어 저장한 뒤 반환한다.
     */
    suspend fun refreshSnapshot(): LiveSnapshot {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val games = api.getGames(
            fromDate = today.minusDays(21).format(fmt),
            toDate = today.plusDays(7).format(fmt),
        ).result?.games.orEmpty().filter { it.categoryId == "kbo" }

        val todayStr = today.format(fmt)
        val yesterdayStr = today.minusDays(1).format(fmt)
        val todayGames = games.filter { it.gameDate == todayStr }
        val lotteToday = todayGames.firstOrNull { it.involvesLotte() }
        val otherGames = todayGames.filter { !it.involvesLotte() }.map { it.toMiniGame() }
        val yesterdayGames = games.filter { it.gameDate == yesterdayStr }.map { it.toMiniGame() }

        val rutaConnected = tryConnectRuta()

        var lotteInfo = lotteToday?.toLotteBase()
        var relayData: TextRelayData? = null
        if (lotteToday != null && lotteInfo != null) {
            relayData = runCatching { fetchFullRelay(lotteToday.gameId) }.getOrNull()
            if (relayData != null) {
                lotteInfo = mergeRelay(lotteInfo, relayData)
            }
            lotteInfo = enrichGameSummary(lotteInfo, games)
        }

        val nextLotte = games
            .filter { it.gameDate > todayStr && it.involvesLotte() && !it.cancel }
            .minByOrNull { it.gameDateTime }
            ?.toLotteBase()
            ?.let { enrichGameSummary(it, games) }

        // 1순위 루타 extras → 2순위 네이버 metricOption WPA
        val focusGame = lotteInfo ?: nextLotte
        val rutaExtras = if (focusGame != null && rutaConnected) {
            fetchRutaExtras(focusGame.gameId, focusGame.isHome)
        } else {
            RutaGameExtras(connected = rutaConnected)
        }
        val naverWinProb = relayData?.let { buildWinProbFromRelay(it, lotteInfo?.isHome == true) }.orEmpty()
        val winProbSeries = rutaExtras.winProbSeries.ifEmpty { naverWinProb }

        val recentLotte = games
            .filter { it.involvesLotte() && it.gameDate <= todayStr && it.status() == GameStatus.ENDED }
            .sortedByDescending { it.gameDateTime }
            .take(5)
            .map { it.toLotteBase() }
        val lastLotte = recentLotte.firstOrNull()

        val prev = store.loadSnapshot()
        val now = System.currentTimeMillis()
        val keepHighlight = (prev?.highlightUntilMillis ?: 0L) > now

        var weather = prev?.weather
        val weatherStadium = lotteInfo?.stadium?.takeIf { it.isNotBlank() }
            ?: nextLotte?.stadium?.takeIf { it.isNotBlank() }
        if (!weatherStadium.isNullOrBlank()) {
            weather = runCatching { fetchStadiumWeather(weatherStadium) }.getOrNull() ?: weather
        }
        lotteInfo = lotteInfo?.let { g ->
            g.copy(preview = g.preview?.copy(weather = weather) ?: g.preview)
        }

        val snapshot = LiveSnapshot(
            updatedAtMillis = now,
            lotteGame = lotteInfo,
            nextLotteGame = nextLotte,
            lastLotteGame = lastLotte,
            recentLotteGames = recentLotte,
            otherGames = otherGames,
            yesterdayGames = yesterdayGames,
            highlightText = when {
                rutaExtras.highlightText.isNotBlank() -> rutaExtras.highlightText
                keepHighlight -> prev?.highlightText.orEmpty()
                else -> ""
            },
            highlightUntilMillis = when {
                rutaExtras.highlightText.isNotBlank() -> now + 45_000L
                keepHighlight -> prev?.highlightUntilMillis ?: 0L
                else -> 0L
            },
            weather = weather,
            rutaConnected = rutaExtras.connected || rutaConnected,
            winProbSeries = winProbSeries,
            hotColdZone = emptyList(),
            pitchLocations = lotteInfo?.pitchLocations.orEmpty(),
        )
        store.saveSnapshot(snapshot)
        return snapshot
    }

    private suspend fun tryConnectRuta(): Boolean = runCatching {
        val deviceId = UUID.nameUUIDFromBytes(
            (android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ) ?: "sajik-score").toByteArray(),
        ).toString()
        RutaApi.tryEnsureGuestToken(rutaApi, deviceId) && !RutaApi.bearerOrNull().isNullOrBlank()
    }.getOrDefault(false)

    /** 루타 우선 고급 데이터. 실패 시 빈 extras → 호출부가 네이버 fallback 사용 */
    private suspend fun fetchRutaExtras(gameId: String, isHome: Boolean): RutaGameExtras {
        val bearer = RutaApi.bearerOrNull() ?: return RutaGameExtras(connected = false)
        val winObj = runCatching { rutaApi.getWinRate(gameId = gameId, authorization = bearer) }.getOrNull()
        val gameObj = runCatching { rutaApi.getGame(gameId, bearer) }.getOrNull()
        val highlightObj = runCatching { rutaApi.getGameHighlight(gameId, bearer) }.getOrNull()
        val series = parseRutaWinRate(winObj, isHome).ifEmpty { parseRutaWinRate(gameObj, isHome) }
        val highlight = highlightObj?.let { extractRutaHighlight(it) }.orEmpty()
        val ok = winObj != null || gameObj != null || highlightObj != null
        return RutaGameExtras(
            connected = ok || bearer.isNotBlank(),
            winProbSeries = series,
            highlightText = highlight,
        )
    }

    private fun extractRutaHighlight(obj: JsonObject): String {
        val data = obj["data"] as? JsonObject ?: obj
        val keys = listOf("text", "message", "title", "highlight", "content")
        for (k in keys) {
            val el = data[k] ?: continue
            val p = el as? kotlinx.serialization.json.JsonPrimitive ?: continue
            p.content.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    /** 네이버 relay metricOption → 롯데 승리확률 시계열 */
    private fun buildWinProbFromRelay(relay: TextRelayData, isHome: Boolean): List<WinProbPoint> {
        fun rateOf(m: MetricOptionDto?): Double? {
            if (m == null) return null
            val raw = if (isHome) m.homeTeamWinRate else m.awayTeamWinRate
            val v = raw ?: return null
            return (if (v > 1.5) v / 100.0 else v).coerceIn(0.0, 1.0)
        }
        val fromPlates = relay.textRelays.mapIndexedNotNull { idx, tr ->
            val r = rateOf(tr.metricOption) ?: return@mapIndexedNotNull null
            WinProbPoint(seq = idx, label = "${tr.inn}회", homeProb = r)
        }
        if (fromPlates.isNotEmpty()) return fromPlates
        val last = rateOf(relay.lastValidMetricOption) ?: return emptyList()
        return listOf(WinProbPoint(seq = 0, label = "현재", homeProb = last))
    }

    /** 네이버 preview + 순위/맞대결로 GamePreview·주요장면·MVP 채움 */
    private suspend fun enrichGameSummary(base: LotteGameInfo, seasonGames: List<GameDto>): LotteGameInfo {
        val previewDto = runCatching { api.getPreview(base.gameId).result?.previewData }.getOrNull()
        val standings = runCatching { fetchStandings() }.getOrDefault(emptyList())
        val preview = buildGamePreview(base, previewDto, standings, seasonGames)
        val withSeason = fillMissingSeasonStats(base)
        val keyPlays = extractKeyPlays(withSeason.recentTexts)
        val pitchCount = (withSeason.lottePitchers + withSeason.opponentPitchers)
            .firstOrNull { it.playerCode == withSeason.currentPitcherCode && withSeason.currentPitcherCode.isNotBlank() }
            ?.pitchCount
            ?: (withSeason.lottePitchers + withSeason.opponentPitchers)
                .firstOrNull { it.name == withSeason.currentPitcherName && withSeason.currentPitcherName.isNotBlank() }
                ?.pitchCount
            ?: 0
        val (mvpName, mvpLine) = provisionalMvp(withSeason)
        return withSeason.copy(
            preview = preview,
            keyPlays = keyPlays,
            currentPitcherPitchCount = pitchCount,
            provisionalMvpName = mvpName,
            provisionalMvpLine = mvpLine,
        )
    }

    /** 시즌 타율/ERA가 비어 있으면 Keubo 리더보드로 보강 */
    private suspend fun fillMissingSeasonStats(game: LotteGameInfo): LotteGameInfo {
        val needBatter = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
            .any { it.seasonAvg == null || it.seasonAvg <= 0.0 }
        val needPitcher = (game.lottePitchers + game.opponentPitchers).any { it.seasonEra.isBlank() }
        if (!needBatter && !needPitcher) return game

        val batters = if (needBatter) runCatching { fetchLeaders(false) }.getOrDefault(emptyList()) else emptyList()
        val pitchers = if (needPitcher) runCatching { fetchLeaders(true) }.getOrDefault(emptyList()) else emptyList()

        fun fillSlot(s: LineupSlot): LineupSlot {
            if (s.seasonAvg != null && s.seasonAvg > 0.0) return s
            val hit = batters.firstOrNull {
                (s.playerCode.isNotBlank() && it.playerCode == s.playerCode) ||
                    (s.name.isNotBlank() && it.name == s.name)
            } ?: return s
            val avg = hit.avg.toDoubleOrNull()?.takeIf { it > 0 } ?: return s
            return s.copy(seasonAvg = avg)
        }
        fun fillPitcher(p: PitcherLine): PitcherLine {
            if (p.seasonEra.isNotBlank()) return p
            val hit = pitchers.firstOrNull {
                (p.playerCode.isNotBlank() && it.playerCode == p.playerCode) ||
                    (p.name.isNotBlank() && it.name == p.name)
            } ?: return p
            return p.copy(seasonEra = hit.era.ifBlank { p.seasonEra })
        }
        return game.copy(
            lotteLineup = game.lotteLineup.map(::fillSlot),
            opponentLineup = game.opponentLineup.map(::fillSlot),
            lotteBenchBatters = game.lotteBenchBatters.map(::fillSlot),
            opponentBenchBatters = game.opponentBenchBatters.map(::fillSlot),
            lottePitchers = game.lottePitchers.map(::fillPitcher),
            opponentPitchers = game.opponentPitchers.map(::fillPitcher),
        )
    }

    private fun buildGamePreview(
        game: LotteGameInfo,
        dto: PreviewData?,
        standings: List<TeamStanding>,
        seasonGames: List<GameDto>,
    ): GamePreview {
        val homeStarter = dto?.homeStarter
        val awayStarter = dto?.awayStarter
        val lotteStarterBlock = if (game.isHome) homeStarter else awayStarter
        val oppStarterBlock = if (game.isHome) awayStarter else homeStarter
        val lotteTop = if (game.isHome) dto?.homeTopPlayer else dto?.awayTopPlayer
        val oppTop = if (game.isHome) dto?.awayTopPlayer else dto?.homeTopPlayer

        val lotteSt = standings.firstOrNull { it.teamId == LOTTE_TEAM_CODE }
        val oppSt = standings.firstOrNull { it.teamId == game.opponentCode }

        val matchups = seasonGames
            .filter {
                it.involvesLotte() &&
                    (it.homeTeamCode == game.opponentCode || it.awayTeamCode == game.opponentCode) &&
                    it.status() == GameStatus.ENDED
            }
            .sortedByDescending { it.gameDateTime }
        var w = 0
        var d = 0
        var l = 0
        matchups.forEach { m ->
            val lotteHome = m.homeTeamCode == LOTTE_TEAM_CODE
            val ls = if (lotteHome) m.homeTeamScore else m.awayTeamScore
            val os = if (lotteHome) m.awayTeamScore else m.homeTeamScore
            when {
                ls > os -> w++
                ls < os -> l++
                else -> d++
            }
        }

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
                teamCode = LOTTE_TEAM_CODE,
                teamName = "롯데",
                rank = lotteSt?.ranking ?: 0,
                win = lotteSt?.win ?: 0,
                draw = lotteSt?.draw ?: 0,
                lose = lotteSt?.lose ?: 0,
                wra = lotteSt?.wra ?: 0.0,
            ),
            opponentStanding = PreviewTeamLine(
                teamCode = game.opponentCode,
                teamName = game.opponentName,
                rank = oppSt?.ranking ?: 0,
                win = oppSt?.win ?: 0,
                draw = oppSt?.draw ?: 0,
                lose = oppSt?.lose ?: 0,
                wra = oppSt?.wra ?: 0.0,
            ),
            seasonMatchup = MatchupRecord(
                wins = w,
                draws = d,
                losses = l,
                label = if (matchups.isEmpty()) "시즌 맞대결 없음" else "시즌 상대전 ${w}승 ${d}무 ${l}패",
            ),
            recentMatchups = matchups.take(5).map { it.toMiniGame() },
        )
    }

    private fun toPreviewPitcher(block: PreviewPlayerBlock?, fallbackName: String): PreviewPitcher {
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

    private fun toPreviewBatter(block: PreviewPlayerBlock?): PreviewBatter {
        val info = block?.playerInfo
        val stats = block?.currentSeasonStats
        return PreviewBatter(
            name = info?.name.orEmpty(),
            playerCode = info?.pCode ?: block?.playerCode.orEmpty(),
            avg = stats?.hra.orEmpty(),
            hits = stats?.hit ?: 0,
            hr = stats?.hr ?: 0,
            rbi = stats?.rbi ?: 0,
            games = stats?.gameCount ?: 0,
            ops = stats?.obp?.let { String.format("%.3f", it) }.orEmpty(),
        )
    }

    private fun extractKeyPlays(texts: List<RelayText>): List<KeyPlay> {
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

    private fun provisionalMvp(game: LotteGameInfo): Pair<String, String> {
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

    suspend fun saveWeather(weather: StadiumWeather?) {
        val prev = store.loadSnapshot() ?: return
        store.saveSnapshot(prev.copy(weather = weather))
    }

    suspend fun setHighlight(text: String, durationMs: Long = 45_000L) {
        val prev = store.loadSnapshot() ?: LiveSnapshot()
        store.saveSnapshot(
            prev.copy(
                highlightText = text,
                highlightUntilMillis = System.currentTimeMillis() + durationMs,
            ),
        )
    }

    suspend fun fetchGamesForDate(date: LocalDate): List<MiniGame> {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val day = date.format(fmt)
        return api.getGames(fromDate = day, toDate = day)
            .result?.games.orEmpty()
            .filter { it.categoryId == "kbo" && it.gameDate == day }
            .map { it.toMiniGame() }
    }

    suspend fun fetchGamesForMonth(month: YearMonth): List<MiniGame> {
        val from = month.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val to = month.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return api.getGames(fromDate = from, toDate = to)
            .result?.games.orEmpty()
            .filter { it.categoryId == "kbo" }
            .map { it.toMiniGame() }
    }

    suspend fun fetchStandings(): List<TeamStanding> {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        return api.getStandings(season.toString()).result?.seasonTeamStats.orEmpty()
            .map {
                TeamStanding(
                    teamId = it.teamId,
                    teamName = it.teamName,
                    ranking = it.ranking,
                    wra = it.wra,
                    gameCount = it.gameCount,
                    win = it.winGameCount,
                    draw = it.drawnGameCount,
                    lose = it.loseGameCount,
                    gameBehind = it.gameBehind,
                    streak = it.continuousGameResult.orEmpty(),
                    lastFive = it.lastFiveGames.orEmpty(),
                )
            }
            .sortedBy { it.ranking }
    }

    suspend fun fetchStadiumWeather(stadium: String): StadiumWeather {
        val coord = resolveStadiumCoord(stadium)
        val res = weatherApi.current(coord.lat, coord.lon)
        val cur = res.current
        val code = cur?.weather_code ?: 0
        return StadiumWeather(
            stadium = coord.name,
            temperatureC = cur?.temperature_2m ?: 0.0,
            weatherCode = code,
            precipProbability = cur?.precipitation_probability,
            summary = weatherSummaryKo(code),
            updatedAt = cur?.time.orEmpty(),
        )
    }

    /**
     * KBO 공식 선수등록현황(날짜별 등록/말소).
     * 출처: m.koreabaseball.com GetRoster
     */
    suspend fun fetchDayEntryChanges(date: LocalDate, resolveCodes: Boolean = true): DayEntryChanges {
        val season = date.year.toString()
        val gDt = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val res = kboApi.getRoster(
            KboRosterRequest(season_id = season, g_dt = gDt, t_id = LOTTE_TEAM_CODE),
        )
        val codeByName = if (resolveCodes) {
            runCatching {
                (fetchLeaders(false) + fetchLeaders(true))
                    .filter { it.isLotte }
                    .associate { it.name to it.playerCode }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        fun toPlayers(table: String) = KboRosterParser.parsePlayers(table).map {
            EntryPlayer(
                name = it.name,
                playerCode = codeByName[it.name].orEmpty(),
                backNumber = it.backNumber,
                position = it.position,
                hitType = it.batsThrows,
                isPitcher = it.position.contains("투수"),
            )
        }
        return DayEntryChanges(
            date = gDt,
            registered = toPlayers(res.tableKboY),
            removed = toPlayers(res.tableKboN),
        )
    }

    /** 오늘부터 최대 lookback일 전까지 공시가 있는 가장 최근 날짜 */
    suspend fun findLatestEntryDate(lookback: Int = 21): LocalDate {
        val today = LocalDate.now()
        for (i in 0..lookback) {
            val d = today.minusDays(i.toLong())
            val changes = runCatching { fetchDayEntryChanges(d) }.getOrNull()
            if (changes != null && changes.hasChanges) return d
        }
        return today
    }

    /** 한 달 중 등말소 공시가 있는 날짜 */
    suspend fun fetchEntryChangeDates(month: YearMonth): Set<LocalDate> {
        val hits = mutableSetOf<LocalDate>()
        for (day in 1..month.lengthOfMonth()) {
            val d = month.atDay(day)
            runCatching { fetchDayEntryChanges(d, resolveCodes = false) }
                .onSuccess { if (it.hasChanges) hits.add(d) }
        }
        return hits
    }

    suspend fun fetchRecentRosterMoves(days: Int = 7): List<RosterMove> {
        val from = LocalDate.now().minusDays((days - 1).toLong()).toString()
        val moves = keuboApi.getRosterMoves(KeuboApi.LOTTE_TEAM_ID).moves.map { it.toDomain() }
        return moves.filter { it.moveDate >= from }.sortedByDescending { it.moveDate }
    }

    suspend fun fetchLeaders(isPitcher: Boolean): List<LeaderPlayer> {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        val type = if (isPitcher) "pitcher" else "batter"
        return keuboApi.getStats(type, season).stats.map { it.toLeader(isPitcher) }
    }

    suspend fun fetchLotteTeamCard(): LotteTeamCard =
        keuboApi.getTeamCard(KeuboApi.LOTTE_SLUG).toDomain()

    suspend fun fetchPlayerDetail(
        playerCode: String,
        fallback: LineupSlot? = null,
        gameIdHint: String? = null,
    ): PlayerDetail {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val resolvedCode = playerCode.ifBlank {
            val name = fallback?.name.orEmpty()
            if (name.isBlank()) ""
            else runCatching {
                (fetchLeaders(false) + fetchLeaders(true))
                    .firstOrNull { it.name == name }?.playerCode.orEmpty()
            }.getOrDefault("")
        }
        val hintId = gameIdHint?.takeIf { it.isNotBlank() }
            ?: api.getGames(
                fromDate = today.minusDays(14).format(fmt),
                toDate = today.plusDays(3).format(fmt),
            ).result?.games.orEmpty()
                .filter { it.categoryId == "kbo" && it.involvesLotte() }
                .maxByOrNull { it.gameDateTime }
                ?.gameId

        var detail = basePlayerFromLineup(fallback, resolvedCode)

        if (!hintId.isNullOrBlank()) {
            val preview = runCatching { api.getPreview(hintId).result?.previewData }.getOrNull()
            val blocks = listOfNotNull(
                preview?.homeStarter,
                preview?.awayStarter,
                preview?.homeTopPlayer,
                preview?.awayTopPlayer,
            )
            val match = blocks.firstOrNull {
                (resolvedCode.isNotBlank() && (
                    it.playerCode == resolvedCode ||
                        it.playerInfo?.pCode == resolvedCode ||
                        it.currentSeasonStats?.playerCode == resolvedCode
                    )) ||
                    (fallback?.name?.isNotBlank() == true && it.playerInfo?.name == fallback.name)
            }
            if (match != null) {
                detail = mergePreviewPlayer(detail, match)
            }

            val relay = runCatching { api.getRelay(hintId).result?.textRelayData }.getOrNull()
            val entryPlayer = listOfNotNull(relay?.homeEntry, relay?.awayEntry)
                .flatMap { it.batter + it.pitcher }
                .firstOrNull {
                    (resolvedCode.isNotBlank() && it.pcode == resolvedCode) ||
                        (fallback?.name?.isNotBlank() == true && it.name == fallback.name)
                }
            if (entryPlayer != null) {
                detail = detail.copy(
                    name = detail.name.ifBlank { entryPlayer.name },
                    playerCode = detail.playerCode.ifBlank { entryPlayer.pcode },
                    hitType = detail.hitType.ifBlank {
                        entryPlayer.hittype ?: entryPlayer.pitchingStyle.orEmpty()
                    },
                    position = detail.position.ifBlank { entryPlayer.pos.orEmpty() },
                    isPitcher = detail.isPitcher || entryPlayer.pos == "1" ||
                        (entryPlayer.pitchingStyle?.isNotBlank() == true && entryPlayer.hittype.isNullOrBlank()),
                )
            }
        }

        return detail.copy(
            photoUrl = if (detail.playerCode.isNotBlank()) playerPhotoUrl(detail.playerCode) else "",
        ).let { withKeuboSeasonStats(it) }
    }

    /** 프리뷰에 없는 선수라도 Keubo 타이틀 스탯으로 시즌 성적 보강 */
    private suspend fun withKeuboSeasonStats(detail: PlayerDetail): PlayerDetail {
        val needsBatter = !detail.isPitcher &&
            (detail.seasonAvg.isBlank() || detail.seasonHits == 0 && detail.seasonHr == 0 && detail.seasonRbi == 0)
        val needsPitcher = detail.isPitcher && detail.pitcherEra.isBlank()
        if (!needsBatter && !needsPitcher && detail.seasonOps.isNotBlank()) return detail

        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        val code = detail.playerCode
        val name = detail.name

        fun match(s: KeuboStatDto): Boolean {
            if (code.isNotBlank() && (s.kboId == code || s.playerId == code)) return true
            return name.isNotBlank() && s.name == name
        }

        if (needsPitcher || detail.isPitcher) {
            val pitcher = runCatching {
                keuboApi.getStats("pitcher", season).stats.firstOrNull(::match)
            }.getOrNull()
            if (pitcher != null) {
                val seeded = pitcher.toLeader(true)
                return detail.copy(
                    name = detail.name.ifBlank { seeded.name },
                    seasonGames = if (detail.seasonGames > 0) detail.seasonGames else seeded.games,
                    pitcherEra = detail.pitcherEra.ifBlank { seeded.era },
                    pitcherWins = if (detail.pitcherWins > 0) detail.pitcherWins else seeded.wins,
                    pitcherLosses = if (detail.pitcherLosses > 0) detail.pitcherLosses else seeded.losses,
                    pitcherSo = if (detail.pitcherSo > 0) detail.pitcherSo else seeded.so,
                    pitcherInn = detail.pitcherInn.ifBlank { seeded.ip },
                    pitcherSaves = if (detail.pitcherSaves > 0) detail.pitcherSaves else seeded.saves,
                    pitcherHolds = if (detail.pitcherHolds > 0) detail.pitcherHolds else seeded.holds,
                    pitcherWhip = detail.pitcherWhip.ifBlank { seeded.whip },
                    isPitcher = true,
                )
            }
        }

        val batter = runCatching {
            keuboApi.getStats("batter", season).stats.firstOrNull(::match)
        }.getOrNull() ?: return detail

        val seeded = batter.toLeader(false)
        return detail.copy(
            name = detail.name.ifBlank { seeded.name },
            seasonAvg = detail.seasonAvg.ifBlank { seeded.avg },
            seasonGames = if (detail.seasonGames > 0) detail.seasonGames else seeded.games,
            seasonHits = if (detail.seasonHits > 0) detail.seasonHits else seeded.hits,
            seasonHr = if (detail.seasonHr > 0) detail.seasonHr else seeded.hr,
            seasonRbi = if (detail.seasonRbi > 0) detail.seasonRbi else seeded.rbi,
            seasonObp = detail.seasonObp.ifBlank { seeded.obp },
            seasonOps = detail.seasonOps.ifBlank { seeded.ops },
            seasonSlg = detail.seasonSlg.ifBlank { seeded.slg },
            seasonSb = if (detail.seasonSb > 0) detail.seasonSb else seeded.sb,
        )
    }

    private fun basePlayerFromLineup(slot: LineupSlot?, code: String): PlayerDetail {
        val c = code.ifBlank { slot?.playerCode.orEmpty() }
        return PlayerDetail(
            playerCode = c,
            name = slot?.name.orEmpty(),
            backNumber = slot?.backNumber.orEmpty(),
            hitType = slot?.hitType.orEmpty(),
            position = slot?.position.orEmpty(),
            seasonAvg = slot?.seasonAvg?.let { String.format("%.3f", it) }.orEmpty(),
            todayLine = if (slot != null) "${slot.todayHits}/${slot.todayAtBats}" else "",
            photoUrl = if (c.isNotBlank()) playerPhotoUrl(c) else "",
        )
    }

    private fun mergePreviewPlayer(base: PlayerDetail, block: PreviewPlayerBlock): PlayerDetail {
        val info = block.playerInfo
        val stats = block.currentSeasonStats
        val isPitcher = stats?.era != null || stats?.inn != null
        return base.copy(
            playerCode = info?.pCode ?: block.playerCode ?: base.playerCode,
            name = info?.name?.takeIf { it.isNotBlank() } ?: base.name,
            backNumber = info?.backnum?.takeIf { it.isNotBlank() } ?: base.backNumber,
            hitType = info?.hitType?.takeIf { it.isNotBlank() } ?: base.hitType,
            birth = info?.birth.orEmpty(),
            heightCm = info?.height.orEmpty(),
            weightKg = info?.weight.orEmpty(),
            seasonAvg = stats?.hra?.takeIf { it.isNotBlank() } ?: base.seasonAvg,
            seasonGames = stats?.gameCount ?: base.seasonGames,
            seasonHits = stats?.hit ?: base.seasonHits,
            seasonAb = stats?.ab ?: base.seasonAb,
            seasonHr = stats?.hr ?: base.seasonHr,
            seasonRbi = stats?.rbi ?: base.seasonRbi,
            seasonObp = stats?.obp?.let { String.format("%.3f", it) }.orEmpty(),
            pitcherEra = stats?.era.orEmpty(),
            pitcherWins = stats?.w ?: 0,
            pitcherLosses = stats?.l ?: 0,
            pitcherSo = stats?.kk ?: 0,
            pitcherInn = stats?.inn.orEmpty(),
            isPitcher = isPitcher || base.isPitcher,
        )
    }

    private fun GameDto.involvesLotte() =
        homeTeamCode == LOTTE_TEAM_CODE || awayTeamCode == LOTTE_TEAM_CODE

    private fun GameDto.status(): GameStatus = when {
        cancel -> GameStatus.CANCELED
        statusCode == "RESULT" || statusNum == 4 -> GameStatus.ENDED
        statusCode == "BEFORE" || statusNum == 1 -> GameStatus.BEFORE
        else -> GameStatus.LIVE
    }

    private fun GameDto.startTimeText(): String = runCatching {
        LocalDateTime.parse(gameDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")

    private fun GameDto.toMiniGame() = MiniGame(
        gameId = gameId,
        homeName = homeTeamName,
        awayName = awayTeamName,
        homeScore = homeTeamScore,
        awayScore = awayTeamScore,
        status = status(),
        statusText = statusInfo?.takeIf { it.isNotBlank() }?.let { raw ->
            if (status() == GameStatus.CANCELED) {
                cancelDisplayLabel(normalizeCancelReason(raw))
            } else {
                raw
            }
        } ?: when (status()) {
            GameStatus.BEFORE -> startTimeText()
            GameStatus.CANCELED -> cancelDisplayLabel(normalizeCancelReason(statusInfo))
            GameStatus.ENDED -> "종료"
            GameStatus.LIVE -> "진행 중"
        },
        stadium = stadium.orEmpty(),
        startTime = startTimeText(),
        homeLogoUrl = homeTeamEmblemUrl ?: teamLogoUrl(homeTeamCode),
        awayLogoUrl = awayTeamEmblemUrl ?: teamLogoUrl(awayTeamCode),
        homeStarter = homeStarterName.orEmpty(),
        awayStarter = awayStarterName.orEmpty(),
        broadChannel = broadChannel.orEmpty(),
        winPitcherName = winPitcherName.orEmpty(),
        losePitcherName = losePitcherName.orEmpty(),
        gameDate = gameDate,
        homeTeamCode = homeTeamCode,
        awayTeamCode = awayTeamCode,
    )

    private fun GameDto.toLotteBase(): LotteGameInfo {
        val isHome = homeTeamCode == LOTTE_TEAM_CODE
        val oppCode = if (isHome) awayTeamCode else homeTeamCode
        return LotteGameInfo(
            gameId = gameId,
            gameDate = gameDate,
            startTime = startTimeText(),
            stadium = stadium.orEmpty(),
            isHome = isHome,
            opponentCode = oppCode,
            opponentName = if (isHome) awayTeamName else homeTeamName,
            opponentLogoUrl = (if (isHome) awayTeamEmblemUrl else homeTeamEmblemUrl)
                ?: teamLogoUrl(oppCode),
            lotteScore = if (isHome) homeTeamScore else awayTeamScore,
            opponentScore = if (isHome) awayTeamScore else homeTeamScore,
            status = status(),
            statusText = when (status()) {
                GameStatus.CANCELED -> cancelDisplayLabel(normalizeCancelReason(statusInfo))
                else -> statusInfo.orEmpty()
            },
            cancelReason = if (status() == GameStatus.CANCELED) {
                normalizeCancelReason(statusInfo)
            } else {
                ""
            },
            broadChannel = broadChannel.orEmpty(),
            lotteStartingPitcher = (if (isHome) homeStarterName else awayStarterName).orEmpty(),
            opponentStartingPitcher = (if (isHome) awayStarterName else homeStarterName).orEmpty(),
            currentPitcherName = (if (isHome) awayCurrentPitcherName else homeCurrentPitcherName).orEmpty(),
            winPitcherName = winPitcherName.orEmpty(),
            losePitcherName = losePitcherName.orEmpty(),
        )
    }

    /**
     * 네이버 relay는 기본 응답에 현재 이닝 문자중계만 포함된다.
     * `?inning=N`으로 1~현재 이닝을 병렬 조회해 textRelays를 합친다.
     * 이미 끝난 이닝은 메모리 캐시해 폴링 부하를 줄인다.
     */
    private suspend fun fetchFullRelay(gameId: String): TextRelayData? {
        val base = api.getRelay(gameId).result?.textRelayData ?: return null
        fun scoreKeys(map: Map<String, String>?) =
            map?.keys?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: 0
        val maxFromScore = maxOf(
            scoreKeys(base.inningScore?.home),
            scoreKeys(base.inningScore?.away),
        )
        val maxInn = maxOf(base.inn, maxFromScore, 1).coerceAtMost(18)
        val cache = relayInningCache.getOrPut(gameId) { ConcurrentHashMap() }

        coroutineScope {
            (1..maxInn).map { inn ->
                async {
                    val reuse = inn < base.inn && !cache[inn].isNullOrEmpty()
                    if (reuse) return@async
                    val chunk = runCatching {
                        api.getRelay(gameId, inning = inn).result?.textRelayData?.textRelays.orEmpty()
                    }.getOrDefault(emptyList())
                    if (chunk.isNotEmpty()) {
                        cache[inn] = chunk
                    } else if (inn == base.inn && base.textRelays.isNotEmpty()) {
                        // inning 파라미터 실패 시 기본 응답(현재 이닝)이라도 사용
                        cache[inn] = base.textRelays
                    }
                }
            }.forEach { it.await() }
        }

        // 현재 이닝은 항상 최신 base 응답으로 덮어씀 (캐시가 비어 있을 때)
        if (base.textRelays.isNotEmpty() && base.textRelays.all { it.inn == base.inn || it.inn == 0 }) {
            val currentOnly = base.textRelays.filter { it.inn == base.inn || it.inn == 0 }
            if (currentOnly.isNotEmpty()) cache[base.inn] = currentOnly
        }

        val merged = (1..maxInn).flatMap { cache[it].orEmpty() }
            .ifEmpty { base.textRelays }
        return base.copy(textRelays = merged)
    }

    private fun mergeRelay(base: LotteGameInfo, relay: TextRelayData): LotteGameInfo {
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
                .mapValues { (_, list) -> list.maxBy { it.seqno } }

        fun LineupDto.startersByOrder(): Map<Int, LineupBatterDto> =
            batter.filter { it.batOrder in 1..9 }
                .groupBy { it.batOrder }
                .mapValues { (_, list) -> list.minBy { it.seqno } }

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
                    val starterSeq = list.minOf { it.seqno }
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
                    val yBroken = yRaw == null || yRaw < 1.0 || yRaw > 5.0 ||
                        (yRaw in 0.70..0.72)
                    val y = when {
                        !yBroken && yRaw != null -> yRaw.toFloat()
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

        val state = relay.currentGameState
        val isTop = relay.homeOrAway != "1"
        val isLotteBatting = if (isHome) !isTop else isTop

        val battingLineupDto = if (isTop) {
            if (isHome) oppLineupDto else lotteLineupDto
        } else {
            if (isHome) lotteLineupDto else oppLineupDto
        }
        val battingOrder = battingLineupDto?.currentByOrder().orEmpty()
        val batterCode = state?.batter.orEmpty()
        val currentBatter = battingOrder.values.firstOrNull { it.pcode == batterCode }
        val nextBatter = currentBatter?.let { battingOrder[(it.batOrder % 9) + 1] }

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
                    RelayText(it.seqno, it.text, it.type, tr.inn, isTopInning = isTop)
                }
            }
            .filter { it.type != 99 && it.text.isNotBlank() }
            .sortedByDescending { it.seqno }

        val pitcherCode = state?.pitcher.orEmpty()
        return base.copy(
            lotteScore = state?.run { if (isHome) homeScore else awayScore }?.toIntOrNull() ?: base.lotteScore,
            opponentScore = state?.run { if (isHome) awayScore else homeScore }?.toIntOrNull() ?: base.opponentScore,
            inning = relay.inn,
            isTopInning = isTop,
            strike = state?.strike?.toIntOrNull() ?: 0,
            ball = state?.ball?.toIntOrNull() ?: 0,
            out = state?.out?.toIntOrNull() ?: 0,
            onBase1 = state?.base1 == "1",
            onBase2 = state?.base2 == "1",
            onBase3 = state?.base3 == "1",
            currentPitcherName = names[pitcherCode]
                ?: listOf(lottePitchers, opponentPitchers).flatten()
                    .firstOrNull { it.playerCode == pitcherCode }?.name
                ?: base.currentPitcherName,
            currentPitcherCode = pitcherCode,
            currentBatterName = names[batterCode] ?: currentBatter?.name.orEmpty(),
            currentBatterOrder = currentBatter?.batOrder ?: 0,
            nextBatterName = nextBatter?.name.orEmpty(),
            isLotteBatting = isLotteBatting,
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
            lotteInningScores = (if (isHome) inningScores?.home else inningScores?.away)?.ordered().orEmpty(),
            opponentInningScores = (if (isHome) inningScores?.away else inningScores?.home)?.ordered().orEmpty(),
            lotteHits = state?.run { if (isHome) homeHit else awayHit }?.toIntOrNull() ?: 0,
            opponentHits = state?.run { if (isHome) awayHit else homeHit }?.toIntOrNull() ?: 0,
            lotteErrors = state?.run { if (isHome) homeError else awayError }?.toIntOrNull() ?: 0,
            opponentErrors = state?.run { if (isHome) awayError else homeError }?.toIntOrNull() ?: 0,
            lotteBb = state?.run { if (isHome) homeBallFour else awayBallFour }?.toIntOrNull() ?: 0,
            opponentBb = state?.run { if (isHome) awayBallFour else homeBallFour }?.toIntOrNull() ?: 0,
            recentTexts = texts,
            pitchLocations = pitchLocations,
        )
    }

    /**
     * 투구 궤적(초기 위치·속도·가속도)으로 홈플레이트 높이(ft)를 추정한다.
     * 네이버 crossPlateY가 깨진 경기에서 존 차트용.
     */
    private fun estimatePlateHeightFt(p: PtsOptionDto): Float? {
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

    companion object {
        @Volatile
        private var instance: GiantsRepository? = null

        fun get(context: Context): GiantsRepository =
            instance ?: synchronized(this) {
                instance ?: GiantsRepository(context.applicationContext).also { instance = it }
            }
    }
}
