package com.bossxor.lottegiants

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bossxor.lottegiants.BuildConfig
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.InstallResult
import com.bossxor.lottegiants.data.UpdateCheckResult
import com.bossxor.lottegiants.data.UpdateChecker
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.ThemeMode
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.ui.LotteGiantsTheme
import com.bossxor.lottegiants.ui.MainViewModel
import com.bossxor.lottegiants.ui.screens.EntryBoardScreen
import com.bossxor.lottegiants.ui.screens.LeadersScreen
import com.bossxor.lottegiants.ui.screens.LiveScreen
import com.bossxor.lottegiants.ui.screens.PlayerDetailSheet
import com.bossxor.lottegiants.ui.screens.ResultsScreen
import com.bossxor.lottegiants.ui.screens.SettingsScreen
import com.bossxor.lottegiants.ui.screens.StandingsScreen
import com.bossxor.lottegiants.ui.screens.TeamHistoryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Overlay { None, TeamHistory, EntryBoard, Leaders }

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val openTabExtra = mutableStateOf<String?>(null)
    private val openTabNonce = mutableIntStateOf(0)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotifIfNeeded()
        openTabExtra.value = intent.getStringExtra(EXTRA_OPEN_TAB)

        setContent {
            val themeMode by vm.themeMode.collectAsState()
            LotteGiantsTheme(themeMode = themeMode) {
                val snapshot by vm.snapshot.collectAsState()
                val standings by vm.standings.collectAsState()
                val error by vm.error.collectAsState()
                val dayGames by vm.dayGames.collectAsState()
                val dayGamesLoading by vm.dayGamesLoading.collectAsState()
                val selectedDate by vm.selectedDate.collectAsState()
                val secondsUntilRefresh by vm.secondsUntilRefresh.collectAsState()
                val isRefreshing by vm.isRefreshing.collectAsState()
                val monthGames by vm.monthGames.collectAsState()
                val calendarMonth by vm.calendarMonth.collectAsState()
                val weather by vm.weather.collectAsState()
                val entryDate by vm.entryDate.collectAsState()
                val dayEntry by vm.dayEntry.collectAsState()
                val entryLoading by vm.entryLoading.collectAsState()
                val recentMoves by vm.recentMoves.collectAsState()
                val entryChangeDates by vm.entryChangeDates.collectAsState()
                val teamCard by vm.teamCard.collectAsState()
                val batterLeaders by vm.batterLeaders.collectAsState()
                val pitcherLeaders by vm.pitcherLeaders.collectAsState()
                val playerDetail by vm.playerDetail.collectAsState()
                val playerLoading by vm.playerLoading.collectAsState()
                val favoriteCodes by vm.favoriteCodes.collectAsState()
                val favoritePlayers by vm.favoritePlayers.collectAsState()
                val viewingGame by vm.viewingGame.collectAsState()
                val viewingLoading by vm.viewingLoading.collectAsState()
                val scope = rememberCoroutineScope()
                var updateStatus by remember { mutableStateOf<String?>(null) }
                var autoUpdateRan by remember { mutableStateOf(false) }
                var pendingNeedsResume by remember { mutableStateOf(false) }

                fun handleInstallResult(result: InstallResult) {
                    when (result) {
                        is InstallResult.Launched -> {
                            pendingNeedsResume = false
                            updateStatus = "설치 화면에서 「설치」를 눌러주세요."
                        }
                        is InstallResult.NeedsPermission -> {
                            pendingNeedsResume = true
                            updateStatus = null
                            Toast.makeText(
                                this@MainActivity,
                                "「출처를 알 수 없는 앱 설치」를 허용한 뒤 앱으로 돌아오면 이어서 설치합니다.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        is InstallResult.DownloadFailed -> {
                            pendingNeedsResume = false
                            updateStatus = null
                            Toast.makeText(
                                this@MainActivity,
                                result.message,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> {
                                vm.startPolling()
                                if (snapshot?.lotteGame?.status == GameStatus.LIVE) {
                                    LiveScoreService.start(this@MainActivity)
                                }
                                if (!autoUpdateRan) {
                                    autoUpdateRan = true
                                    scope.launch {
                                        val store = GiantsRepository.get(this@MainActivity).store
                                        updateStatus = "업데이트 확인 중…"
                                        val result = withContext(Dispatchers.IO) {
                                            UpdateChecker.runAutoUpdate(
                                                this@MainActivity,
                                                store,
                                            ) { downloaded, total ->
                                                if (total > 0L) {
                                                    val pct = ((downloaded * 100) / total).toInt()
                                                    updateStatus = "업데이트 다운로드 중… $pct%"
                                                }
                                            }
                                        }
                                        if (result == null) {
                                            updateStatus = null
                                            return@launch
                                        }
                                        handleInstallResult(result)
                                    }
                                }
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                if (!pendingNeedsResume) return@LifecycleEventObserver
                                scope.launch {
                                    val store = GiantsRepository.get(this@MainActivity).store
                                    withContext(Dispatchers.IO) {
                                        UpdateChecker.syncPendingUpdateState(
                                            this@MainActivity,
                                            store,
                                        )
                                    }
                                    val pendingPath = withContext(Dispatchers.IO) {
                                        store.pendingUpdateApkPath()
                                    }
                                    val pendingCode = withContext(Dispatchers.IO) {
                                        store.pendingUpdateVersionCode()
                                    }
                                    if (pendingPath.isBlank() ||
                                        pendingCode <= BuildConfig.VERSION_CODE ||
                                        !UpdateChecker.canInstallPackages(this@MainActivity)
                                    ) {
                                        pendingNeedsResume = false
                                        return@launch
                                    }
                                    pendingNeedsResume = false
                                    updateStatus = "업데이트를 이어서 설치합니다…"
                                    val result = withContext(Dispatchers.IO) {
                                        UpdateChecker.resumePendingInstall(
                                            this@MainActivity,
                                            store,
                                        )
                                    }
                                    handleInstallResult(result)
                                }
                            }
                            Lifecycle.Event.ON_STOP -> vm.stopPolling()
                            else -> {}
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                updateStatus?.let { status ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("업데이트") },
                        text = { Text(status) },
                        confirmButton = {
                            Text("잠시만요…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }

                AppScaffold(
                    initialTab = tabFromIntent(openTabExtra.value),
                    openEntry = isEntryIntent(openTabExtra.value),
                    openEntryNonce = openTabNonce.intValue,
                    snapshot = snapshot,
                    standings = standings,
                    error = error,
                    loading = snapshot == null && error == null,
                    dayGames = dayGames,
                    dayGamesLoading = dayGamesLoading,
                    selectedDate = selectedDate,
                    onSelectDate = vm::selectDate,
                    secondsUntilRefresh = secondsUntilRefresh,
                    isRefreshing = isRefreshing,
                    onRefresh = vm::refreshNow,
                    onRefreshStandings = vm::refreshStandings,
                    onRefreshDayGames = vm::refreshDayGames,
                    monthGames = monthGames,
                    calendarMonth = calendarMonth,
                    onSelectMonth = vm::selectCalendarMonth,
                    weather = weather,
                    entryDate = entryDate,
                    dayEntry = dayEntry,
                    entryLoading = entryLoading,
                    recentMoves = recentMoves,
                    entryChangeDates = entryChangeDates,
                    onSelectEntryDate = vm::selectEntryDate,
                    onOpenEntrySmart = vm::openEntrySmart,
                    teamCard = teamCard,
                    batterLeaders = batterLeaders,
                    pitcherLeaders = pitcherLeaders,
                    themeMode = themeMode,
                    onThemeModeChange = vm::setThemeMode,
                    playerDetail = playerDetail,
                    playerLoading = playerLoading,
                    favoriteCodes = favoriteCodes,
                    favoritePlayers = favoritePlayers,
                    onPlayerClick = { slot ->
                        vm.loadPlayerDetail(slot, viewingGame?.gameId ?: snapshot?.lotteGame?.gameId)
                    },
                    onPitcherClick = { p -> vm.loadPitcherDetail(p) },
                    onLeaderPlayerClick = { p -> vm.loadPlayerFromLeader(p) },
                    onToggleFavorite = { code, name, team -> vm.toggleFavorite(code, name, team) },
                    onRemoveFavorite = vm::removeFavorite,
                    onClearPlayer = vm::clearPlayerDetail,
                    onSelectLiveGame = vm::selectLiveGame,
                    viewingGame = viewingGame,
                    viewingLoading = viewingLoading,
                    onOpenGame = vm::openGame,
                    onBackToLotte = vm::backToLotte,
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTabExtra.value = intent.getStringExtra(EXTRA_OPEN_TAB)
        openTabNonce.intValue++
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"

        fun tabFromIntent(value: String?): Int = when (value?.lowercase()) {
            "results", "result", "1" -> 1
            "standings", "standing", "2" -> 2
            "settings", "3" -> 3
            else -> 0
        }

        fun isEntryIntent(value: String?): Boolean =
            value?.lowercase() in setOf("entry", "roster")
    }
}

@Composable
private fun AppScaffold(
    initialTab: Int,
    openEntry: Boolean,
    openEntryNonce: Int,
    snapshot: com.bossxor.lottegiants.domain.LiveSnapshot?,
    standings: List<com.bossxor.lottegiants.domain.TeamStanding>,
    error: String?,
    loading: Boolean,
    dayGames: List<com.bossxor.lottegiants.domain.MiniGame>,
    dayGamesLoading: Boolean,
    selectedDate: java.time.LocalDate,
    onSelectDate: (java.time.LocalDate) -> Unit,
    secondsUntilRefresh: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRefreshStandings: () -> Unit,
    onRefreshDayGames: () -> Unit,
    monthGames: List<com.bossxor.lottegiants.domain.MiniGame>,
    calendarMonth: java.time.YearMonth,
    onSelectMonth: (java.time.YearMonth) -> Unit,
    weather: com.bossxor.lottegiants.domain.StadiumWeather?,
    entryDate: java.time.LocalDate,
    dayEntry: com.bossxor.lottegiants.domain.DayEntryChanges?,
    entryLoading: Boolean,
    recentMoves: List<RosterMove>,
    entryChangeDates: Set<java.time.LocalDate>,
    onSelectEntryDate: (java.time.LocalDate) -> Unit,
    onOpenEntrySmart: () -> Unit,
    teamCard: LotteTeamCard?,
    batterLeaders: List<LeaderPlayer>,
    pitcherLeaders: List<LeaderPlayer>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    playerDetail: com.bossxor.lottegiants.domain.PlayerDetail?,
    playerLoading: Boolean,
    favoriteCodes: Set<String>,
    favoritePlayers: List<com.bossxor.lottegiants.domain.FavoritePlayer>,
    onPlayerClick: (LineupSlot) -> Unit,
    onPitcherClick: (com.bossxor.lottegiants.domain.PitcherLine) -> Unit,
    onLeaderPlayerClick: (LeaderPlayer) -> Unit,
    onToggleFavorite: (String, String, String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onClearPlayer: () -> Unit,
    onSelectLiveGame: (String) -> Unit,
    viewingGame: LotteGameInfo?,
    viewingLoading: Boolean,
    onOpenGame: (String) -> Unit,
    onBackToLotte: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { GiantsRepository.get(context).store }
    var tab by remember { mutableIntStateOf(initialTab) }
    var overlay by remember { mutableStateOf(if (openEntry) Overlay.EntryBoard else Overlay.None) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var showOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!store.isOnboardingDone()) showOnboarding = true
    }
    LaunchedEffect(initialTab) { tab = initialTab }
    LaunchedEffect(openEntry, openEntryNonce) {
        if (openEntry) {
            overlay = Overlay.EntryBoard
            onOpenEntrySmart()
        }
    }

    BackHandler {
        when {
            showPlayerSheet -> {
                showPlayerSheet = false
                onClearPlayer()
            }
            overlay != Overlay.None -> overlay = Overlay.None
            viewingGame != null -> onBackToLotte()
            tab != 0 -> tab = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    onExit()
                } else {
                    lastBackAt = now
                    Toast.makeText(context, "한 번 더 누르면 종료", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("사직스코어 안내", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "알림·배터리 예외·홈 화면 위젯을 켜 두면 경기 중 스코어를 더 빠르게 볼 수 있습니다.\n설정에서 언제든 변경할 수 있습니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            store.setOnboardingDone(true)
                            showOnboarding = false
                        }
                    },
                ) { Text("확인") }
            },
        )
    }

    Scaffold(
        bottomBar = {
            if (overlay == Overlay.None) {
                CompactBottomBar(
                    selectedTab = tab,
                    onSelectTab = { tab = it },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (overlay) {
                Overlay.TeamHistory -> TeamHistoryScreen(onBack = { overlay = Overlay.None })
                Overlay.EntryBoard -> EntryBoardScreen(
                    selectedDate = entryDate,
                    dayEntry = dayEntry,
                    loading = entryLoading,
                    recentMoves = recentMoves,
                    changeDates = entryChangeDates,
                    onSelectDate = onSelectEntryDate,
                    onBack = { overlay = Overlay.None },
                    onPlayerClick = { p ->
                        showPlayerSheet = true
                        onPlayerClick(
                            LineupSlot(
                                batOrder = 0,
                                name = p.name,
                                position = p.position,
                                playerCode = p.playerCode,
                                backNumber = p.backNumber,
                                hitType = p.hitType,
                                isPitcher = p.isPitcher || p.position.contains("투수"),
                            ),
                        )
                    },
                )
                Overlay.Leaders -> LeadersScreen(
                    batters = batterLeaders,
                    pitchers = pitcherLeaders,
                    favoriteCodes = favoriteCodes,
                    onBack = { overlay = Overlay.None },
                    onPlayerClick = { p ->
                        showPlayerSheet = true
                        onLeaderPlayerClick(p)
                    },
                    onToggleFavorite = { p ->
                        onToggleFavorite(p.playerCode, p.name, p.team)
                    },
                )
                Overlay.None -> when (tab) {
                    0 -> LiveScreen(
                        snapshot = snapshot,
                        error = error,
                        loading = loading,
                        secondsUntilRefresh = secondsUntilRefresh,
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        weather = weather,
                        batterLeaders = batterLeaders,
                        onOpenTeamHistory = { overlay = Overlay.TeamHistory },
                        onOpenEntryBoard = {
                            onOpenEntrySmart()
                            overlay = Overlay.EntryBoard
                        },
                        onOpenLeaders = { overlay = Overlay.Leaders },
                        onPlayerClick = { slot ->
                            showPlayerSheet = true
                            onPlayerClick(slot)
                        },
                        onPitcherClick = { p ->
                            showPlayerSheet = true
                            onPitcherClick(p)
                        },
                        onKeyPlayerClick = { code, name ->
                            showPlayerSheet = true
                            val byCode = batterLeaders.firstOrNull { p ->
                                code.isNotBlank() && p.playerCode == code
                            }
                            val byName = batterLeaders.filter { p ->
                                name.isNotBlank() && p.name == name
                            }
                            val leader = byCode
                                ?: byName.firstOrNull { it.isLotte }
                                ?: byName.firstOrNull()
                            if (leader != null) {
                                onLeaderPlayerClick(leader)
                            } else {
                                onPlayerClick(LineupSlot(0, name, "", playerCode = code))
                            }
                        },
                        onShare = { g ->
                            val text = "${g.focusName()} ${g.lotteScore}:${g.opponentScore} ${g.opponentName} · ${g.inningLabel}\n#사직스코어"
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "공유"))
                        },
                        onSelectLiveGame = onSelectLiveGame,
                        viewingGame = viewingGame,
                        viewingLoading = viewingLoading,
                        onOpenGame = onOpenGame,
                        onBackToLotte = onBackToLotte,
                    )
                    1 -> ResultsScreen(
                        selectedDate = selectedDate,
                        games = dayGames,
                        loading = dayGamesLoading,
                        onSelectDate = onSelectDate,
                        calendarMonth = calendarMonth,
                        monthGames = monthGames,
                        onSelectMonth = onSelectMonth,
                        onRefresh = onRefreshDayGames,
                        refreshing = isRefreshing,
                        onOpenGame = { id ->
                            tab = 0
                            overlay = Overlay.None
                            onOpenGame(id)
                        },
                    )
                    2 -> StandingsScreen(
                        standings = standings,
                        teamCard = teamCard,
                        batterLeaders = batterLeaders,
                        pitcherLeaders = pitcherLeaders,
                        onOpenLeaders = { overlay = Overlay.Leaders },
                        onRefresh = onRefreshStandings,
                        refreshing = isRefreshing,
                    )
                    3 -> SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        favoritePlayers = favoritePlayers,
                        onRemoveFavorite = onRemoveFavorite,
                    )
                }
            }

            if (showPlayerSheet) {
                PlayerDetailSheet(
                    detail = playerDetail,
                    loading = playerLoading,
                    isFavorite = playerDetail?.playerCode?.let { it in favoriteCodes } == true,
                    onToggleFavorite = {
                        val d = playerDetail ?: return@PlayerDetailSheet
                        onToggleFavorite(d.playerCode, d.name, "")
                    },
                    onDismiss = {
                        showPlayerSheet = false
                        onClearPlayer()
                    },
                )
            }
        }
    }
}

private data class BottomTab(val label: String, val icon: ImageVector)

@Composable
private fun CompactBottomBar(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val tabs = listOf(
        BottomTab("라이브", Icons.Default.Home),
        BottomTab("결과", Icons.AutoMirrored.Filled.List),
        BottomTab("순위", Icons.Default.Star),
        BottomTab("설정", Icons.Default.Settings),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, item ->
                if (index > 0) {
                    VerticalDivider(
                        modifier = Modifier.height(28.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
                val selected = selectedTab == index
                val tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelectTab(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                        tint = tint,
                    )
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                    )
                }
            }
        }
    }
}
