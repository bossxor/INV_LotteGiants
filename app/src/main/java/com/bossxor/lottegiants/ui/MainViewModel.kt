package com.bossxor.lottegiants.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.DayEntryChanges
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.FavoritePlayer
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.PitcherLine
import com.bossxor.lottegiants.domain.PlayerDetail
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.StadiumWeather
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.ThemeMode
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.involvesTeam
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.teamHomeStadiumName
import com.bossxor.lottegiants.domain.teamKeuboSlug
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.widget.WidgetUpdater
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GiantsRepository.get(app)

    private val _snapshot = MutableStateFlow<LiveSnapshot?>(null)
    val snapshot: StateFlow<LiveSnapshot?> = _snapshot.asStateFlow()

    private val _standings = MutableStateFlow<List<TeamStanding>>(emptyList())
    val standings: StateFlow<List<TeamStanding>> = _standings.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 라이브 탭 자동 새로고침 실패 안내 (히스토리·엔트리와 무관) */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()
    /** 사용자가 배너를 닫으면 자동 폴링 실패로 다시 안 띄운다. */
    private var refreshErrorDismissed = false

    private val _favoriteStats = MutableStateFlow<Map<String, PlayerDetail>>(emptyMap())
    val favoriteStats: StateFlow<Map<String, PlayerDetail>> = _favoriteStats.asStateFlow()
    private var favoriteStatsJob: Job? = null

    private val calendarController = CalendarController(viewModelScope, repo, ::currentTeamCode) { _error.value = it }
    private val _dayGames = calendarController.dayGames
    val dayGames: StateFlow<List<MiniGame>> = _dayGames.asStateFlow()

    private val _dayGamesLoading = calendarController.loading
    val dayGamesLoading: StateFlow<Boolean> = _dayGamesLoading.asStateFlow()

    private val _selectedDate = MutableStateFlow(kboToday())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    /** 결과 탭에서 날짜를 고르면 false. 오전 5시 경계에 '오늘'을 따라간다. */
    private var followKboToday = true

    private val _monthGames = calendarController.monthGames
    val monthGames: StateFlow<List<MiniGame>> = _monthGames.asStateFlow()

    private val _calendarMonth = calendarController.month
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    private val _weather = MutableStateFlow<StadiumWeather?>(null)
    val weather: StateFlow<StadiumWeather?> = _weather.asStateFlow()

    private val entryController = EntryController(viewModelScope, repo)
    private val _entryDate = entryController.date
    val entryDate: StateFlow<LocalDate> = _entryDate.asStateFlow()

    private val _dayEntry = entryController.changes
    val dayEntry: StateFlow<DayEntryChanges?> = _dayEntry.asStateFlow()

    private val _entryLoading = entryController.loading
    val entryLoading: StateFlow<Boolean> = _entryLoading.asStateFlow()

    private val _entryChangeDates = entryController.dates
    val entryChangeDates: StateFlow<Set<LocalDate>> = _entryChangeDates.asStateFlow()

    private val _recentMoves = entryController.recent
    val recentMoves: StateFlow<List<RosterMove>> = _recentMoves.asStateFlow()

    private val _teamCard = MutableStateFlow<LotteTeamCard?>(null)
    val teamCard: StateFlow<LotteTeamCard?> = _teamCard.asStateFlow()

    private val _batterLeaders = MutableStateFlow<List<LeaderPlayer>>(emptyList())
    val batterLeaders: StateFlow<List<LeaderPlayer>> = _batterLeaders.asStateFlow()

    private val _pitcherLeaders = MutableStateFlow<List<LeaderPlayer>>(emptyList())
    val pitcherLeaders: StateFlow<List<LeaderPlayer>> = _pitcherLeaders.asStateFlow()

    private val playerController = PlayerDetailController(viewModelScope, repo) { _snapshot.value?.lotteGame?.gameId }
    private val _playerDetail = playerController._playerDetail
    val playerDetail: StateFlow<PlayerDetail?> = _playerDetail.asStateFlow()

    private val _playerLoading = playerController._playerLoading
    val playerLoading: StateFlow<Boolean> = _playerLoading.asStateFlow()

    val favoritePlayers: StateFlow<List<FavoritePlayer>> = repo.store.favoritePlayersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteCodes: StateFlow<Set<String>> = favoritePlayers
        .map { list -> list.map { it.code }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val themeMode: StateFlow<ThemeMode> = repo.store.themeModeFlow
        .map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val myTeamCode: StateFlow<String> = repo.store.myTeamCodeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, LOTTE_TEAM_CODE)

    private val _secondsUntilRefresh = MutableStateFlow(POLL_LIVE_SEC)
    val secondsUntilRefresh: StateFlow<Int> = _secondsUntilRefresh.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _viewingGame = MutableStateFlow<LotteGameInfo?>(null)
    val viewingGame: StateFlow<LotteGameInfo?> = _viewingGame.asStateFlow()

    private val _viewingLoading = MutableStateFlow(false)
    val viewingLoading: StateFlow<Boolean> = _viewingLoading.asStateFlow()

    private val _resultsTeamCode = MutableStateFlow("")
    val resultsTeamCode: StateFlow<String> = _resultsTeamCode.asStateFlow()

    private val _seasonGames = MutableStateFlow<List<MiniGame>>(emptyList())
    val seasonGames: StateFlow<List<MiniGame>> = _seasonGames.asStateFlow()

    private val _seasonLoading = MutableStateFlow(false)
    val seasonLoading: StateFlow<Boolean> = _seasonLoading.asStateFlow()

    private val rosterController = PlayerRosterController(viewModelScope, repo)
    private val _jerseyPlayers = rosterController.players
    val jerseyPlayers: StateFlow<List<com.bossxor.lottegiants.domain.EntryPlayer>> = _jerseyPlayers.asStateFlow()

    private val _jerseyLoading = rosterController.loading
    val jerseyLoading: StateFlow<Boolean> = _jerseyLoading.asStateFlow()

    private val _playersTeamCode = rosterController.team
    val playersTeamCode: StateFlow<String> = _playersTeamCode.asStateFlow()

    private val _overlayTeamCode = MutableStateFlow(LOTTE_TEAM_CODE)
    val overlayTeamCode: StateFlow<String> = _overlayTeamCode.asStateFlow()

    private val _overlayTeamCard = MutableStateFlow<LotteTeamCard?>(null)
    val overlayTeamCard: StateFlow<LotteTeamCard?> = _overlayTeamCard.asStateFlow()

    private var viewingGameId: String? = null
    private var fullRelayFor: String? = null

    private var pollJob: Job? = null
    private var seasonJob: Job? = null
    private var seasonFetchFailed = false

    init {
        viewModelScope.launch {
            _snapshot.value = repo.store.loadSnapshot()
            refreshWeatherFromSnapshot(_snapshot.value)
            if (_resultsTeamCode.value.isBlank()) {
                _resultsTeamCode.value = repo.store.myTeamCode()
            }
        }
        viewModelScope.launch {
            favoritePlayers.collect { list ->
                loadFavoriteStats(list)
            }
        }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                val wait = pollIntervalSec()
                for (left in wait downTo 1) {
                    _secondsUntilRefresh.value = left
                    delay(1_000L)
                    if (!isActive) return@launch
                }
                _secondsUntilRefresh.value = 0
            }
        }
    }

    private fun pollIntervalSec(): Int {
        val live = _snapshot.value?.lotteGame?.status == GameStatus.LIVE
        return if (live) POLL_LIVE_SEC else POLL_IDLE_SEC
    }

    fun ensureStandingsTab() {
        if (_standings.value.isEmpty()) {
            refreshStandings()
        } else {
            ensureSeasonGames()
            ensureLeaders()
        }
    }

    fun ensureResultsTab() {
        if (_dayGames.value.isEmpty()) loadGamesForDate(_selectedDate.value)
        if (_monthGames.value.isEmpty()) loadMonthGames(_calendarMonth.value)
    }

    fun ensurePlayersTab() {
        val code = _playersTeamCode.value.ifBlank { currentTeamCode() }
        if (_jerseyPlayers.value.isEmpty() || _playersTeamCode.value != code) {
            loadJerseyRoster(code, force = false)
        } else {
            loadJerseyRoster(code, force = false, backgroundOnly = true)
        }
    }

    fun setPlayersTeam(code: String) {
        val next = code.trim().uppercase()
        if (_playersTeamCode.value == next && _jerseyPlayers.value.isNotEmpty()) {
            loadJerseyRoster(next, force = false, backgroundOnly = true)
            return
        }
        _playersTeamCode.value = next
        loadJerseyRoster(next, force = false)
    }

    fun refreshJerseyRoster() {
        loadJerseyRoster(_playersTeamCode.value.ifBlank { currentTeamCode() }, force = true)
    }

    private fun loadJerseyRoster(teamCode: String, force: Boolean, backgroundOnly: Boolean = false) {
        rosterController.load(teamCode.ifBlank { currentTeamCode() }, force, backgroundOnly)
    }

    fun ensureLeaders() {
        if (_batterLeaders.value.isNotEmpty() && _pitcherLeaders.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { repo.fetchLeaders(false) }.onSuccess { _batterLeaders.value = it }
            runCatching { repo.fetchLeaders(true) }.onSuccess { _pitcherLeaders.value = it }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshOnce(force = true)
                runCatching { repo.fetchLotteTeamCard() }.onSuccess { _teamCard.value = it }
            } finally {
                _isRefreshing.value = false
                stopPolling()
                startPolling()
            }
        }
    }

    fun selectLiveGame(gameId: String) {
        if (gameId.isBlank()) return
        viewingGameId = null
        _viewingGame.value = null
        fullRelayFor = null
        viewModelScope.launch {
            repo.store.setPreferredLiveGameId(gameId)
            refreshNow()
        }
    }

    fun refreshStandings() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                runCatching { repo.fetchStandings() }
                    .onSuccess { _standings.value = it }
                    .onFailure { _error.value = it.message }
                runCatching { repo.fetchLotteTeamCard() }.onSuccess { _teamCard.value = it }
                runCatching { repo.fetchLeaders(false) }
                    .onSuccess { _batterLeaders.value = it }
                    .onFailure { _error.value = it.message }
                runCatching { repo.fetchLeaders(true) }
                    .onSuccess { _pitcherLeaders.value = it }
                    .onFailure { _error.value = it.message }
                ensureSeasonGames()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshDayGames() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetchDayGames(_selectedDate.value)
                calendarController.loadMonth(_calendarMonth.value).join()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadPlayerByCode(playerCode: String, name: String = "") = playerController.loadPlayerByCode(playerCode, name)

    fun loadPlayerFromLeader(player: LeaderPlayer) = playerController.loadPlayerFromLeader(player)

    fun loadPitcherDetail(p: PitcherLine) = playerController.loadPitcherDetail(p)

    fun toggleFavorite(code: String, name: String = "", team: String = "") {
        if (code.isBlank()) return
        viewModelScope.launch { repo.store.toggleFavorite(code, name, team) }
    }

    fun removeFavorite(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch { repo.store.removeFavorite(code) }
    }

    fun clearRefreshError() {
        _refreshError.value = null
        refreshErrorDismissed = true
    }

    private fun loadFavoriteStats(list: List<FavoritePlayer>) {
        favoriteStatsJob?.cancel()
        if (list.isEmpty()) {
            _favoriteStats.value = emptyMap()
            return
        }
        favoriteStatsJob = viewModelScope.launch {
            val out = linkedMapOf<String, PlayerDetail>()
            for (fav in list) {
                if (fav.code.isBlank()) continue
                val detail = runCatching {
                    repo.fetchPlayerDetail(fav.code)
                }.getOrNull() ?: continue
                out[fav.code] = detail
                _favoriteStats.value = out.toMap()
            }
        }
    }

    suspend fun refreshOnce(force: Boolean = false) {
        if (followKboToday) {
            val today = kboToday()
            if (_selectedDate.value != today) {
                _selectedDate.value = today
                val ym = YearMonth.from(today)
                if (ym != _calendarMonth.value) {
                    _calendarMonth.value = ym
                    loadMonthGames(ym)
                }
                loadGamesForDate(today)
            }
        }
        val staleGame = _snapshot.value?.lotteGame?.let { !it.belongsToKboToday() } == true
        runCatching {
            if (!force && !staleGame && _snapshot.value?.lotteGame?.status == GameStatus.LIVE) repo.refreshLiveSnapshot()
            else repo.refreshSnapshot(force || staleGame)
        }
            .onSuccess {
                _snapshot.value = it
                _refreshError.value = null
                refreshErrorDismissed = false
                WidgetUpdater.updateAll(getApplication())
                if (_selectedDate.value == kboToday()) {
                    syncTodayGamesFromSnapshot(it)
                }
                refreshWeatherFromSnapshot(it)
                refreshViewingGame(it)
            }
            .onFailure { e ->
                if (e is CancellationException) throw e
                Log.e("LiveVM", "refreshOnce", e)
                val snap = _snapshot.value
                if (snap == null) {
                    _refreshError.value = e.message ?: "경기를 불러오지 못했습니다."
                    return@onFailure
                }
                val whenStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                    .format(java.util.Date(snap.updatedAtMillis.coerceAtLeast(0L)))
                val msg = "오프라인 · 마지막 갱신 $whenStr"
                // 수동 새로고침 실패는 항상, 자동은 닫기 전 한 번만
                if (force || !refreshErrorDismissed) {
                    _refreshError.value = msg
                }
            }
    }

    /** 결과·다른 구장에서 경기 상세. 오늘 롯데 경기는 기존 라이브 선택, 그 외는 오버레이. */
    fun openGame(gameId: String) {
        if (gameId.isBlank()) return
        val snap = _snapshot.value
        if (isTodayLotteGame(gameId, snap)) {
            selectLiveGame(gameId)
            return
        }
        viewingGameId = gameId
        fullRelayFor = null
        viewModelScope.launch {
            _viewingLoading.value = true
            try {
                val fetched = runCatching { repo.fetchGameDetail(gameId) }.getOrNull()
                if (fetched != null) {
                    _viewingGame.value = fetched
                    _error.value = null
                } else if (_viewingGame.value == null) {
                    viewingGameId = null
                    _error.value = "경기를 불러오지 못했습니다."
                }
            } finally {
                _viewingLoading.value = false
            }
        }
    }

    fun backToLotte() {
        viewingGameId = null
        _viewingGame.value = null
        _viewingLoading.value = false
        fullRelayFor = null
    }

    /** 중계 탭을 열 때 지난 이닝 문자중계를 한 번에 합친다. */
    fun ensureFullRelay(gameId: String) {
        if (gameId.isBlank() || fullRelayFor == gameId) return
        viewModelScope.launch {
            val current = _viewingGame.value?.takeIf { it.gameId == gameId }
                ?: _snapshot.value?.lotteGame?.takeIf { it.gameId == gameId }
                ?: _snapshot.value?.nextLotteGame?.takeIf { it.gameId == gameId }
                ?: return@launch
            val expanded = runCatching { repo.expandFullRelay(current) }.getOrNull() ?: return@launch
            fullRelayFor = gameId
            when {
                _viewingGame.value?.gameId == gameId -> _viewingGame.value = expanded
                _snapshot.value?.lotteGame?.gameId == gameId ->
                    _snapshot.value = _snapshot.value?.copy(lotteGame = expanded)
                _snapshot.value?.nextLotteGame?.gameId == gameId ->
                    _snapshot.value = _snapshot.value?.copy(nextLotteGame = expanded)
            }
        }
    }

    private fun isTodayLotteGame(gameId: String, snap: LiveSnapshot?): Boolean {
        if (snap == null) return false
        if (snap.lotteGame?.gameId == gameId) return true
        return snap.todayLotteGames.any { it.gameId == gameId }
    }

    private suspend fun refreshViewingGame(snap: LiveSnapshot) {
        val id = viewingGameId ?: return
        if (isTodayLotteGame(id, snap)) {
            viewingGameId = null
            _viewingGame.value = null
            return
        }
        val existing = _viewingGame.value
        if (existing != null && existing.gameId == id &&
            (existing.status == GameStatus.ENDED || existing.status == GameStatus.CANCELED)
        ) {
            return
        }
        runCatching { repo.fetchGameDetail(id) }.onSuccess { g ->
            if (g != null) _viewingGame.value = g
        }
    }

    fun selectDate(date: LocalDate) {
        followKboToday = date == kboToday()
        _selectedDate.value = date
        val ym = YearMonth.from(date)
        if (ym != _calendarMonth.value) loadMonthGames(ym)
        loadGamesForDate(date)
    }

    fun selectCalendarMonth(month: YearMonth) {
        _calendarMonth.value = month
        loadMonthGames(month)
    }

    fun loadGamesForDate(date: LocalDate) { calendarController.loadDay(date) }
    fun loadMonthGames(month: YearMonth) { calendarController.loadMonth(month) }
    private suspend fun fetchDayGames(date: LocalDate) { calendarController.loadDay(date).join() }

    fun setResultsTeam(code: String) {
        _resultsTeamCode.value = code
        if (code.isNotBlank()) ensureSeasonGames()
    }

    fun ensureSeasonGames() {
        if (seasonJob?.isActive == true) return
        if (_seasonGames.value.isNotEmpty() && !seasonFetchFailed) return
        seasonJob = viewModelScope.launch {
            _seasonLoading.value = true
            val year = kboToday().let { if (it.monthValue < 3) it.year - 1 else it.year }
            runCatching { repo.fetchGamesForSeason(year) }
                .onSuccess {
                    _seasonGames.value = it
                    seasonFetchFailed = false
                }
                .onFailure {
                    seasonFetchFailed = true
                    _error.value = it.message ?: "시즌 일정을 불러오지 못했습니다."
                }
            _seasonLoading.value = false
        }
    }

    fun openTeamHistory(code: String) {
        val c = code.ifBlank { LOTTE_TEAM_CODE }
        _overlayTeamCode.value = c
        if (c == LOTTE_TEAM_CODE && _teamCard.value != null) {
            _overlayTeamCard.value = _teamCard.value
            return
        }
        viewModelScope.launch {
            runCatching { repo.fetchTeamCard(teamKeuboSlug(c)) }
                .onSuccess { card ->
                    _overlayTeamCard.value = card
                    if (c == LOTTE_TEAM_CODE) _teamCard.value = card
                }
        }
    }

    fun openLeadersForTeam(code: String) {
        _overlayTeamCode.value = code.ifBlank { LOTTE_TEAM_CODE }
        ensureLeaders()
    }

    fun openEntrySmart() = openEntryForTeam(LOTTE_TEAM_CODE)

    fun openEntryForTeam(teamCode: String) {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        _overlayTeamCode.value = code
        entryController.open(code)
    }
    fun selectEntryDate(date: LocalDate) = entryController.select(date)
    fun loadEntryForDate(date: LocalDate) { entryController.load(date) }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.store.setThemeMode(mode.name) }
    }

    fun loadPlayerDetail(slot: LineupSlot, gameId: String?) = playerController.loadPlayerDetail(slot, gameId)

    fun clearPlayerDetail() = playerController.clearPlayerDetail()

    private fun refreshWeatherFromSnapshot(snap: LiveSnapshot?) {
        val stadium = snap?.lotteGame?.stadium
            ?: snap?.nextLotteGame?.stadium
            ?: teamHomeStadiumName(snap?.myTeamCode ?: currentTeamCode())
        viewModelScope.launch {
            runCatching { repo.fetchStadiumWeather(stadium, snap?.myTeamCode ?: currentTeamCode()) }
                .onSuccess {
                    _weather.value = it
                    repo.store.setWeather(it)
                }
        }
    }

    private fun syncTodayGamesFromSnapshot(snap: LiveSnapshot) {
        val liveById = buildMap {
            snap.lotteGame?.let { put(it.gameId, it.toResultsMini()) }
            snap.otherGames.forEach { g ->
                put(
                    g.gameId,
                    if (g.status != GameStatus.CANCELED) g else g.copy(statusText = g.cancelLabel),
                )
            }
        }
        if (liveById.isEmpty()) return
        val current = _dayGames.value
        if (current.isEmpty()) return
        _dayGames.value = sortMyTeamFirst(
            current.map { g -> liveById[g.gameId]?.let { live -> g.mergeLive(live) } ?: g },
            snap.myTeamCode.ifBlank { currentTeamCode() },
        )
    }

    private fun LotteGameInfo.toResultsMini(): MiniGame {
        val name = focusName()
        val code = focusTeamCode.ifBlank { LOTTE_TEAM_CODE }
        val logo = lotteLogoUrl.ifBlank { teamLogoUrl(code) }
        return MiniGame(
            gameId = gameId,
            homeName = if (isHome) name else opponentName,
            awayName = if (isHome) opponentName else name,
            homeScore = if (isHome) lotteScore else opponentScore,
            awayScore = if (isHome) opponentScore else lotteScore,
            status = status,
            statusText = when {
                status == GameStatus.CANCELED -> cancelLabel
                status == GameStatus.LIVE -> inningLabel.ifBlank { statusText }
                else -> statusText
            },
            cancelReason = cancelReason,
            stadium = stadium,
            startTime = startTime,
            homeLogoUrl = if (isHome) logo else opponentLogoUrl,
            awayLogoUrl = if (isHome) opponentLogoUrl else logo,
            homeStarter = if (isHome) lotteStartingPitcher else opponentStartingPitcher,
            awayStarter = if (isHome) opponentStartingPitcher else lotteStartingPitcher,
            broadChannel = broadChannel,
            winPitcherName = winPitcherName,
            losePitcherName = losePitcherName,
            gameDate = gameDate,
            homeTeamCode = if (isHome) code else opponentCode,
            awayTeamCode = if (isHome) opponentCode else code,
            doubleHeaderNo = doubleHeaderNo,
        )
    }

    private fun MiniGame.mergeLive(live: MiniGame): MiniGame {
        val mergedStatusText = when {
            live.status == GameStatus.LIVE && live.statusText.contains("회") -> live.statusText
            live.status == GameStatus.LIVE && statusText.contains("회") -> statusText
            live.statusText.isNotBlank() && live.statusText != "진행 중" -> live.statusText
            else -> statusText
        }
        return copy(
            homeScore = live.homeScore,
            awayScore = live.awayScore,
            status = live.status,
            statusText = mergedStatusText,
            cancelReason = live.cancelReason.ifBlank { cancelReason },
            homeStarter = live.homeStarter.ifBlank { homeStarter },
            awayStarter = live.awayStarter.ifBlank { awayStarter },
            winPitcherName = live.winPitcherName.ifBlank { winPitcherName },
            losePitcherName = live.losePitcherName.ifBlank { losePitcherName },
            broadChannel = live.broadChannel.ifBlank { broadChannel },
            doubleHeaderNo = if (live.doubleHeaderNo > 0) live.doubleHeaderNo else doubleHeaderNo,
            isSuspended = live.isSuspended,
            resumeTime = live.resumeTime.ifBlank { resumeTime },
        )
    }

    private fun currentTeamCode(): String =
        myTeamCode.value.ifBlank { _snapshot.value?.myTeamCode.orEmpty().ifBlank { LOTTE_TEAM_CODE } }

    companion object {
        const val POLL_LIVE_SEC = 10
        const val POLL_IDLE_SEC = 45

        fun sortMyTeamFirst(games: List<MiniGame>, teamCode: String = LOTTE_TEAM_CODE): List<MiniGame> =
            games.sortedByDescending { it.involvesTeam(teamCode) }

        fun sortLotteFirst(games: List<MiniGame>): List<MiniGame> = sortMyTeamFirst(games, LOTTE_TEAM_CODE)
    }
}
