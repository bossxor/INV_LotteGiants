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

class GiantsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val api: NaverSportsApi = NaverSportsApi.create()
    private val weatherApi: WeatherApi = WeatherApi.create()
    private val kboApi: KboMobileApi = KboMobileApi.create()
    private val kboOfficialApi: KboOfficialApi = KboOfficialApi.create()
    private val keuboApi: KeuboApi = KeuboApi.create()
    private val rutaApi: RutaApi = RutaApi.create()
    val store = SnapshotStore(appContext)

    private val refreshMutex = Mutex()
    @Volatile private var memorySnapshot: LiveSnapshot? = null
    @Volatile private var memorySnapshotAt = 0L
    @Volatile private var seasonWindow: List<KboOfficialGame> = emptyList()
    @Volatile private var seasonWindowAt = 0L
    @Volatile private var snapshotFailCount = 0
    @Volatile private var snapshotCooldownUntil = 0L

    /** 종료된 이닝 문자중계 캐시 (gameId → inning → relays). 현재 이닝은 매번 재조회. */
    private val relayInningCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<TextRelayDto>>>()

    @Volatile private var lastRutaAt = 0L
    @Volatile private var lastRutaGameId = ""
    @Volatile private var lastRutaExtras = RutaGameExtras(connected = false)

    /** 날짜별 KBO 일정 캐시 (yyyy-MM-dd → fetchedAt, games) */
    private val kboDateCache = ConcurrentHashMap<String, Pair<Long, List<KboOfficialGame>>>()

    private var standingsCache: Pair<Long, List<TeamStanding>>? = null

    /** 일정·순위 캐시는 두고, 팀 전환 때 스냅샷·중계·루타만 비운다. */
    fun clearTeamCaches() {
        memorySnapshot = null
        memorySnapshotAt = 0L
        relayInningCache.clear()
        lastRutaAt = 0L
        lastRutaGameId = ""
        lastRutaExtras = RutaGameExtras(connected = false)
        snapshotFailCount = 0
        snapshotCooldownUntil = 0L
    }

    /**
     * KBO 공식 일정을 1차 소스로 오늘·어제·최근 21일·향후 14일을 읽고,
     * 네이버 문자중계로 라인업·투구 위치 등 KBO에 없는 항목만 보완한다.
     *
     * [force]가 아니면 방금 받은 스냅샷(4초 이내)을 재사용한다.
     * 앱·서비스·위젯이 동시에 호출해도 네트워크는 한 번만 탄다.
     * 연속 실패 시 잠시 쉬고, 마지막 성공 스냅샷을 돌려 알림·위젯이 멈추지 않게 한다.
     */
    suspend fun refreshSnapshot(force: Boolean = false): LiveSnapshot {
        if (!force) {
            freshMemorySnapshot()?.takeUnless { snapshotStaleForKboDay(it.updatedAtMillis) }?.let { return it }
        }
        val stale = lastKnownSnapshot()
        if (stale != null && !force && System.currentTimeMillis() < snapshotCooldownUntil) {
            return stale
        }
        return refreshMutex.withLock {
            if (!force) {
                freshMemorySnapshot()?.takeUnless { snapshotStaleForKboDay(it.updatedAtMillis) }?.let { return@withLock it }
            }
            val lockedStale = lastKnownSnapshot()
            if (lockedStale != null && !force && System.currentTimeMillis() < snapshotCooldownUntil) {
                return@withLock lockedStale
            }
            try {
                fetchFreshSnapshot().also {
                    memorySnapshot = it
                    memorySnapshotAt = System.currentTimeMillis()
                    snapshotFailCount = 0
                    snapshotCooldownUntil = 0L
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshSnapshot failed", e)
                snapshotFailCount += 1
                snapshotCooldownUntil = System.currentTimeMillis() + snapshotBackoffMs(snapshotFailCount)
                // 빈 스냅샷을 stale로 붙잡으면 오늘 경기 폴백을 영구히 막는다.
                val todayOnly = runCatching { fetchTodayOnlySnapshot() }.getOrNull()
                val fallback = when {
                    todayOnly?.lotteGame != null || todayOnly?.nextLotteGame != null -> todayOnly
                    lockedStale?.lotteGame != null || lockedStale?.nextLotteGame != null -> lockedStale
                    todayOnly != null -> todayOnly
                    else -> emptyFocusSnapshot()
                }
                fallback.also {
                    memorySnapshot = it
                    memorySnapshotAt = System.currentTimeMillis()
                    runCatching { store.saveSnapshot(it) }
                }
            }
        }
    }

    private suspend fun lastKnownSnapshot(): LiveSnapshot? =
        memorySnapshot ?: runCatching { store.loadSnapshot() }.getOrNull()

    private fun snapshotBackoffMs(fails: Int): Long = when {
        fails <= 1 -> 15_000L
        fails == 2 -> 30_000L
        fails == 3 -> 60_000L
        else -> 120_000L
    }

    private suspend fun kboSeasonWindow(today: LocalDate): List<KboOfficialGame> {
        val now = System.currentTimeMillis()
        if (seasonWindow.isNotEmpty() && now - seasonWindowAt in 0 until KBO_RANGE_TTL_MS) {
            return seasonWindow
        }
        val games = fetchKboGamesCached(today.minusDays(21), today.plusDays(14))
        if (games.isNotEmpty()) {
            seasonWindow = games
            seasonWindowAt = now
            return games
        }
        return seasonWindow
    }

    private suspend fun freshMemorySnapshot(): LiveSnapshot? {
        val now = System.currentTimeMillis()
        val mem = memorySnapshot
        if (mem != null) {
            val age = now - memorySnapshotAt
            if (age in 0 until SNAPSHOT_FRESH_MS) return mem
        } else {
            val disk = store.loadSnapshot()
            if (disk != null) {
                memorySnapshot = disk
                memorySnapshotAt = disk.updatedAtMillis
                val age = now - disk.updatedAtMillis
                if (age in 0 until SNAPSHOT_FRESH_MS) return disk
            }
        }
        return null
    }

    /** 전체 스냅샷이 깨져도 오늘 내 팀 경기는 보여 준다. */
    private suspend fun fetchTodayOnlySnapshot(): LiveSnapshot {
        val focus = runCatching { store.myTeamCode() }.getOrDefault(LOTTE_TEAM_CODE)
        val today = kboToday()
        val games = fetchKboGames(today)
        val kbo = pickKboLotte(games, null, focus)
        val lotte = kbo?.toLotteBase(focus)?.takeIf { it.belongsToKboToday() }
        return LiveSnapshot(
            updatedAtMillis = System.currentTimeMillis(),
            lotteGame = lotte,
            otherGames = kboToMiniGames(today, games.filter { !it.involvesTeam(focus) }),
            todayLotteGames = kboToMiniGames(today, games.filter { it.involvesTeam(focus) }),
            myTeamCode = focus,
        )
    }

    private suspend fun emptyFocusSnapshot(): LiveSnapshot = LiveSnapshot(
        updatedAtMillis = System.currentTimeMillis(),
        myTeamCode = runCatching { store.myTeamCode() }.getOrDefault(LOTTE_TEAM_CODE),
    )

    private suspend fun safeEnrich(
        base: LotteGameInfo,
        seasonGames: List<KboOfficialGame>,
        kbo: KboOfficialGame? = null,
    ): LotteGameInfo = runCatching { enrichGameSummary(base, seasonGames, kbo) }
        .onFailure { Log.e(TAG, "enrichGameSummary ${base.gameId}", it) }
        .getOrDefault(base)

    private suspend fun fetchFreshSnapshot(): LiveSnapshot {
        val focus = runCatching { store.myTeamCode() }.getOrDefault(LOTTE_TEAM_CODE)
        val today = kboToday()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr = today.format(fmt)
        val prev = store.loadSnapshot()
        val rangeWasCached = seasonWindow.isNotEmpty() &&
            System.currentTimeMillis() - seasonWindowAt in 0 until KBO_RANGE_TTL_MS

        val (kboToday, kboYesterday, kboRange) = coroutineScope {
            val a = async { fetchKboGames(today) }
            val b = async { fetchKboGames(today.minusDays(1)) }
            val c = async { runCatching { kboSeasonWindow(today) }.getOrDefault(emptyList()) }
            Triple(a.await(), b.await(), c.await())
        }
        val reuseSideCards = rangeWasCached &&
            prev?.nextLotteGame != null &&
            prev.recentLotteGames.isNotEmpty()

        val otherGames = if (kboToday.isNotEmpty()) {
            kboToMiniGames(today, kboToday.filter { !it.involvesTeam(focus) })
        } else {
            val naverToday = runCatching {
                api.getGames(fromDate = todayStr, toDate = todayStr)
                    .result?.games.orEmpty().filter { it.categoryId == "kbo" }
            }.getOrDefault(emptyList())
            val reasons = cancelReasonsFor(naverToday, today)
            naverToday.filter { !it.involvesTeam(focus) }
                .map { it.toMiniGame(kboCancelLabel = reasons[it.matchKey()]) }
        }
        val yesterdayGames = if (kboYesterday.isNotEmpty()) {
            kboToMiniGames(today.minusDays(1), kboYesterday)
        } else {
            val yesterdayStr = today.minusDays(1).format(fmt)
            val naverYesterday = runCatching {
                api.getGames(fromDate = yesterdayStr, toDate = yesterdayStr)
                    .result?.games.orEmpty().filter { it.categoryId == "kbo" }
            }.getOrDefault(emptyList())
            val reasons = cancelReasonsFor(naverYesterday, today.minusDays(1))
            naverYesterday.map { it.toMiniGame(kboCancelLabel = reasons[it.matchKey()]) }
        }

        val rutaConnected = tryConnectRuta()
        val preferredLiveId = store.preferredLiveGameId()

        val kboLotte = pickKboLotte(kboToday, preferredLiveId, focus)
        val lotteTodayNaver = if (kboLotte == null) {
            runCatching {
                api.getGames(fromDate = todayStr, toDate = todayStr)
                    .result?.games.orEmpty().filter { it.categoryId == "kbo" && it.involvesTeam(focus) }
                    .let { pickNaverLotte(it, preferredLiveId) }
            }.getOrNull()
        } else {
            null
        }
        var lotteInfo = kboLotte?.toLotteBase(focus)
            ?: lotteTodayNaver?.toLotteBase(
                kboCancelLabel = cancelReasonsFor(
                    listOfNotNull(lotteTodayNaver),
                    today,
                )[lotteTodayNaver.matchKey()],
                focusTeamCode = focus,
            )
        if (lotteInfo != null && !lotteInfo.belongsToKboToday(todayStr)) {
            lotteInfo = null
        }
        val relayGameId = kboLotte?.naverGameId() ?: lotteTodayNaver?.gameId
        var relayData: TextRelayData? = null
        if (relayGameId != null && lotteInfo != null) {
            // KBO 스코어보드·박스스코어 우선 (LIVE/ENDED/BEFORE 모두)
            lotteInfo = enrichFromKboDetail(lotteInfo, kboLotte)
            val wantRelay = lotteInfo.status == GameStatus.LIVE ||
                lotteInfo.status == GameStatus.ENDED ||
                lotteInfo.status == GameStatus.BEFORE
            if (wantRelay) {
                val relayResult = runCatching {
                    fetchRelayForPoll(relayGameId, lotteInfo.status, lotteInfo.lineupAnnounced)
                }
                relayData = relayResult.getOrNull()
                if (relayData != null) {
                    lotteInfo = runCatching { mergeRelay(lotteInfo, relayData) }
                        .onFailure { Log.e(TAG, "mergeRelay ${lotteInfo.gameId}", it) }
                        .getOrDefault(lotteInfo)
                } else if (relayResult.isFailure &&
                    lotteInfo.lotteLineup.isEmpty() &&
                    lotteInfo.recentTexts.isEmpty()
                ) {
                    lotteInfo = lotteInfo.copy(detailError = "상세 기록을 불러오지 못했습니다.")
                }
            }
            val skipSummary = prev != null &&
                prev.lotteGame?.gameId == lotteInfo.gameId &&
                lotteInfo.status == GameStatus.LIVE &&
                prev.lotteGame?.preview != null &&
                System.currentTimeMillis() - prev.updatedAtMillis in 0 until SUMMARY_TTL_MS
            lotteInfo = if (skipSummary) {
                lotteInfo.copy(preview = prev?.lotteGame?.preview)
            } else {
                safeEnrich(lotteInfo, kboRange, kboLotte)
            }
        }

        val nextLotte = if (reuseSideCards) {
            prev?.nextLotteGame
        } else {
            val nextKbo = kboRange
                .filter {
                    it.involvesTeam(focus) &&
                        it.status() == GameStatus.BEFORE &&
                        it.isoDate() >= todayStr &&
                        it.naverGameId() != lotteInfo?.gameId
                }
                .minWithOrNull(compareBy({ it.isoDate() }, { it.startTime }))
            nextKbo?.let { kbo ->
                val prevNext = prev?.nextLotteGame
                if (prevNext != null && prevNext.gameId == kbo.naverGameId() &&
                    (prevNext.lotteLineup.size >= 9 || prevNext.lineupAnnounced)
                ) {
                    enrichFromKboDetail(prevNext, kbo)
                } else {
                    var info = enrichFromKboDetail(kbo.toLotteBase(focus), kbo)
                    runCatching {
                        fetchRelayForPoll(kbo.naverGameId(), info.status, info.lineupAnnounced)
                    }.getOrNull()?.let { relay ->
                        info = runCatching { mergeRelay(info, relay) }.getOrDefault(info)
                    }
                    safeEnrich(info, kboRange, kbo)
                }
            } ?: run {
                val nextDto = runCatching {
                    api.getGames(
                        fromDate = today.plusDays(1).format(fmt),
                        toDate = today.plusDays(14).format(fmt),
                    ).result?.games.orEmpty()
                        .filter { it.categoryId == "kbo" && it.involvesTeam(focus) && !it.cancel }
                        .minByOrNull { it.gameDateTime }
                }.getOrNull()
                nextDto?.let { dto ->
                    val kboNext = runCatching { LocalDate.parse(dto.gameDate) }.getOrNull()
                        ?.let { fetchKboGames(it) }
                        ?.firstOrNull { it.involvesTeam(focus) && it.naverGameId() == dto.gameId }
                    val base = kboNext?.toLotteBase(focus) ?: dto.toLotteBase(focusTeamCode = focus)
                    safeEnrich(base, kboRange, kboNext)
                }
            }
        }

        val focusGame = lotteInfo ?: nextLotte
        val rutaExtras = if (focusGame != null && rutaConnected) {
            cachedRutaExtras(focusGame.gameId, focusGame.isHome)
        } else {
            RutaGameExtras(connected = rutaConnected)
        }
        val naverWinProb = relayData?.let { buildWinProbFromRelay(it, lotteInfo?.isHome == true) }.orEmpty()
        val estimated = lotteInfo?.takeIf {
            it.status == GameStatus.LIVE || it.status == GameStatus.ENDED
        }?.let {
            listOf(WinProbPoint(seq = 0, label = "현재", homeProb = estimateLotteWinProb(it)))
        }.orEmpty()
        val freshWin = WinProb.sanitizeSeries(
            lotteInfo,
            WinProb.pickSeries(naverWinProb, rutaExtras.winProbSeries, estimated),
        )
        val prevWin = prev?.winProbSeries.orEmpty()
        val winProbSeries = when {
            freshWin.size >= 2 -> freshWin.takeLast(48)
            freshWin.isNotEmpty() && prevWin.isNotEmpty() &&
                prev?.lotteGame?.gameId == lotteInfo?.gameId -> {
                (prevWin + freshWin.mapIndexed { i, p -> p.copy(seq = prevWin.size + i) })
                    .distinctBy { "%.4f".format(it.homeProb) to it.label }
                    .takeLast(48)
            }
            freshWin.isNotEmpty() -> freshWin
            prev?.lotteGame?.gameId == lotteInfo?.gameId -> prevWin
            else -> emptyList()
        }

        val recentLotte = if (reuseSideCards) {
            prev?.recentLotteGames.orEmpty()
        } else {
            kboRange
                .filter { it.involvesTeam(focus) && it.status() == GameStatus.ENDED && it.isoDate() <= todayStr }
                .sortedByDescending { it.gameDate }
                .take(5)
                .map { kbo ->
                    val base = enrichFromKboDetail(kbo.toLotteBase(focus), kbo)
                    safeEnrich(base, kboRange, kbo)
                }
        }
        val lastLotte = recentLotte.firstOrNull()

        val todayLotteGames = if (kboToday.any { it.involvesTeam(focus) }) {
            kboToMiniGames(today, kboToday.filter { it.involvesTeam(focus) })
                .sortedWith(compareBy({ it.doubleHeaderNo }, { it.startTime }))
        } else {
            runCatching {
                api.getGames(fromDate = todayStr, toDate = todayStr)
                    .result?.games.orEmpty()
                    .filter { it.categoryId == "kbo" && it.involvesTeam(focus) }
                    .map { it.toMiniGame() }
                    .sortedWith(compareBy({ it.doubleHeaderNo }, { it.startTime }))
            }.getOrDefault(emptyList())
        }

        val now = System.currentTimeMillis()
        val keepHighlight = (prev?.highlightUntilMillis ?: 0L) > now

        var weather = prev?.weather
        val weatherStadium = lotteInfo?.stadium?.takeIf { it.isNotBlank() }
            ?: nextLotte?.stadium?.takeIf { it.isNotBlank() }
            ?: teamHomeStadiumName(focus)
        val weatherFresh = weather != null &&
            weather.stadium == weatherStadium &&
            now - (prev?.updatedAtMillis ?: 0L) in 0 until WEATHER_TTL_MS
        if (weatherStadium.isNotBlank() && !weatherFresh) {
            weather = runCatching { fetchStadiumWeather(weatherStadium, focus) }.getOrNull() ?: weather
        }
        lotteInfo = lotteInfo?.let { g ->
            val extra = g.recentTexts.joinToString(" ") { it.text }
            g.copy(preview = g.preview?.copy(weather = weather) ?: g.preview)
                .normalizedIfCanceled()
                .withSuspendFilled(extra)
        }

        val standingsNow = runCatching { fetchStandings() }.getOrDefault(emptyList())
        val lotteSt = standingsNow.firstOrNull { it.teamId.equals(focus, true) }
        val seasonG = seasonLength(standingsNow)
        val rem = lotteSt?.let { remainingGames(it, seasonG) } ?: 0
        val rank = lotteSt?.ranking ?: lotteInfo?.lotteRank ?: nextLotte?.lotteRank ?: 0
        val starter = when {
            lotteInfo?.status == GameStatus.BEFORE -> lotteInfo.lotteStartingPitcher
            lotteInfo?.status == GameStatus.LIVE -> lotteInfo.currentPitcherName
            else -> nextLotte?.lotteStartingPitcher.orEmpty()
        }
        val countdownGame = when {
            lotteInfo?.isCanceledGame() == true -> null
            lotteInfo?.status == GameStatus.BEFORE -> lotteInfo
            lotteInfo?.status == GameStatus.LIVE -> null
            else -> nextLotte?.takeUnless { it.isCanceledGame() }
        }
        val countdown = countdownGame?.let { gameCountdownLabel(it.gameDate, it.startTime, now) }.orEmpty()

        val snapshot = LiveSnapshot(
            updatedAtMillis = now,
            lotteGame = lotteInfo,
            nextLotteGame = nextLotte,
            lastLotteGame = lastLotte,
            recentLotteGames = recentLotte,
            otherGames = otherGames,
            yesterdayGames = yesterdayGames,
            todayLotteGames = todayLotteGames,
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
            mediaHighlightText = rutaExtras.highlightText.ifBlank { prev?.mediaHighlightText.orEmpty() },
            mediaHighlightUrl = rutaExtras.highlightUrl.ifBlank { prev?.mediaHighlightUrl.orEmpty() },
            weather = weather,
            rutaConnected = rutaExtras.connected || rutaConnected,
            winProbSeries = winProbSeries,
            hotColdZone = run {
                val fresh = hotColdCellsFor(lotteInfo)
                val prevHot = prev?.hotColdZone.orEmpty()
                when {
                    fresh.isNotEmpty() -> fresh
                    prev?.lotteGame?.gameId == lotteInfo?.gameId && prevHot.isNotEmpty() -> prevHot
                    else -> fresh
                }
            },
            pitchLocations = lotteInfo?.pitchLocations.orEmpty(),
            lotteSeasonRank = rank,
            lotteRemainingGames = rem,
            widgetRaceLine = widgetRaceLine(rank, rem, starter, countdown),
            myTeamCode = focus,
        )
        runCatching { store.saveSnapshot(snapshot) }
            .onFailure { Log.e(TAG, "saveSnapshot", it) }
        return snapshot
    }

    /**
     * 특정 경기 상세. 위젯·알림 스냅샷은 건드리지 않는다.
     * 롯데가 나오면 롯데 기준, 아니면 홈팀 기준으로 같은 화면 모델을 채운다.
     */
    suspend fun fetchGameDetail(gameId: String): LotteGameInfo? {
        if (gameId.isBlank()) return null
        val date = parseNaverGameIdDate(gameId) ?: kboToday()
        val dayGames = fetchKboGames(date)
        val kbo = dayGames.firstOrNull { it.naverGameId() == gameId || it.gameId == gameId }
        if (kbo != null) {
            val myTeam = store.myTeamCode()
            val focus = if (kbo.involvesTeam(myTeam)) {
                myTeam
            } else {
                kbo.homeId.trim().uppercase()
            }
            var info = kbo.toLotteBase(focus)
            info = enrichFromKboDetail(info, kbo)
            val wantRelay = info.status == GameStatus.LIVE ||
                info.status == GameStatus.ENDED ||
                info.status == GameStatus.BEFORE
            if (wantRelay) {
                val relayResult = runCatching {
                    fetchRelayForPoll(kbo.naverGameId(), info.status, info.lineupAnnounced)
                }
                val relay = relayResult.getOrNull()
                if (relay != null) {
                    info = mergeRelay(info, relay)
                } else if (
                    relayResult.isFailure &&
                    info.lotteLineup.isEmpty() &&
                    info.recentTexts.isEmpty()
                ) {
                    info = info.copy(detailError = "상세 기록을 불러오지 못했습니다.")
                }
            }
            val season = fetchKboGamesCached(date.minusDays(45), date.plusDays(1))
            return withStadiumWeather(enrichGameSummary(info, season, kbo)).fillSuspendFromRelay()
        }
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dto = runCatching {
            api.getGames(fromDate = dateStr, toDate = dateStr)
                .result?.games.orEmpty()
                .firstOrNull { it.gameId == gameId }
        }.getOrNull() ?: return null
        val myTeam = store.myTeamCode()
        val focus = if (dto.involvesTeam(myTeam)) {
            myTeam
        } else {
            dto.homeTeamCode.trim().uppercase().ifBlank { myTeam }
        }
        var info = dto.toLotteBase(focusTeamCode = focus)
        runCatching { fetchRelayForPoll(gameId, info.status, info.lineupAnnounced) }.getOrNull()?.let { relay ->
            info = mergeRelay(info, relay)
        }
        val season = fetchKboGamesCached(date.minusDays(45), date.plusDays(1))
        return withStadiumWeather(enrichGameSummary(info, season, null)).fillSuspendFromRelay()
    }

    private suspend fun withStadiumWeather(game: LotteGameInfo): LotteGameInfo {
        val stadium = game.stadium.ifBlank { game.preview?.stadium.orEmpty() }
        if (stadium.isBlank()) return game
        val w = runCatching { fetchStadiumWeather(stadium) }.getOrNull() ?: return game
        val preview = game.preview?.copy(weather = w) ?: GamePreview(
            gameDate = game.gameDate,
            startTime = game.startTime,
            stadium = stadium,
            weather = w,
        )
        return game.copy(preview = preview)
    }

    private suspend fun tryConnectRuta(): Boolean {
        if (!RutaApi.bearerOrNull().isNullOrBlank()) return true
        return runCatching {
            val deviceId = UUID.nameUUIDFromBytes(
                (android.provider.Settings.Secure.getString(
                    appContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                ) ?: "sajik-score").toByteArray(),
            ).toString()
            RutaApi.tryEnsureGuestToken(rutaApi, deviceId) && !RutaApi.bearerOrNull().isNullOrBlank()
        }.getOrDefault(false)
    }

    private suspend fun cachedRutaExtras(gameId: String, isHome: Boolean): RutaGameExtras {
        val now = System.currentTimeMillis()
        if (gameId == lastRutaGameId && now - lastRutaAt < RUTA_TTL_MS) {
            return lastRutaExtras
        }
        val extras = fetchRutaExtras(gameId, isHome)
        lastRutaGameId = gameId
        lastRutaAt = now
        lastRutaExtras = extras
        return extras
    }

    /** 루타 우선 고급 데이터. 실패 시 빈 extras → 호출부가 네이버 fallback 사용 */
    private suspend fun fetchRutaExtras(gameId: String, isHome: Boolean): RutaGameExtras {
        val bearer = RutaApi.bearerOrNull() ?: return RutaGameExtras(connected = false)
        val winObj = runCatching { rutaApi.getWinRate(gameId = gameId, authorization = bearer) }.getOrNull()
        val gameObj = runCatching { rutaApi.getGame(gameId, bearer) }.getOrNull()
        val highlightObj = runCatching { rutaApi.getGameHighlight(gameId, bearer) }.getOrNull()
        val series = parseRutaWinRate(winObj, isHome).ifEmpty { parseRutaWinRate(gameObj, isHome) }
        val highlight = highlightObj?.let { extractRutaHighlight(it) }
        val ok = winObj != null || gameObj != null || highlightObj != null
        return RutaGameExtras(
            connected = ok || bearer.isNotBlank(),
            winProbSeries = series,
            highlightText = highlight?.text.orEmpty(),
            highlightUrl = highlight?.url.orEmpty(),
        )
    }


    /** KBO 스코어보드·박스스코어로 이닝·RHE·관중·엠블럼·주요장면 보강 */
    private suspend fun enrichFromKboDetail(base: LotteGameInfo, kbo: KboOfficialGame?): LotteGameInfo {
        if (kbo == null || kbo.gameId.isBlank()) return base
        val seasonId = kbo.seasonId.takeIf { it > 0 } ?: LocalDate.now().year
        val sb = runCatching {
            kboOfficialApi.getScoreBoardScroll(
                seasonId = seasonId,
                gameId = kbo.gameId,
                srId = kbo.srId,
            )
        }.getOrNull() ?: return base

        val board = runCatching {
            KboTableParser.parseInningBoard(sb.table2, sb.table3, sb.maxInning)
        }.getOrNull() ?: return base
        val isHome = base.isHome

        var result = base.copy(
            lotteInningScores = if (isHome) board.homeScores else board.awayScores,
            opponentInningScores = if (isHome) board.awayScores else board.homeScores,
            lotteHits = if (isHome) board.homeHits else board.awayHits,
            opponentHits = if (isHome) board.awayHits else board.homeHits,
            lotteErrors = if (isHome) board.homeErrors else board.awayErrors,
            opponentErrors = if (isHome) board.awayErrors else board.homeErrors,
            lotteBb = if (isHome) board.homeWalks else board.awayWalks,
            opponentBb = if (isHome) board.awayWalks else board.homeWalks,
            crowdCount = sb.crowd.ifBlank { base.crowdCount },
            gameDuration = sb.duration.ifBlank { base.gameDuration },
        )

        if (base.status == GameStatus.ENDED || base.status == GameStatus.LIVE) {
            val box = runCatching {
                kboOfficialApi.getBoxScoreScroll(
                    seasonId = seasonId,
                    gameId = kbo.gameId,
                    srId = kbo.srId,
                )
            }.getOrNull()
            if (box != null) {
                val keyPlays = KboTableParser.parseKeyPlays(box.tableEtc)
                if (keyPlays.isNotEmpty()) {
                    result = result.copy(keyPlays = keyPlays)
                }
            }
        }
        return result
    }

    /** 네이버 preview + KBO 순위/맞대결로 GamePreview·주요장면·MVP 채움 */
    private suspend fun enrichGameSummary(
        base: LotteGameInfo,
        seasonGames: List<KboOfficialGame>,
        kbo: KboOfficialGame? = null,
    ): LotteGameInfo {
        val previewDto = if (base.status != GameStatus.CANCELED) {
            runCatching { api.getPreview(base.gameId).result?.previewData }.getOrNull()
        } else {
            null
        }
        val standings = runCatching { fetchStandings() }.getOrDefault(emptyList())
        val preview = buildGamePreview(base, previewDto, standings, seasonGames, kbo)
        val filled = fillLineupFromPreview(base, previewDto)
        val withSeason = fillMissingSeasonStats(filled)
        val keyPlays = if (withSeason.keyPlays.isNotEmpty()) {
            withSeason.keyPlays
        } else {
            extractKeyPlays(withSeason.recentTexts)
        }
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

    suspend fun fetchGamesForDate(date: LocalDate): List<MiniGame> {
        fetchKboGames(date).takeIf { it.isNotEmpty() }?.let { kbo ->
            return kboToMiniGames(date, kbo)
        }
        val day = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val naver = api.getGames(fromDate = day, toDate = day)
            .result?.games.orEmpty()
            .filter { it.categoryId == "kbo" && it.gameDate == day }
        val reasons = cancelReasonsFor(naver, date)
        return naver.map { it.toMiniGame(kboCancelLabel = reasons[it.matchKey()]) }
    }

    suspend fun fetchGamesForMonth(month: YearMonth): List<MiniGame> {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val kbo = coroutineScope {
            days.map { d -> async { fetchKboGames(d) } }.flatMap { it.await() }
        }
        if (kbo.isNotEmpty()) {
            return coroutineScope {
                kbo.groupBy { it.isoDate() }.map { (dayStr, dayGames) ->
                    async {
                        val date = runCatching { LocalDate.parse(dayStr) }.getOrDefault(month.atDay(1))
                        kboToMiniGames(date, dayGames)
                    }
                }.flatMap { it.await() }
            }
        }

        val from = month.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val to = month.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val naver = api.getGames(fromDate = from, toDate = to)
            .result?.games.orEmpty()
            .filter { it.categoryId == "kbo" }
        return coroutineScope {
            naver.groupBy { it.gameDate }.flatMap { (dayStr, games) ->
                val date = runCatching { LocalDate.parse(dayStr) }.getOrNull() ?: return@flatMap emptyList()
                val reasons = cancelReasonsFor(games, date)
                games.map { it.toMiniGame(kboCancelLabel = reasons[it.matchKey()]) }
            }
        }
    }

    suspend fun fetchGamesForSeason(year: Int): List<MiniGame> {
        val from = LocalDate.of(year, 3, 1)
        val to = LocalDate.of(year, 11, 15)
        val out = mutableListOf<MiniGame>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val end = cursor.plusDays(9).let { if (it.isAfter(to)) to else it }
            val kbo = fetchKboGamesCached(cursor, end)
            if (kbo.isNotEmpty()) {
                kbo.groupBy { it.isoDate() }.forEach { (dayStr, dayGames) ->
                    val date = runCatching { LocalDate.parse(dayStr) }.getOrDefault(cursor)
                    out += kboToMiniGames(date, dayGames)
                }
            }
            cursor = end.plusDays(1)
        }
        if (out.isEmpty()) {
            var ym = YearMonth.from(from)
            val endYm = YearMonth.from(to)
            while (!ym.isAfter(endYm)) {
                out += fetchGamesForMonth(ym)
                ym = ym.plusMonths(1)
            }
        }
        return out
    }

    private suspend fun kboToMiniGames(date: LocalDate, games: List<KboOfficialGame>): List<MiniGame> =
        games.map { g ->
            if (g.status() != GameStatus.CANCELED) return@map g.toMiniGame()
            val reason = g.cancelReasonLabel().orEmpty()
            g.toMiniGame().copy(
                status = GameStatus.CANCELED,
                cancelReason = reason,
                statusText = cancelDisplayLabel(reason.ifBlank { null }),
            )
        }

    suspend fun fetchStandings(): List<TeamStanding> {
        val now = System.currentTimeMillis()
        standingsCache?.let { (t, list) ->
            if (now - t < STANDINGS_TTL_MS) return list
        }
        val kbo = runCatching {
            KboTableParser.parseStandings(kboOfficialApi.getTeamRank())
        }.getOrNull()
        if (!kbo.isNullOrEmpty()) {
            standingsCache = now to kbo
            return kbo
        }
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

    suspend fun fetchStadiumWeather(
        stadium: String,
        fallbackTeamCode: String = LOTTE_TEAM_CODE,
    ): StadiumWeather {
        val coord = resolveStadiumCoord(stadium, teamHomeStadiumName(fallbackTeamCode))
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
    suspend fun fetchDayEntryChanges(
        date: LocalDate,
        resolveCodes: Boolean = true,
        teamCode: String = LOTTE_TEAM_CODE,
    ): DayEntryChanges {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        val season = date.year.toString()
        val gDt = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val res = kboApi.getRoster(
            KboRosterRequest(season_id = season, g_dt = gDt, t_id = code),
        )
        val codeByName = if (resolveCodes) {
            runCatching {
                val batters = fetchLeaders(false).filter { it.matchesTeam(code) }
                val pitchers = fetchLeaders(true).filter { it.matchesTeam(code) }
                val moves = fetchAllRosterMoves(code)
                    .filter { it.playerCode.isNotBlank() && it.playerName.isNotBlank() }
                    .associate { it.playerName to it.playerCode }
                Triple(
                    batters.associate { it.name to it.playerCode },
                    pitchers.associate { it.name to it.playerCode },
                    moves,
                )
            }.getOrNull()
        } else {
            null
        }
        fun codeFor(name: String, isPitcher: Boolean): String {
            val maps = codeByName ?: return ""
            val (batterMap, pitcherMap, moveMap) = maps
            val primary = if (isPitcher) pitcherMap[name] else batterMap[name]
            return primary?.takeIf { it.isNotBlank() }
                ?: moveMap[name].orEmpty()
                    .ifBlank { (if (isPitcher) batterMap[name] else pitcherMap[name]).orEmpty() }
        }
        fun toPlayers(table: String) = KboRosterParser.parsePlayers(table).map {
            val pitcher = it.position.contains("투수")
            EntryPlayer(
                name = it.name,
                playerCode = codeFor(it.name, pitcher),
                backNumber = it.backNumber,
                position = it.position,
                hitType = it.batsThrows,
                isPitcher = pitcher,
            )
        }
        val kboReg = toPlayers(res.tableKboY)
        val kboRem = toPlayers(res.tableKboN)
        val keuboDay = if (resolveCodes) {
            runCatching { fetchAllRosterMoves(code) }.getOrDefault(emptyList())
                .filter { it.moveDate == gDt }
        } else {
            emptyList()
        }
        fun merge(kbo: List<EntryPlayer>, extras: List<RosterMove>): List<EntryPlayer> {
            val names = kbo.map { it.name }.toSet()
            val added = extras.filter { it.playerName.isNotBlank() && it.playerName !in names }.map { m ->
                val pitcher = m.playerName.let { n ->
                    codeByName?.second?.containsKey(n) == true
                }
                EntryPlayer(
                    name = m.playerName,
                    playerCode = m.playerCode.ifBlank { codeFor(m.playerName, pitcher) },
                    isPitcher = pitcher,
                )
            }
            return kbo + added
        }
        return DayEntryChanges(
            date = gDt,
            registered = merge(kboReg, keuboDay.filter { it.isRegister }),
            removed = merge(kboRem, keuboDay.filter { !it.isRegister }),
        )
    }

    /**
     * 팀별 등번호 일람(구단 소속 전체: 1군·퓨처스). 캐시와 같으면 네트워크 결과만 버리고 캐시를 유지한다.
     * KBO 선수조회 HTML은 OkHttp 동기 호출이라 IO에서 실행해야 한다.
     */
    suspend fun fetchTeamJerseyRoster(
        teamCode: String = LOTTE_TEAM_CODE,
        force: Boolean = false,
    ): List<EntryPlayer> = withContext(Dispatchers.IO) {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        val season = kboToday().year
        val cachedRaw = store.jerseyRoster(code, season)
        // 등번호가 전부 비면 폴백 캐시로 보고 버린다
        val cached = cachedRaw.takeIf { list ->
            list.isNotEmpty() && list.any { it.backNumber.isNotBlank() }
        }.orEmpty()
        if (cachedRaw.isNotEmpty() && cached.isEmpty()) {
            store.setJerseyRoster(code, season, emptyList())
        }
        val fromSearch = runCatching {
            KboPlayerSearchParser.fetchTeamPlayers(code)
        }.onFailure { e ->
            Log.w("GiantsRepo", "jersey roster search failed: ${e.message}")
        }.getOrDefault(emptyList())
        val fromRegister = runCatching {
            val html = KboRegisterAllParser.fetchHtml()
            KboRegisterAllParser.parseTeamPlayers(html, code)
        }.onFailure { e ->
            Log.w("GiantsRepo", "jersey roster registerAll failed: ${e.message}")
        }.getOrDefault(emptyList())
        // 선수조회가 1군·퓨처스 전체라 기준. 등록현황은 선수코드가 없어 합치면 1군이 두 번 들어가므로
        // 등번호가 빈 선수만 채운다(선수조회가 실패했을 때만 등록현황 그대로).
        val remote = KboPlayerSearchParser.mergeWithRegister(fromSearch, fromRegister)
            .sortedWith(
                compareBy(
                    { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.backNumber },
                    { it.name },
                ),
            )
        if (remote.isEmpty()) {
            if (cached.isNotEmpty()) return@withContext cached
            return@withContext leadersAsJerseyFallback(code)
        }
        val codeByName = runCatching {
            val batters = fetchLeaders(false).filter { it.matchesTeam(code) }
            val pitchers = fetchLeaders(true).filter { it.matchesTeam(code) }
            (batters + pitchers).associate { it.name to it.playerCode }
        }.getOrDefault(emptyMap())
        val enriched = fillBackNumbersFromNaver(
            remote.map { p ->
                val pc = p.playerCode.ifBlank { codeByName[p.name].orEmpty() }
                if (pc == p.playerCode) p else p.copy(playerCode = pc)
            },
            season,
        )
        if (!force && jerseyFingerprint(enriched) == jerseyFingerprint(cached)) {
            return@withContext cached.ifEmpty { enriched }
        }
        store.setJerseyRoster(code, season, enriched)
        enriched
    }

    /**
     * KBO가 번호를 안 주는 선수(육성선수 등)만 네이버 선수 API로 채운다. 네이버 backNo 0 은 "없음"이라 버린다.
     * 호출은 번호가 빈 선수(구단당 4~11명)뿐이고 결과는 로스터 캐시에 들어간다.
     */
    private suspend fun fillBackNumbersFromNaver(
        players: List<EntryPlayer>,
        season: Int,
    ): List<EntryPlayer> = coroutineScope {
        players.map { p ->
            if (p.backNumber.isNotBlank() || p.playerCode.isBlank()) return@map async { p }
            async {
                // 일시 오류로 번호가 빈 채 캐시되지 않도록 한 번 더 시도한다.
                var no = 0
                for (attempt in 1..2) {
                    val got = runCatching { api.getPlayer(season.toString(), p.playerCode).result?.player?.backNo }
                        .getOrNull()
                    if (got != null) { no = got; break }
                }
                if (no > 0) p.copy(backNumber = no.toString()) else p
            }
        }.awaitAll().sortedWith(
            compareBy({ it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.backNumber }, { it.name }),
        )
    }

    private fun jerseyFingerprint(list: List<EntryPlayer>): String =
        list.sortedWith(
            compareBy({ it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }),
        ).joinToString(";") {
            "${it.backNumber}|${it.name}|${it.position}|${it.playerCode}"
        }

    private suspend fun leadersAsJerseyFallback(teamCode: String): List<EntryPlayer> {
        val batters = runCatching { fetchLeaders(false).filter { it.matchesTeam(teamCode) } }
            .getOrDefault(emptyList())
        val pitchers = runCatching { fetchLeaders(true).filter { it.matchesTeam(teamCode) } }
            .getOrDefault(emptyList())
        return (pitchers.map {
            EntryPlayer(
                name = it.name,
                playerCode = it.playerCode,
                position = "투수",
                isPitcher = true,
            )
        } + batters.map {
            EntryPlayer(
                name = it.name,
                playerCode = it.playerCode,
                position = "타자",
                isPitcher = false,
            )
        }).sortedBy { it.name }
    }

    /** 오늘부터 최대 lookback일 전까지 공시가 있는 가장 최근 날짜 */
    suspend fun findLatestEntryDate(lookback: Int = 21, teamCode: String = LOTTE_TEAM_CODE): LocalDate {
        val today = LocalDate.now()
        for (i in 0..lookback) {
            val d = today.minusDays(i.toLong())
            val changes = runCatching {
                fetchDayEntryChanges(d, resolveCodes = false, teamCode = teamCode)
            }.getOrNull()
            if (changes != null && changes.hasChanges) return d
        }
        return today
    }

    /** 한 달 중 등말소 공시가 있는 날짜 */
    suspend fun fetchEntryChangeDates(
        month: YearMonth,
        teamCode: String = LOTTE_TEAM_CODE,
    ): Set<LocalDate> {
        val hits = mutableSetOf<LocalDate>()
        for (day in 1..month.lengthOfMonth()) {
            val d = month.atDay(day)
            runCatching { fetchDayEntryChanges(d, resolveCodes = false, teamCode = teamCode) }
                .onSuccess { if (it.hasChanges) hits.add(d) }
        }
        runCatching { fetchAllRosterMoves(teamCode) }.getOrDefault(emptyList()).forEach { m ->
            val d = runCatching { LocalDate.parse(m.moveDate) }.getOrNull() ?: return@forEach
            if (YearMonth.from(d) == month) hits.add(d)
        }
        return hits
    }

    suspend fun fetchAllRosterMoves(teamCode: String = LOTTE_TEAM_CODE): List<RosterMove> =
        keuboApi.getRosterMoves(teamKeuboId(teamCode)).moves.map { it.toDomain() }

    suspend fun fetchRecentRosterMoves(days: Int = 7, teamCode: String = LOTTE_TEAM_CODE): List<RosterMove> {
        val from = kboToday().minusDays((days - 1).toLong()).toString()
        return fetchAllRosterMoves(teamCode).filter { it.moveDate >= from }.sortedByDescending { it.moveDate }
    }

    /**
     * 엔트리 알림용 경량 조회 — KBO 공식 GetRoster(당일)만 본다. Keubo 전체 이력보다 빠르다.
     */
    suspend fun pollRosterMovesForAlert(teamCode: String = ""): List<RosterMove> {
        val today = kboToday()
        val teamCode = teamCode.ifBlank { store.myTeamCode() }
        val dateStr = today.toString()
        val changes = runCatching {
            fetchDayEntryChanges(today, resolveCodes = false, teamCode = teamCode)
        }.getOrNull() ?: return emptyList()
        fun EntryPlayer.toMove(register: Boolean) = RosterMove(
            playerCode = playerCode,
            playerName = name,
            moveType = if (register) "등록" else "말소",
            moveDate = dateStr,
            isRegister = register,
        )
        return changes.registered.map { it.toMove(true) } + changes.removed.map { it.toMove(false) }
    }

    /**
     * 라인업 알림용 경량 조회 — 당일 KBO 일정 + (필요 시) 네이버 라인업 relay만 본다.
     */
    suspend fun refreshLineupAlert(): LotteGameInfo? {
        val today = kboToday()
        val focus = store.myTeamCode()
        val kboLotte = pickKboLotte(fetchKboGamesFresh(today), store.preferredLiveGameId(), focus)
            ?: return null
        var lotteInfo = kboLotte.toLotteBase(focus)
        if (lotteInfo.status == GameStatus.CANCELED || lotteInfo.status == GameStatus.ENDED) {
            return lotteInfo
        }
        val gameId = kboLotte.naverGameId()
        if (gameId.isNotBlank() &&
            (lotteInfo.lineupAnnounced || lotteInfo.lotteLineup.size < 9)
        ) {
            runCatching { fetchLineupRelay(gameId) }.getOrNull()?.let { relay ->
                if (relayHasLineup(relay) || lotteInfo.lineupAnnounced) {
                    lotteInfo = mergeRelay(lotteInfo, relay)
                }
            }
        }
        return lotteInfo.copy(
            lineupAnnounced = lotteInfo.lineupAnnounced || lotteInfo.lotteLineup.size >= 9,
        )
    }

    suspend fun fetchLeaders(isPitcher: Boolean): List<LeaderPlayer> {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        val type = if (isPitcher) "pitcher" else "batter"
        return keuboApi.getStats(type, season).stats.map { it.toLeader(isPitcher) }
    }

    suspend fun fetchTeamCard(slug: String = KeuboApi.LOTTE_SLUG): LotteTeamCard =
        runCatching { keuboApi.getTeamCard(slug).toDomain() }.getOrDefault(LotteTeamCard())

    suspend fun fetchMyTeamCard(): LotteTeamCard =
        fetchTeamCard(teamKeuboSlug(store.myTeamCode()))

    suspend fun fetchLotteTeamCard(): LotteTeamCard = fetchMyTeamCard()

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
                val pitcherFirst = fallback?.isPitcher == true ||
                    isPitcherPosition(fallback?.position.orEmpty())
                val ordered = if (pitcherFirst) {
                    fetchLeaders(true) + fetchLeaders(false)
                } else {
                    fetchLeaders(false) + fetchLeaders(true)
                }
                pickLeaderByName(name, ordered)?.playerCode.orEmpty()
            }.getOrDefault("")
        }
        val hintId = gameIdHint?.takeIf { it.isNotBlank() }
            ?: runCatching { api.getGames(
                fromDate = today.minusDays(14).format(fmt),
                toDate = today.plusDays(3).format(fmt),
            ).result?.games.orEmpty() }.getOrDefault(emptyList())
                .filter { it.categoryId == "kbo" && it.involvesTeam(store.myTeamCode()) }
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
                resolvedCode.isNotBlank() && (
                    it.playerCode == resolvedCode ||
                        it.playerInfo?.pCode == resolvedCode ||
                        it.currentSeasonStats?.playerCode == resolvedCode
                    )
            } ?: blocks.firstOrNull {
                resolvedCode.isBlank() &&
                    fallback?.name?.isNotBlank() == true &&
                    it.playerInfo?.name == fallback.name
            }
            if (match != null) {
                detail = mergePreviewPlayer(detail, match)
            }

            val relay = runCatching { api.getRelay(hintId).result?.textRelayData }.getOrNull()
            val entryPlayer = listOfNotNull(relay?.homeEntry, relay?.awayEntry)
                .flatMap { it.batter + it.pitcher }
                .firstOrNull { resolvedCode.isNotBlank() && it.pcode == resolvedCode }
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
        ).let { withKeuboSeasonStats(it) }.let { PlayerBiographySource.enrich(it, api) }
    }

    /** 프리뷰에 없는 선수라도 루타(Keubo) 시즌 스탯으로 보강. 투수/타자 힌트를 존중한다. */
    private suspend fun withKeuboSeasonStats(detail: PlayerDetail): PlayerDetail {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        val code = detail.playerCode
        val name = detail.name
        val pitcherHint = detail.isPitcher || isPitcherPosition(detail.position)

        fun matchByCode(s: KeuboStatDto): Boolean =
            code.isNotBlank() && (s.kboId == code || s.playerId == code)

        fun matchByNamePreferLotte(stats: List<KeuboStatDto>): KeuboStatDto? {
            val hits = stats.filter { name.isNotBlank() && it.name == name }
            return hits.firstOrNull { it.team.contains("롯데") || it.team.equals("LT", true) }
                ?: hits.firstOrNull()
        }

        suspend fun findPitcher(): KeuboStatDto? = runCatching {
            val stats = keuboApi.getStats("pitcher", season).stats
            stats.firstOrNull(::matchByCode) ?: matchByNamePreferLotte(stats)
        }.getOrNull()

        suspend fun findBatter(): KeuboStatDto? = runCatching {
            val stats = keuboApi.getStats("batter", season).stats
            stats.firstOrNull(::matchByCode) ?: matchByNamePreferLotte(stats)
        }.getOrNull()

        if (pitcherHint) {
            val pitcher = findPitcher() ?: return detail
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

        val batter = findBatter()
        if (batter != null) {
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
                isPitcher = false,
            )
        }

        val pitcher = findPitcher() ?: return detail
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

    private fun pickLeaderByName(name: String, leaders: List<LeaderPlayer>): LeaderPlayer? {
        val hits = leaders.filter { it.name == name }
        return hits.firstOrNull { it.isLotte }
    }

    private fun basePlayerFromLineup(slot: LineupSlot?, code: String): PlayerDetail {
        val c = code.ifBlank { slot?.playerCode.orEmpty() }
        val pitcher = slot?.isPitcher == true || isPitcherPosition(slot?.position.orEmpty())
        return PlayerDetail(
            playerCode = c,
            name = slot?.name.orEmpty(),
            backNumber = slot?.backNumber.orEmpty(),
            hitType = slot?.hitType.orEmpty(),
            position = slot?.position.orEmpty(),
            seasonAvg = slot?.seasonAvg?.let { String.format("%.3f", it) }.orEmpty(),
            todayLine = if (slot != null) "${slot.todayHits}/${slot.todayAtBats}" else "",
            photoUrl = if (c.isNotBlank()) playerPhotoUrl(c) else "",
            isPitcher = pitcher,
        )
    }

    private fun mergePreviewPlayer(base: PlayerDetail, block: PreviewPlayerBlock): PlayerDetail {
        val info = block.playerInfo
        val stats = block.currentSeasonStats
        val isPitcher = base.isPitcher ||
            isPitcherPosition(base.position) ||
            stats?.era != null || stats?.inn != null
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
            hotCold = block.hotColdZone.map { it.toDomain() }.ifEmpty { base.hotCold },
        )
    }

    /** 알림 폴링용 — 당일 일정 캐시를 무시하고 최신을 받는다. */
    private suspend fun fetchKboGamesFresh(date: LocalDate): List<KboOfficialGame> {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val games = runCatching {
            kboOfficialApi.getGameList(date = KboOfficialApi.dateParam(date))
                .game
                .filter { it.gameId.isNotBlank() }
                .forKboDate(date)
        }.getOrDefault(emptyList())
        if (games.isNotEmpty()) kboDateCache[key] = System.currentTimeMillis() to games
        return games
    }

    /** KBO 공식 일정 (1차 소스). 실패하면 빈 목록 → 호출부가 네이버로 폴백한다. */
    private suspend fun fetchKboGames(date: LocalDate): List<KboOfficialGame> {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val cached = kboDateCache[key]
        val now = System.currentTimeMillis()
        val ttl = if (date == kboToday() || date == LocalDate.now(KBO_ZONE)) {
            KBO_TODAY_TTL_MS
        } else {
            KBO_PAST_TTL_MS
        }
        if (cached != null && now - cached.first < ttl) return cached.second
        val games = runCatching {
            kboOfficialApi.getGameList(date = KboOfficialApi.dateParam(date))
                .game
                .filter { it.gameId.isNotBlank() }
                .forKboDate(date)
        }.getOrDefault(emptyList())
        if (games.isNotEmpty()) kboDateCache[key] = now to games
        return games
    }

    /** 날짜 범위 KBO 일정 (캐시·병렬 조회) */
    private suspend fun fetchKboGamesCached(from: LocalDate, to: LocalDate): List<KboOfficialGame> =
        coroutineScope {
            var d = from
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<KboOfficialGame>>>()
            while (!d.isAfter(to)) {
                val day = d
                jobs.add(async { fetchKboGames(day) })
                d = d.plusDays(1)
            }
            jobs.flatMap { it.await() }
        }

    /** 네이버 폴백 경로에서 KBO 취소 사유 보강 (키: AWAY_HOME) */
    private suspend fun cancelReasonsFor(
        dtos: List<GameDto>,
        date: LocalDate,
    ): Map<String, String> {
        if (dtos.none { it.cancel }) return emptyMap()
        return fetchKboGames(date)
            .mapNotNull { g ->
                g.cancelReasonLabel()?.let { g.matchKey() to it }
            }
            .toMap()
    }

    private suspend fun fetchLineupRelay(gameId: String): TextRelayData? =
        api.getRelay(gameId).result?.textRelayData

    private fun relayHasLineup(relay: TextRelayData): Boolean =
        listOfNotNull(relay.homeLineup, relay.awayLineup)
            .any { dto -> dto.batter.any { it.name.isNotBlank() } }

    /**
     * 스냅샷 폴링용. 현재 이닝 1회 + 직전 이닝이 캐시에 없을 때만 1회.
     * 전체 이닝은 [expandFullRelay] (중계 탭)에서 채운다.
     */
    private suspend fun fetchLiveRelay(gameId: String): TextRelayData? {
        val base = api.getRelay(gameId).result?.textRelayData ?: return null
        val cache = relayInningCache.getOrPut(gameId) { ConcurrentHashMap() }
        if (base.textRelays.isNotEmpty()) {
            val currentOnly = base.textRelays.filter { it.inn == base.inn || it.inn == 0 }
                .ifEmpty { base.textRelays }
            cache[base.inn] = currentOnly
        }
        val prevInn = base.inn - 1
        val hadHistory = cache.keys.any { it != base.inn }
        if (hadHistory && prevInn >= 1 && cache[prevInn].isNullOrEmpty()) {
            val chunk = runCatching {
                api.getRelay(gameId, inning = prevInn).result?.textRelayData?.textRelays.orEmpty()
            }.getOrDefault(emptyList())
            if (chunk.isNotEmpty()) cache[prevInn] = chunk
        }
        fun scoreKeys(map: Map<String, String>?) =
            map?.keys?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: 0
        val maxFromScore = maxOf(
            scoreKeys(base.inningScore?.home),
            scoreKeys(base.inningScore?.away),
        )
        val maxInn = maxOf(base.inn, maxFromScore, 1).coerceAtMost(18)
        val merged = (1..maxInn).flatMap { cache[it].orEmpty() }
            .ifEmpty { base.textRelays }
        return base.copy(textRelays = merged)
    }

    private suspend fun fetchRelayForPoll(
        gameId: String,
        status: GameStatus,
        lineupAnnounced: Boolean,
    ): TextRelayData? {
        if (status == GameStatus.BEFORE && lineupAnnounced) {
            val quick = fetchLineupRelay(gameId)
            if (quick != null && relayHasLineup(quick)) return quick
        }
        return fetchLiveRelay(gameId)
    }

    /** 중계 탭을 열었을 때 1~현재 이닝을 합친다. 끝난 이닝은 캐시를 재사용한다. */
    suspend fun expandFullRelay(game: LotteGameInfo): LotteGameInfo? {
        if (game.gameId.isBlank()) return null
        val relay = fetchFullRelay(game.gameId) ?: return null
        return mergeRelay(game, relay)
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

    companion object {
        private const val STANDINGS_TTL_MS = 5 * 60_000L
        private const val KBO_TODAY_TTL_MS = 30_000L
        private const val KBO_PAST_TTL_MS = 10 * 60_000L
        private const val KBO_RANGE_TTL_MS = 10 * 60_000L
        private const val WEATHER_TTL_MS = 15 * 60_000L
        private const val SUMMARY_TTL_MS = 5 * 60_000L
        private const val SNAPSHOT_FRESH_MS = 4_000L
        private const val RUTA_TTL_MS = 25_000L
        private const val TAG = "GiantsRepo"

        @Volatile
        private var instance: GiantsRepository? = null

        fun get(context: Context): GiantsRepository =
            instance ?: synchronized(this) {
                instance ?: GiantsRepository(context.applicationContext).also { instance = it }
            }
    }
}
