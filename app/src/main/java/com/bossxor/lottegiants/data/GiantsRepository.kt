package com.bossxor.lottegiants.data

import android.content.Context
import com.bossxor.lottegiants.domain.GamePreview
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.WinProb
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.cancelDisplayLabel
import com.bossxor.lottegiants.domain.estimateLotteWinProb
import com.bossxor.lottegiants.domain.withSuspendFilled
import com.bossxor.lottegiants.domain.teamHomeStadiumName
import com.bossxor.lottegiants.domain.teamKeuboSlug
import com.bossxor.lottegiants.domain.remainingGames
import com.bossxor.lottegiants.domain.seasonLength
import com.bossxor.lottegiants.domain.widgetRaceLine
import com.bossxor.lottegiants.domain.gameCountdownLabel
import com.bossxor.lottegiants.domain.isCanceledGame
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.snapshotStaleForKboDay
import com.bossxor.lottegiants.domain.normalizedIfCanceled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.util.Log
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
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

    private val snapshots = SnapshotCoordinator(store::loadSnapshot, store::saveSelectedSnapshot, {
        SnapshotIdentity(store.myTeamCode(), store.preferredLiveGameId(), kboToday().toString())
    })
    private val metadataScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val metadataLock = Any()
    private var metadataJob: kotlinx.coroutines.Job? = null
    private val schedules = ScheduleSource(kboOfficialApi)
    private val relays = RelaySource(api)
    private val rosters = RosterSource(api, kboApi, keuboApi, store, ::fetchLeaders)
    private val previews = PreviewSource(api)
    private val weather = WeatherSource(weatherApi)
    private val players = PlayerSource(api, keuboApi, relays, previews, store, ::fetchLeaders)
    @Volatile private var snapshotFailCount = 0
    @Volatile private var snapshotCooldownUntil = 0L


    @Volatile private var lastRutaAt = 0L
    @Volatile private var lastRutaGameId = ""
    @Volatile private var lastRutaExtras = RutaGameExtras(connected = false)


    private var standingsCache: Pair<Long, List<TeamStanding>>? = null

    /** 일정·순위 캐시는 두고, 팀 전환 때 스냅샷·중계·루타만 비운다. */
    suspend fun clearTeamCaches() {
        snapshots.invalidate()
        relays.clear()
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
    suspend fun refreshSnapshot(force: Boolean = false): LiveSnapshot =
        snapshots.refresh(SnapshotKind.FULL, force, SNAPSHOT_FRESH_MS) {
            val stale = lastKnownSnapshot()
            if (stale != null && !force && System.currentTimeMillis() < snapshotCooldownUntil) return@refresh stale
            try {
                fetchFreshSnapshot().also { snapshotFailCount = 0; snapshotCooldownUntil = 0L }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "refreshSnapshot failed", e)
                snapshotFailCount += 1
                snapshotCooldownUntil = System.currentTimeMillis() + snapshotBackoffMs(snapshotFailCount)
                val todayOnly = try { fetchTodayOnlySnapshot() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                when {
                    todayOnly?.lotteGame != null || todayOnly?.nextLotteGame != null -> todayOnly!!
                    stale?.lotteGame != null || stale?.nextLotteGame != null -> stale!!
                    todayOnly != null -> todayOnly
                    else -> emptyFocusSnapshot()
                }
            }
        }

    /** 화면·서비스·위젯이 같은 LIVE 갱신을 공유하고 보조 자료는 별도 주기로 보완한다. */
    suspend fun refreshLiveSnapshot(): LiveSnapshot {
        val previous = lastKnownSnapshot()
        if (previous?.lotteGame?.status != GameStatus.LIVE || snapshotStaleForKboDay(previous.updatedAtMillis)) return refreshSnapshot()
        refreshMetadataInBackground()
        return snapshots.refresh(SnapshotKind.LIVE, false, SNAPSHOT_FRESH_MS) {
            val prev = lastKnownSnapshot() ?: previous
            val today = kboToday()
            val games = fetchKboGamesFresh(today)
            val focus = store.myTeamCode()
            val selected = pickKboLotte(games, store.preferredLiveGameId(), focus) ?: return@refresh prev
            var game = selected.toLotteBase(focus)
            val cursor = com.bossxor.lottegiants.domain.LiveEventCursor.decode(store.liveEventCursor())
            val recover = cursor.inning.takeIf { cursor.gameId == game.gameId }
            val relay = fetchLiveRelay(game.gameId, recover)
            if (relay != null) game = mergeRelay(game, relay)
            prev.copy(updatedAtMillis = System.currentTimeMillis(), lotteGame = game,
                todayLotteGames = kboToMiniGames(today, games.filter { it.involvesTeam(focus) }),
                otherGames = kboToMiniGames(today, games.filterNot { it.involvesTeam(focus) }),
                pitchLocations = game.pitchLocations,
                winProbSeries = relay?.let { buildWinProbFromRelay(it, game.isHome) }?.takeIf { it.isNotEmpty() }
                    ?: prev.winProbSeries)
        }
    }

    /** 라이브는 점수만 기다린다. 프리뷰·순위·날씨는 최대 1분 간격의 별도 작업이다. */
    private fun refreshMetadataInBackground() = synchronized(metadataLock) {
        if (metadataJob?.isActive == true) return@synchronized
        metadataJob = metadataScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                if (snapshots.fresh(SnapshotKind.FULL, 60_000L) == null) refreshSnapshot()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w(TAG, "metadata refresh failed", e) }
        }.also { it.start() }
    }

    private suspend fun lastKnownSnapshot(): LiveSnapshot? = snapshots.latest()

    private fun snapshotBackoffMs(fails: Int): Long = when {
        fails <= 1 -> 15_000L
        fails == 2 -> 30_000L
        fails == 3 -> 60_000L
        else -> 120_000L
    }

    private suspend fun kboSeasonWindow(today: LocalDate): List<KboOfficialGame> = schedules.seasonWindow(today)

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
        val prev = lastKnownSnapshot()
        val rangeWasCached = schedules.hasSeasonWindow(today)

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
            lotteInfo = safeEnrich(lotteInfo, kboRange, kboLotte)
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
            runCatching { previews.get(base.gameId) }.getOrNull()
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
        val kbo = schedules.range(month.atDay(1), month.atEndOfMonth())
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

    suspend fun fetchStadiumWeather(stadium: String, fallbackTeamCode: String = LOTTE_TEAM_CODE) =
        weather.get(stadium, fallbackTeamCode)

    /**
     * KBO 공식 선수등록현황(날짜별 등록/말소).
     * 출처: m.koreabaseball.com GetRoster
     */
    suspend fun fetchDayEntryChanges(date: LocalDate, resolveCodes: Boolean = true, teamCode: String = LOTTE_TEAM_CODE) = rosters.fetchDayEntryChanges(date, resolveCodes, teamCode)
    suspend fun fetchTeamJerseyRoster(teamCode: String = LOTTE_TEAM_CODE, force: Boolean = false) = rosters.fetchTeamJerseyRoster(teamCode, force)
    suspend fun findLatestEntryDate(lookback: Int = 21, teamCode: String = LOTTE_TEAM_CODE) = rosters.findLatestEntryDate(lookback, teamCode)
    suspend fun fetchEntryChangeDates(month: YearMonth, teamCode: String = LOTTE_TEAM_CODE) = rosters.fetchEntryChangeDates(month, teamCode)
    suspend fun fetchAllRosterMoves(teamCode: String = LOTTE_TEAM_CODE) = rosters.fetchAllRosterMoves(teamCode)
    suspend fun fetchRecentRosterMoves(days: Int = 7, teamCode: String = LOTTE_TEAM_CODE) = rosters.fetchRecentRosterMoves(days, teamCode)
    suspend fun pollRosterMovesForAlert(teamCode: String = "") = rosters.pollRosterMovesForAlert(teamCode)

    /**
     * 라인업 알림용 경량 조회 — 당일 KBO 일정 + (필요 시) 네이버 라인업 relay만 본다.
     */
    suspend fun refreshLineupAlert(requestedGameId: String? = null): LotteGameInfo? {
        val today = kboToday()
        val focus = store.myTeamCode()
        val games = try { fetchKboGamesFresh(today) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        val kboLotte = games.firstOrNull { it.involvesTeam(focus) && it.naverGameId() == requestedGameId }
            ?: pickKboLotte(games.filter { it.toLotteBase(focus).status == GameStatus.BEFORE }, "", focus)
            ?: pickKboLotte(games, store.preferredLiveGameId(), focus)
        var lotteInfo = kboLotte?.toLotteBase(focus) ?: run {
            val candidates = api.getGames(today.toString(), today.toString()).result?.games.orEmpty()
                .filter { it.categoryId == "kbo" && it.involvesTeam(focus) }
            val naver = candidates.firstOrNull { it.gameId == requestedGameId }
                ?: pickNaverLotte(candidates.filter { it.toLotteBase(focusTeamCode = focus).status == GameStatus.BEFORE }, "")
                ?: pickNaverLotte(candidates, store.preferredLiveGameId()) ?: return null
            naver.toLotteBase(focusTeamCode = focus)
        }
        if (lotteInfo.status == GameStatus.CANCELED || lotteInfo.status == GameStatus.ENDED) {
            return lotteInfo
        }
        val gameId = lotteInfo.gameId
        if (gameId.isNotBlank() &&
            (lotteInfo.lineupAnnounced || lotteInfo.lotteLineup.size < 9)
        ) {
            fetchLineupRelay(gameId)?.let { relay ->
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

    suspend fun fetchPlayerDetail(playerCode: String, fallback: LineupSlot? = null, gameIdHint: String? = null) =
        players.fetchPlayerDetail(playerCode, fallback, gameIdHint)

    private suspend fun fetchKboGamesFresh(date: LocalDate): List<KboOfficialGame> = schedules.day(date, fresh = true)
    private suspend fun fetchKboGames(date: LocalDate): List<KboOfficialGame> = schedules.day(date)
    private suspend fun fetchKboGamesCached(from: LocalDate, to: LocalDate): List<KboOfficialGame> = schedules.range(from, to)

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

    private suspend fun fetchLineupRelay(gameId: String): TextRelayData? = relays.current(gameId)
    private fun relayHasLineup(relay: TextRelayData): Boolean = relays.hasLineup(relay)
    private suspend fun fetchLiveRelay(gameId: String, recoverFrom: Int? = null): TextRelayData? = relays.live(gameId, recoverFrom)
    private suspend fun fetchRelayForPoll(gameId: String, status: GameStatus, lineupAnnounced: Boolean): TextRelayData? =
        relays.poll(gameId, status, lineupAnnounced)
    suspend fun expandFullRelay(game: LotteGameInfo): LotteGameInfo? =
        game.gameId.takeIf { it.isNotBlank() }?.let { id -> relays.full(id)?.let { mergeRelay(game, it) } }

    companion object {
        private const val STANDINGS_TTL_MS = 5 * 60_000L
        private const val WEATHER_TTL_MS = 15 * 60_000L
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
