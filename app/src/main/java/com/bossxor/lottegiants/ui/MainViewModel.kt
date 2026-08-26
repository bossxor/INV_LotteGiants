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
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.domain.teamKeuboSlug
import com.bossxor.lottegiants.live.WearBridge
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _dayGames = MutableStateFlow<List<MiniGame>>(emptyList())
    val dayGames: StateFlow<List<MiniGame>> = _dayGames.asStateFlow()

    private val _dayGamesLoading = MutableStateFlow(false)
    val dayGamesLoading: StateFlow<Boolean> = _dayGamesLoading.asStateFlow()

    private val _selectedDate = MutableStateFlow(kboToday())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _monthGames = MutableStateFlow<List<MiniGame>>(emptyList())
    val monthGames: StateFlow<List<MiniGame>> = _monthGames.asStateFlow()

    private val _calendarMonth = MutableStateFlow(YearMonth.from(kboToday()))
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    private val _weather = MutableStateFlow<StadiumWeather?>(null)
    val weather: StateFlow<StadiumWeather?> = _weather.asStateFlow()

    private val _entryDate = MutableStateFlow(kboToday())
    val entryDate: StateFlow<LocalDate> = _entryDate.asStateFlow()

    private val _dayEntry = MutableStateFlow<DayEntryChanges?>(null)
    val dayEntry: StateFlow<DayEntryChanges?> = _dayEntry.asStateFlow()

    private val _entryLoading = MutableStateFlow(false)
    val entryLoading: StateFlow<Boolean> = _entryLoading.asStateFlow()

    private val _entryChangeDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val entryChangeDates: StateFlow<Set<LocalDate>> = _entryChangeDates.asStateFlow()

    private val _recentMoves = MutableStateFlow<List<RosterMove>>(emptyList())
    val recentMoves: StateFlow<List<RosterMove>> = _recentMoves.asStateFlow()

    private val _teamCard = MutableStateFlow<LotteTeamCard?>(null)
    val teamCard: StateFlow<LotteTeamCard?> = _teamCard.asStateFlow()

    private val _batterLeaders = MutableStateFlow<List<LeaderPlayer>>(emptyList())
    val batterLeaders: StateFlow<List<LeaderPlayer>> = _batterLeaders.asStateFlow()

    private val _pitcherLeaders = MutableStateFlow<List<LeaderPlayer>>(emptyList())
    val pitcherLeaders: StateFlow<List<LeaderPlayer>> = _pitcherLeaders.asStateFlow()

    private val _playerDetail = MutableStateFlow<PlayerDetail?>(null)
    val playerDetail: StateFlow<PlayerDetail?> = _playerDetail.asStateFlow()

    private val _playerLoading = MutableStateFlow(false)
    val playerLoading: StateFlow<Boolean> = _playerLoading.asStateFlow()

    val favoritePlayers: StateFlow<List<FavoritePlayer>> = repo.store.favoritePlayersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteCodes: StateFlow<Set<String>> = favoritePlayers
        .map { list -> list.map { it.code }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val themeMode: StateFlow<ThemeMode> = repo.store.themeModeFlow
        .map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

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

    private val _overlayTeamCode = MutableStateFlow(LOTTE_TEAM_CODE)
    val overlayTeamCode: StateFlow<String> = _overlayTeamCode.asStateFlow()

    private val _overlayTeamCard = MutableStateFlow<LotteTeamCard?>(null)
    val overlayTeamCard: StateFlow<LotteTeamCard?> = _overlayTeamCard.asStateFlow()

    private var viewingGameId: String? = null

    private var pollJob: Job? = null
    private var dayGamesJob: Job? = null
    private var monthJob: Job? = null
    private var entryJob: Job? = null
    private var seasonJob: Job? = null
    private var seasonFetchFailed = false

    init {
        viewModelScope.launch {
            _snapshot.value = repo.store.loadSnapshot()
            refreshWeatherFromSnapshot(_snapshot.value)
        }
        viewModelScope.launch {
            runCatching { repo.fetchStandings() }.onSuccess { _standings.value = it }
        }
        viewModelScope.launch {
            runCatching { repo.fetchLotteTeamCard() }.onSuccess { _teamCard.value = it }
        }
        viewModelScope.launch {
            runCatching { repo.fetchLeaders(false) }.onSuccess { _batterLeaders.value = it }
            runCatching { repo.fetchLeaders(true) }.onSuccess { _pitcherLeaders.value = it }
        }
        viewModelScope.launch {
            runCatching { repo.fetchRecentRosterMoves(7) }.onSuccess { _recentMoves.value = it }
        }
        loadGamesForDate(kboToday())
        loadMonthGames(YearMonth.from(kboToday()))
        openEntrySmart()
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                for (left in POLL_LIVE_SEC downTo 1) {
                    _secondsUntilRefresh.value = left
                    delay(1_000L)
                    if (!isActive) return@launch
                }
                _secondsUntilRefresh.value = 0
            }
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
                refreshOnce()
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
                runCatching { repo.fetchLeaders(false) }.onSuccess { _batterLeaders.value = it }
                runCatching { repo.fetchLeaders(true) }.onSuccess { _pitcherLeaders.value = it }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshDayGames() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                dayGamesJob?.cancel()
                monthJob?.cancel()
                fetchDayGames(_selectedDate.value)
                fetchMonthGames(_calendarMonth.value)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadPlayerByCode(playerCode: String, name: String = "") {
        if (playerCode.isBlank()) return
        viewModelScope.launch {
            _playerLoading.value = true
            _playerDetail.value = null
            val slot = LineupSlot(
                batOrder = 0,
                name = name,
                position = "",
                playerCode = playerCode,
            )
            runCatching { repo.fetchPlayerDetail(playerCode, slot, null) }
                .onSuccess { _playerDetail.value = it }
                .onFailure {
                    _playerDetail.value = PlayerDetail(
                        playerCode = playerCode,
                        name = name,
                        photoUrl = playerPhotoUrl(playerCode),
                    )
                }
            _playerLoading.value = false
        }
    }

    fun loadPlayerFromLeader(player: LeaderPlayer) {
        if (player.playerCode.isBlank()) {
            _playerDetail.value = player.toDetailSeed()
            return
        }
        viewModelScope.launch {
            _playerLoading.value = true
            val seeded = player.toDetailSeed()
            _playerDetail.value = seeded
            val slot = LineupSlot(
                batOrder = 0,
                name = player.name,
                position = "",
                playerCode = player.playerCode,
            )
            runCatching { repo.fetchPlayerDetail(player.playerCode, slot, null) }
                .onSuccess { fetched ->
                    _playerDetail.value = seeded.copy(
                        backNumber = fetched.backNumber.ifBlank { seeded.backNumber },
                        hitType = fetched.hitType.ifBlank { seeded.hitType },
                        position = fetched.position.ifBlank { seeded.position },
                        birth = fetched.birth.ifBlank { seeded.birth },
                        heightCm = fetched.heightCm.ifBlank { seeded.heightCm },
                        weightKg = fetched.weightKg.ifBlank { seeded.weightKg },
                        photoUrl = fetched.photoUrl.ifBlank { seeded.photoUrl },
                        seasonAvg = seeded.seasonAvg.ifBlank { fetched.seasonAvg },
                        seasonGames = if (seeded.seasonGames > 0) seeded.seasonGames else fetched.seasonGames,
                        seasonHits = if (seeded.seasonHits > 0) seeded.seasonHits else fetched.seasonHits,
                        seasonHr = if (seeded.seasonHr > 0) seeded.seasonHr else fetched.seasonHr,
                        seasonRbi = if (seeded.seasonRbi > 0) seeded.seasonRbi else fetched.seasonRbi,
                        seasonObp = seeded.seasonObp.ifBlank { fetched.seasonObp },
                        seasonOps = seeded.seasonOps.ifBlank { fetched.seasonOps },
                        seasonSlg = seeded.seasonSlg.ifBlank { fetched.seasonSlg },
                        seasonSb = if (seeded.seasonSb > 0) seeded.seasonSb else fetched.seasonSb,
                        pitcherEra = seeded.pitcherEra.ifBlank { fetched.pitcherEra },
                        pitcherWins = if (seeded.pitcherWins > 0) seeded.pitcherWins else fetched.pitcherWins,
                        pitcherLosses = if (seeded.pitcherLosses > 0) seeded.pitcherLosses else fetched.pitcherLosses,
                        pitcherSo = if (seeded.pitcherSo > 0) seeded.pitcherSo else fetched.pitcherSo,
                        pitcherInn = seeded.pitcherInn.ifBlank { fetched.pitcherInn },
                        pitcherSaves = if (seeded.pitcherSaves > 0) seeded.pitcherSaves else fetched.pitcherSaves,
                        pitcherHolds = if (seeded.pitcherHolds > 0) seeded.pitcherHolds else fetched.pitcherHolds,
                        pitcherWhip = seeded.pitcherWhip.ifBlank { fetched.pitcherWhip },
                        isPitcher = seeded.isPitcher || fetched.isPitcher,
                    )
                }
            _playerLoading.value = false
        }
    }

    fun loadPitcherDetail(p: PitcherLine) {
        if (p.playerCode.isBlank()) return
        viewModelScope.launch {
            _playerLoading.value = true
            _playerDetail.value = PlayerDetail(
                playerCode = p.playerCode,
                name = p.name,
                backNumber = p.backNumber,
                isPitcher = true,
                todayLine = listOfNotNull(
                    p.innings.takeIf { it.isNotBlank() }?.let { "${it}이닝" },
                    "${p.strikeouts}K",
                    "${p.hits}H",
                ).joinToString(" · "),
                photoUrl = playerPhotoUrl(p.playerCode),
            )
            val slot = LineupSlot(
                batOrder = 0,
                name = p.name,
                position = "투수",
                playerCode = p.playerCode,
                backNumber = p.backNumber,
                isPitcher = true,
            )
            runCatching { repo.fetchPlayerDetail(p.playerCode, slot, _snapshot.value?.lotteGame?.gameId) }
                .onSuccess { fetched ->
                    _playerDetail.value = fetched.copy(
                        isPitcher = true,
                        todayLine = _playerDetail.value?.todayLine.orEmpty().ifBlank { fetched.todayLine },
                    )
                }
            _playerLoading.value = false
        }
    }

    fun toggleFavorite(code: String, name: String = "", team: String = "") {
        if (code.isBlank()) return
        viewModelScope.launch { repo.store.toggleFavorite(code, name, team) }
    }

    fun removeFavorite(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch { repo.store.removeFavorite(code) }
    }

    suspend fun refreshOnce() {
        runCatching { repo.refreshSnapshot() }
            .onSuccess {
                _snapshot.value = it
                _error.value = null
                WidgetUpdater.updateAll(getApplication())
                WearBridge.syncSnapshot(getApplication(), it)
                if (_selectedDate.value == kboToday()) {
                    syncTodayGamesFromSnapshot(it)
                }
                refreshWeatherFromSnapshot(it)
                refreshViewingGame(it)
            }
            .onFailure { _error.value = it.message }
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
        _selectedDate.value = date
        val ym = YearMonth.from(date)
        if (ym != _calendarMonth.value) loadMonthGames(ym)
        loadGamesForDate(date)
    }

    fun selectCalendarMonth(month: YearMonth) {
        _calendarMonth.value = month
        loadMonthGames(month)
    }

    fun loadGamesForDate(date: LocalDate) {
        dayGamesJob?.cancel()
        dayGamesJob = viewModelScope.launch { fetchDayGames(date) }
    }

    fun loadMonthGames(month: YearMonth) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch { fetchMonthGames(month) }
    }

    private suspend fun fetchDayGames(date: LocalDate) {
        _dayGamesLoading.value = true
        try {
            runCatching { repo.fetchGamesForDate(date) }
                .onSuccess { _dayGames.value = sortLotteFirst(it) }
        } finally {
            _dayGamesLoading.value = false
        }
    }

    private suspend fun fetchMonthGames(month: YearMonth) {
        _calendarMonth.value = month
        runCatching { repo.fetchGamesForMonth(month) }
            .onSuccess { _monthGames.value = it }
    }

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
                .onFailure { seasonFetchFailed = true }
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
    }

    fun openEntrySmart() = openEntryForTeam(LOTTE_TEAM_CODE)

    fun openEntryForTeam(teamCode: String) {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        if (_overlayTeamCode.value != code) {
            _overlayTeamCode.value = code
            _entryChangeDates.value = emptySet()
            _dayEntry.value = null
            _recentMoves.value = emptyList()
        } else {
            _overlayTeamCode.value = code
        }
        viewModelScope.launch {
            _entryLoading.value = true
            runCatching { repo.fetchRecentRosterMoves(7, code) }.onSuccess { _recentMoves.value = it }
            val date = runCatching { repo.findLatestEntryDate(21, code) }.getOrDefault(LocalDate.now())
            _entryDate.value = date
            loadEntryForDate(date)
            prefetchEntryDates(YearMonth.from(date))
        }
    }

    fun selectEntryDate(date: LocalDate) {
        _entryDate.value = date
        loadEntryForDate(date)
        val ym = YearMonth.from(date)
        if (_entryChangeDates.value.none { YearMonth.from(it) == ym }) {
            prefetchEntryDates(ym)
        }
    }

    fun loadEntryForDate(date: LocalDate) {
        val code = _overlayTeamCode.value.ifBlank { LOTTE_TEAM_CODE }
        entryJob?.cancel()
        entryJob = viewModelScope.launch {
            _entryLoading.value = true
            runCatching { repo.fetchDayEntryChanges(date, teamCode = code) }
                .onSuccess { _dayEntry.value = it }
                .onFailure { _dayEntry.value = DayEntryChanges(date = date.toString()) }
            _entryLoading.value = false
        }
    }

    private fun prefetchEntryDates(month: YearMonth) {
        val code = _overlayTeamCode.value.ifBlank { LOTTE_TEAM_CODE }
        viewModelScope.launch {
            runCatching { repo.fetchEntryChangeDates(month, code) }
                .onSuccess { hits ->
                    _entryChangeDates.value = _entryChangeDates.value + hits
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.store.setThemeMode(mode.name) }
    }

    fun loadPlayerDetail(slot: LineupSlot, gameId: String?) {
        viewModelScope.launch {
            _playerLoading.value = true
            _playerDetail.value = null
            runCatching { repo.fetchPlayerDetail(slot.playerCode, slot, gameId) }
                .onSuccess { _playerDetail.value = it }
                .onFailure {
                    _playerDetail.value = PlayerDetail(
                        playerCode = slot.playerCode,
                        name = slot.name,
                        backNumber = slot.backNumber,
                        hitType = slot.hitType,
                        position = slot.position,
                        isPitcher = slot.isPitcher ||
                            com.bossxor.lottegiants.domain.isPitcherPosition(slot.position),
                        seasonAvg = slot.seasonAvg?.let { a -> String.format("%.3f", a) }.orEmpty(),
                        todayLine = "${slot.todayHits}/${slot.todayAtBats}",
                        photoUrl = if (slot.playerCode.isNotBlank()) playerPhotoUrl(slot.playerCode) else "",
                    )
                }
            _playerLoading.value = false
        }
    }

    fun clearPlayerDetail() {
        _playerDetail.value = null
    }

    private fun refreshWeatherFromSnapshot(snap: LiveSnapshot?) {
        val stadium = snap?.lotteGame?.stadium
            ?: snap?.nextLotteGame?.stadium
            ?: "사직"
        viewModelScope.launch {
            runCatching { repo.fetchStadiumWeather(stadium) }
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
        _dayGames.value = sortLotteFirst(
            current.map { g -> liveById[g.gameId]?.let { live -> g.mergeLive(live) } ?: g },
        )
    }

    private fun LotteGameInfo.toResultsMini(): MiniGame = MiniGame(
        gameId = gameId,
        homeName = if (isHome) "롯데" else opponentName,
        awayName = if (isHome) opponentName else "롯데",
        homeScore = if (isHome) lotteScore else opponentScore,
        awayScore = if (isHome) opponentScore else lotteScore,
        status = status,
        statusText = if (status == GameStatus.CANCELED) cancelLabel else statusText,
        cancelReason = cancelReason,
        stadium = stadium,
        startTime = startTime,
        homeLogoUrl = if (isHome) lotteLogoUrl.ifBlank { com.bossxor.lottegiants.domain.LOTTE_LOGO_URL } else opponentLogoUrl,
        awayLogoUrl = if (isHome) opponentLogoUrl else lotteLogoUrl.ifBlank { com.bossxor.lottegiants.domain.LOTTE_LOGO_URL },
        homeStarter = if (isHome) lotteStartingPitcher else opponentStartingPitcher,
        awayStarter = if (isHome) opponentStartingPitcher else lotteStartingPitcher,
        broadChannel = broadChannel,
        winPitcherName = winPitcherName,
        losePitcherName = losePitcherName,
        gameDate = gameDate,
        homeTeamCode = if (isHome) LOTTE_TEAM_CODE else opponentCode,
        awayTeamCode = if (isHome) opponentCode else LOTTE_TEAM_CODE,
        doubleHeaderNo = doubleHeaderNo,
    )

    private fun MiniGame.mergeLive(live: MiniGame): MiniGame = copy(
        homeScore = live.homeScore,
        awayScore = live.awayScore,
        status = live.status,
        statusText = live.statusText,
        cancelReason = live.cancelReason.ifBlank { cancelReason },
        homeStarter = live.homeStarter.ifBlank { homeStarter },
        awayStarter = live.awayStarter.ifBlank { awayStarter },
        winPitcherName = live.winPitcherName.ifBlank { winPitcherName },
        losePitcherName = live.losePitcherName.ifBlank { losePitcherName },
        broadChannel = live.broadChannel.ifBlank { broadChannel },
        doubleHeaderNo = if (live.doubleHeaderNo > 0) live.doubleHeaderNo else doubleHeaderNo,
    )

    companion object {
        const val POLL_LIVE_SEC = 10

        fun sortLotteFirst(games: List<MiniGame>): List<MiniGame> =
            games.sortedByDescending {
                it.homeTeamCode == LOTTE_TEAM_CODE || it.awayTeamCode == LOTTE_TEAM_CODE ||
                    it.homeName.contains("롯데") || it.awayName.contains("롯데")
            }
    }
}

private fun LeaderPlayer.toDetailSeed(): PlayerDetail =
    if (isPitcher) {
        PlayerDetail(
            playerCode = playerCode,
            name = name,
            seasonGames = games,
            pitcherEra = era,
            pitcherWins = wins,
            pitcherLosses = losses,
            pitcherSo = so,
            pitcherInn = ip,
            pitcherSaves = saves,
            pitcherHolds = holds,
            pitcherWhip = whip,
            isPitcher = true,
            photoUrl = if (playerCode.isNotBlank()) playerPhotoUrl(playerCode) else "",
        )
    } else {
        PlayerDetail(
            playerCode = playerCode,
            name = name,
            seasonAvg = avg,
            seasonGames = games,
            seasonHits = hits,
            seasonHr = hr,
            seasonRbi = rbi,
            seasonObp = obp,
            seasonOps = ops,
            seasonSlg = slg,
            seasonSb = sb,
            isPitcher = false,
            photoUrl = if (playerCode.isNotBlank()) playerPhotoUrl(playerCode) else "",
        )
    }
