package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_LOGO_URL
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.StadiumWeather
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.WinGreen
import com.bossxor.lottegiants.domain.RecentFormGame
import com.bossxor.lottegiants.ui.components.DiamondView
import com.bossxor.lottegiants.ui.components.HotColdZoneChart
import com.bossxor.lottegiants.ui.components.ScoreBoard
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.SectionHeader
import com.bossxor.lottegiants.ui.components.PlayerAvatar
import com.bossxor.lottegiants.ui.components.TeamLogo
import com.bossxor.lottegiants.ui.heroGradient

private val DETAIL_TABS = listOf("프리뷰", "라인업", "요약", "중계", "기록")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    snapshot: LiveSnapshot?,
    error: String?,
    loading: Boolean,
    secondsUntilRefresh: Int = 10,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    weather: StadiumWeather? = null,
    batterLeaders: List<com.bossxor.lottegiants.domain.LeaderPlayer> = emptyList(),
    onOpenTeamHistory: () -> Unit = {},
    onOpenEntryBoard: () -> Unit = {},
    onOpenLeaders: () -> Unit = {},
    onPlayerClick: (LineupSlot) -> Unit = {},
    onPitcherClick: (com.bossxor.lottegiants.domain.PitcherLine) -> Unit = {},
    onKeyPlayerClick: (String, String) -> Unit = { _, _ -> },
    onShare: (LotteGameInfo) -> Unit = {},
    onSelectLiveGame: (String) -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { DETAIL_TABS.size })
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { com.bossxor.lottegiants.data.GiantsRepository.get(context).store }
    var showPermBanner by remember { mutableStateOf(true) }
    val today = remember { java.time.LocalDate.now().toString() }
    LaunchedEffect(Unit) {
        val dismissed = store.bannerDismissedDay()
        showPermBanner = dismissed != today
    }
    val needNotif = android.os.Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    val needBattery = !isIgnoringBatteryOptimizations(context)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(LOTTE_LOGO_URL, size = 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("사직스코어", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("실시간 현황", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompactRefresh(secondsUntilRefresh, isRefreshing, onRefresh)
            }
            Spacer(Modifier.height(12.dp))

            val game = snapshot?.lotteGame ?: snapshot?.nextLotteGame
            when {
                loading && snapshot == null -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                game != null -> {
                    if (showPermBanner && (needNotif || needBattery)) {
                        PermissionBanner(
                            needNotif = needNotif,
                            needBattery = needBattery,
                            onOpenSettings = {
                                if (needNotif) {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        },
                                    )
                                } else {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        },
                                    )
                                }
                            },
                            onDismiss = {
                                showPermBanner = false
                                kotlinx.coroutines.MainScope().launch { store.setBannerDismissedDay(today) }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (snapshot?.lotteGame != null) {
                        val liveChoices = snapshot.todayLotteGames
                        if (liveChoices.size >= 2) {
                            DhGameSwitcher(
                                games = liveChoices,
                                selectedId = game.gameId,
                                onSelect = onSelectLiveGame,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        HeroCard(game, onShare = { onShare(game) })
                    } else {
                        NextGameContent(game)
                    }
                    Spacer(Modifier.height(4.dp))
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                    ) {
                        DETAIL_TABS.forEachIndexed { i, label ->
                            val selected = pagerState.currentPage == i
                            Tab(
                                selected = selected,
                                onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        beyondViewportPageCount = 1,
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 12.dp, bottom = 16.dp),
                        ) {
                            when (page) {
                                0 -> PreviewTab(
                                    g = game,
                                    snapshot = snapshot,
                                    weather = weather ?: snapshot?.weather ?: game.preview?.weather,
                                    onKeyPlayerClick = onKeyPlayerClick,
                                )
                                1 -> LineupTab(game, onPlayerClick, onPitcherClick, onRefresh)
                                2 -> SummaryTab(
                                    g = game,
                                    snapshot = snapshot,
                                    weather = weather,
                                    batterLeaders = batterLeaders,
                                    onKeyPlayerClick = onKeyPlayerClick,
                                    onOpenTeamHistory = onOpenTeamHistory,
                                    onOpenEntryBoard = onOpenEntryBoard,
                                    onOpenLeaders = onOpenLeaders,
                                    onRetry = onRefresh,
                                )
                                3 -> RelayTab(game, snapshot, onRefresh)
                                4 -> RecordTab(game, onPlayerClick, onPitcherClick, onRefresh)
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                    ) {
                        if (showPermBanner && (needNotif || needBattery)) {
                            PermissionBanner(
                                needNotif = needNotif,
                                needBattery = needBattery,
                                onOpenSettings = {
                                    if (needNotif) {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            },
                                        )
                                    } else {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            },
                                        )
                                    }
                                },
                                onDismiss = {
                                    showPermBanner = false
                                    kotlinx.coroutines.MainScope().launch { store.setBannerDismissedDay(today) }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        snapshot?.recentLotteGames?.takeIf { it.isNotEmpty() }?.let {
                            RecentFiveCard(it)
                            Spacer(Modifier.height(12.dp))
                        } ?: snapshot?.lastLotteGame?.let {
                            RecentResultCard(it)
                            Spacer(Modifier.height(12.dp))
                        }
                        ScoreTicker(snapshot)
                        Spacer(Modifier.height(12.dp))
                        weather?.let {
                            WeatherLine(it)
                            Spacer(Modifier.height(12.dp))
                        }
                        QuickLinks(onOpenTeamHistory, onOpenEntryBoard, onOpenLeaders)
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreTicker(snapshot: LiveSnapshot?, excludeLotte: Boolean = false) {
    val games = buildList {
        if (!excludeLotte) {
            snapshot?.lotteGame?.let { g ->
                add(
                    MiniGame(
                        gameId = g.gameId,
                        homeName = if (g.isHome) "롯데" else g.opponentName,
                        awayName = if (g.isHome) g.opponentName else "롯데",
                        homeScore = if (g.isHome) g.lotteScore else g.opponentScore,
                        awayScore = if (g.isHome) g.opponentScore else g.lotteScore,
                        status = g.status,
                        statusText = g.inningLabel,
                        homeLogoUrl = if (g.isHome) g.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL } else g.opponentLogoUrl,
                        awayLogoUrl = if (g.isHome) g.opponentLogoUrl else g.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL },
                        homeTeamCode = if (g.isHome) "LT" else g.opponentCode,
                        awayTeamCode = if (g.isHome) g.opponentCode else "LT",
                    )
                )
            }
        }
        snapshot?.otherGames?.let { addAll(it) }
    }
    if (games.isEmpty()) return
    if (excludeLotte) {
        Text(
            "다른 구장",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(games, key = { it.gameId }) { g ->
            val show = g.status == GameStatus.LIVE || g.status == GameStatus.ENDED
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(g.awayLogoUrl, size = 18)
                    Spacer(Modifier.width(4.dp))
                    Text(shortName(g.awayName), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (show) " ${g.awayScore}" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (show) "${g.homeScore} " else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(shortName(g.homeName), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    TeamLogo(g.homeLogoUrl, size = 18)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (g.status) {
                            GameStatus.LIVE -> g.statusText.ifBlank { "LIVE" }
                            GameStatus.ENDED -> "종료"
                            GameStatus.BEFORE -> g.startTime.ifBlank { "예정" }
                            GameStatus.CANCELED -> g.statusText.ifBlank { "취소" }
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (g.status == GameStatus.LIVE) LotteRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun shortName(name: String): String = when {
    name.contains("롯데") -> "롯데"
    name.contains("삼성") -> "삼성"
    name.contains("한화") -> "한화"
    name.contains("두산") -> "두산"
    name.contains("키움") -> "키움"
    name.length <= 3 -> name
    else -> name.take(2)
}

@Composable
private fun WeatherLine(w: StadiumWeather) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${w.stadium} 날씨",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                buildString {
                    append(String.format("%.0f°", w.temperatureC))
                    if (w.summary.isNotBlank()) append(" · ${w.summary}")
                    w.precipProbability?.let { append(" · 강수 $it%") }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun QuickLinks(onHistory: () -> Unit, onEntry: () -> Unit, onLeaders: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickLinkChip("팀 히스토리", Modifier.weight(1f), onHistory)
        QuickLinkChip("엔트리", Modifier.weight(1f), onEntry)
        QuickLinkChip("타이틀", Modifier.weight(1f), onLeaders)
    }
}

@Composable
private fun QuickLinkChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PreviewTab(
    g: LotteGameInfo,
    snapshot: LiveSnapshot?,
    weather: StadiumWeather? = null,
    onKeyPlayerClick: (String, String) -> Unit = { _, _ -> },
) {
    val p = g.preview
    SectionCard {
        Column {
            SectionHeader("경기 정보")
            InfoLine("일시", "${g.gameDate}  ${g.startTime.ifBlank { "-" }}")
            InfoLine("구장", g.stadium.ifBlank { p?.stadium.orEmpty().ifBlank { "-" } })
            InfoLine("중계", g.broadChannel.ifBlank { "-" })
            InfoLine("장소", if (g.isHome) "홈" else "원정")
            if (g.doubleHeaderNo > 0) {
                InfoLine("더블헤더", "${g.doubleHeaderNo}차전")
            }
            if (g.seasonSeriesNo > 0) {
                InfoLine("시즌 대결", "${g.seasonSeriesNo}차전")
            }
            if (g.gameScLabel.isNotBlank()) {
                InfoLine("대회", g.gameScLabel)
            }
            if (g.lineupAnnounced && g.status == GameStatus.BEFORE) {
                InfoLine("라인업", "발표 완료")
            }
            if (g.crowdCount.isNotBlank()) {
                InfoLine("관중", "${g.crowdCount}명")
            }
            if (g.gameDuration.isNotBlank()) {
                InfoLine("경기시간", g.gameDuration)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    weather?.let {
        WeatherLine(it)
        Spacer(Modifier.height(10.dp))
    }

    SectionCard {
        Column {
            SectionHeader("선발 투수 비교")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PreviewPitcherCol("롯데", p?.lotteStarter, g.lotteStartingPitcher)
                PreviewPitcherCol(g.opponentName.ifBlank { "상대" }, p?.opponentStarter, g.opponentStartingPitcher)
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    SectionCard {
        Column {
            SectionHeader("팀 비교")
            val ls = p?.lotteStanding
            val os = p?.opponentStanding
            InfoLine(
                "순위",
                "롯데 ${ls?.rank?.takeIf { it > 0 } ?: "-"}위  ·  ${g.opponentName} ${os?.rank?.takeIf { it > 0 } ?: "-"}위",
            )
            InfoLine(
                "승패",
                "롯데 ${ls?.win ?: 0}승 ${ls?.draw ?: 0}무 ${ls?.lose ?: 0}패  ·  " +
                    "${g.opponentName} ${os?.win ?: 0}승 ${os?.draw ?: 0}무 ${os?.lose ?: 0}패",
            )
            if ((ls?.wra ?: 0.0) > 0 || (os?.wra ?: 0.0) > 0) {
                InfoLine(
                    "승률",
                    "롯데 ${String.format("%.3f", ls?.wra ?: 0.0)}  ·  ${g.opponentName} ${String.format("%.3f", os?.wra ?: 0.0)}",
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    SectionCard {
        Column {
            SectionHeader("시즌 맞대결")
            Text(
                p?.seasonMatchup?.label?.ifBlank { "시즌 맞대결 데이터 없음" } ?: "시즌 맞대결 데이터 없음",
                fontWeight = FontWeight.SemiBold,
            )
            val recent = p?.recentMatchups.orEmpty()
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                recent.forEach { m ->
                    val lotteHome = m.homeTeamCode == com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
                    val ls = if (lotteHome) m.homeScore else m.awayScore
                    val os = if (lotteHome) m.awayScore else m.homeScore
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${m.gameDate.takeLast(5)} ${if (lotteHome) "vs" else "@"} ${g.opponentName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$ls:$os",
                            fontWeight = FontWeight.Bold,
                            color = when {
                                ls > os -> WinGreen
                                ls < os -> LoseRed
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    SectionCard {
        Column {
            SectionHeader("키 플레이어")
            val lb = p?.lotteKeyBatter
            val ob = p?.opponentKeyBatter
            if (lb?.name.isNullOrBlank() && ob?.name.isNullOrBlank()) {
                Text("프리뷰 키플레이어 정보 없음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                if (!lb?.name.isNullOrBlank()) {
                    KeyBatterLine("롯데", lb!!, g.opponentName, onKeyPlayerClick)
                }
                if (!ob?.name.isNullOrBlank()) {
                    if (!lb?.name.isNullOrBlank()) Spacer(Modifier.height(10.dp))
                    KeyBatterLine(g.opponentName, ob!!, "롯데", onKeyPlayerClick)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "최근 5경기 성적이 좋은 타자를 네이버 프리뷰가 고릅니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val lotteForm = p?.lotteRecentForm.orEmpty()
    val oppForm = p?.opponentRecentForm.orEmpty()
    if (lotteForm.isNotEmpty() || oppForm.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Column {
                SectionHeader("최근 5경기")
                if (lotteForm.isNotEmpty()) {
                    RecentFormBlock("롯데", lotteForm)
                }
                if (oppForm.isNotEmpty()) {
                    if (lotteForm.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    RecentFormBlock(g.opponentName.ifBlank { "상대" }, oppForm)
                }
            }
        }
    }
}

@Composable
private fun KeyBatterLine(
    team: String,
    batter: com.bossxor.lottegiants.domain.PreviewBatter,
    opponentName: String,
    onClick: (String, String) -> Unit = { _, _ -> },
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = batter.name.isNotBlank()) { onClick(batter.playerCode, batter.name) }
            .padding(vertical = 2.dp),
    ) {
        Text(
            "$team  ${batter.name}  시즌 ${batter.avg.ifBlank { "-" }} · ${batter.hr}홈런 ${batter.rbi}타점",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val detail = listOfNotNull(
            batter.recentAvg.takeIf { it.isNotBlank() }?.let {
                "최근 5경기 $it" + if (batter.recentHits > 0) " (${batter.recentHits}안타 ${batter.recentRbi}타점)" else ""
            },
            batter.vsOpponentAvg.takeIf { it.isNotBlank() }?.let {
                "${opponentName}전 $it" + if (batter.vsOpponentHr > 0) " (${batter.vsOpponentHr}홈런)" else ""
            },
        ).joinToString("  ·  ")
        if (detail.isNotBlank()) {
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (batter.hotCold.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HotColdZoneChart(batter.hotCold)
        }
    }
}

@Composable
private fun RecentFormBlock(team: String, games: List<RecentFormGame>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(team, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                games.take(5).forEach { g ->
                    val (bg, fg) = when (g.result) {
                        "승" -> WinGreen to Color.White
                        "패" -> LoseRed to Color.White
                        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        g.result.ifBlank { "·" },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = fg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        games.take(5).forEach { g ->
            val date = g.date.takeLast(5)
            val vs = if (g.isHome) "vs" else "@"
            Text(
                "$date $vs ${g.opponentName}  ${g.teamScore}:${g.oppScore}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewPitcherCol(team: String, pitcher: com.bossxor.lottegiants.domain.PreviewPitcher?, fallback: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        Text(team, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(pitcher?.name?.ifBlank { fallback }.orEmpty().ifBlank { fallback.ifBlank { "미정" } }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        val era = pitcher?.era.orEmpty()
        val wl = if ((pitcher?.wins ?: 0) + (pitcher?.losses ?: 0) > 0) "${pitcher?.wins}-${pitcher?.losses}" else ""
        Text(
            listOfNotNull(
                era.takeIf { it.isNotBlank() }?.let { "ERA $it" },
                wl.takeIf { it.isNotBlank() },
                pitcher?.innings?.takeIf { it.isNotBlank() }?.let { "${it}IP" },
            ).joinToString(" · ").ifBlank { "시즌 성적 —" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryTab(
    g: LotteGameInfo,
    snapshot: LiveSnapshot?,
    weather: StadiumWeather? = null,
    batterLeaders: List<com.bossxor.lottegiants.domain.LeaderPlayer> = emptyList(),
    onKeyPlayerClick: (String, String) -> Unit = { _, _ -> },
    onOpenTeamHistory: () -> Unit = {},
    onOpenEntryBoard: () -> Unit = {},
    onOpenLeaders: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    DetailLoadError(g.detailError, onRetry)
    if (g.status == GameStatus.LIVE) {
        SectionCard {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("현재 타석")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DiamondView(
                        on1 = g.onBase1, on2 = g.onBase2, on3 = g.onBase3,
                        outs = g.out, ball = g.ball, strike = g.strike,
                        inningLabel = g.inningLabel,
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                InfoLine("타자", buildString {
                    if (g.currentBatterOrder > 0) append("${g.currentBatterOrder}번 ")
                    append(g.currentBatterName.ifBlank { "-" })
                    if (g.isLotteBatting) append("  (롯데 공격)")
                })
                InfoLine("다음 타자", g.nextBatterName.ifBlank { "-" })
                InfoLine("BSO", "B${g.ball}  S${g.strike}  O${g.out}")
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Column {
                SectionHeader("현재 투수")
                InfoLine("이름", g.currentPitcherName.ifBlank { "-" })
                InfoLine(
                    "투구수",
                    if (g.currentPitcherPitchCount > 0) "${g.currentPitcherPitchCount}구" else "—",
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (g.keyPlays.isNotEmpty()) {
        SectionCard {
            Column {
                SectionHeader("주요 장면")
                g.keyPlays.take(8).forEach { play ->
                    val half = when (play.isTop) {
                        true -> "${play.inning}회초"
                        false -> "${play.inning}회말"
                        null -> "${play.inning}회"
                    }
                    Text(
                        "$half  ${play.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (play.isScoring) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (g.status == GameStatus.ENDED) {
        SectionCard {
            Column {
                SectionHeader("승패 · MVP")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("승리", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(g.winPitcherName.ifBlank { "-" }, fontWeight = FontWeight.Bold, color = WinGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("패전", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(g.losePitcherName.ifBlank { "-" }, fontWeight = FontWeight.Bold, color = LoseRed)
                    }
                    if (g.savePitcherName.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("세이브", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(g.savePitcherName, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (g.provisionalMvpName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    InfoLine("MVP(간이)", "${g.provisionalMvpName}  ${g.provisionalMvpLine}")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    SectionCard {
        Column {
            SectionHeader("선발 투수")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("롯데", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(g.lotteStartingPitcher.ifBlank { "미정" }, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(g.opponentName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(g.opponentStartingPitcher.ifBlank { "미정" }, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    SectionCard {
        Column {
            SectionHeader("기타 정보")
            InfoLine("구장", g.stadium.ifBlank { "-" })
            InfoLine("시간", "${g.gameDate} ${g.startTime}".trim().ifBlank { "-" })
            InfoLine("중계", g.broadChannel.ifBlank { "-" })
        }
    }
    Spacer(Modifier.height(10.dp))

    val winSeries = snapshot?.winProbSeries.orEmpty()
    if (winSeries.isNotEmpty()) {
        SectionCard {
            Column {
                SectionHeader("롯데 승리 확률")
                val last = (winSeries.last().homeProb * 100).toInt()
                Text(
                    "$last%",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                com.bossxor.lottegiants.ui.components.SparklineChart(
                    values = winSeries.map { it.homeProb * 100.0 },
                    height = 56.dp,
                    yMin = 0.0,
                    yMax = 100.0,
                )
                Text(
                    "타석별 변화",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (g.status == GameStatus.LIVE || g.status == GameStatus.ENDED) {
        SectionCard {
            Column {
                SectionHeader("롯데 승리 확률")
                Text(
                    "아직 충분한 데이터가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // 경기 전(BEFORE)에는 WPA 플레이스홀더 숨김
    Spacer(Modifier.height(10.dp))

    KeyPlayerChip(batterLeaders, g) { code, name -> onKeyPlayerClick(code, name) }

    val recent = snapshot?.recentLotteGames.orEmpty()
    if (recent.isNotEmpty()) {
        RecentFiveCard(recent)
        Spacer(Modifier.height(12.dp))
    } else if (g.status == GameStatus.BEFORE) {
        snapshot?.lastLotteGame?.let {
            RecentResultCard(it)
            Spacer(Modifier.height(12.dp))
        }
    }

    ScoreTicker(snapshot, excludeLotte = true)
    Spacer(Modifier.height(12.dp))
    weather?.let {
        WeatherLine(it)
        Spacer(Modifier.height(12.dp))
    }
    QuickLinks(onOpenTeamHistory, onOpenEntryBoard, onOpenLeaders)
}

@Composable
private fun LineupTab(
    g: LotteGameInfo,
    onPlayerClick: (LineupSlot) -> Unit,
    onPitcherClick: (com.bossxor.lottegiants.domain.PitcherLine) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var showLotte by remember { mutableStateOf(true) }
    val lineup = if (showLotte) g.lotteLineup else g.opponentLineup
    val bench = if (showLotte) g.lotteBenchBatters else g.opponentBenchBatters
    val pitchers = if (showLotte) g.lottePitchers else g.opponentPitchers
    val starterName = if (showLotte) g.lotteStartingPitcher else g.opponentStartingPitcher
    DetailLoadError(g.detailError, onRetry)

    SectionCard {
        val starterCode = pitchers.firstOrNull { it.name == starterName }?.playerCode.orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(playerCode = starterCode, name = starterName.ifBlank { "미정" }, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                SectionHeader("선발 투수")
                Text(
                    starterName.ifBlank { "미정" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    if (showLotte) "롯데 선발" else "${g.opponentName} 선발",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LineupTeamChip("롯데", selected = showLotte) { showLotte = true }
        LineupTeamChip(g.opponentName.ifBlank { "상대" }, selected = !showLotte) { showLotte = false }
    }
    Spacer(Modifier.height(10.dp))
    if (lineup.isEmpty()) {
        SectionCard {
            Column {
                Text(
                    when {
                        g.lineupAnnounced -> "라인업은 발표됐지만 상세를 아직 못 불러왔습니다."
                        g.status == GameStatus.ENDED -> "타순 기록을 불러오지 못했습니다."
                        else -> "라인업이 아직 발표되지 않았습니다."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (g.lineupAnnounced) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("다시 불러오기")
                    }
                }
            }
        }
    } else {
        SectionCard {
            Column {
                SectionHeader(if (showLotte) "롯데 선발 타순" else "${g.opponentName} 선발 타순")
                Text("시즌 타율 · 당일 안타/타수", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                lineup.forEachIndexed { idx, slot ->
                    LineupPlayerRow(g, slot, showLotte, onPlayerClick)
                    if (idx < lineup.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
        if (bench.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Column {
                    SectionHeader("후보 야수 · 교체")
                    bench.forEachIndexed { idx, slot ->
                        LineupPlayerRow(g, slot, showLotte, onPlayerClick)
                        if (idx < bench.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
        if (pitchers.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Column {
                    SectionHeader("불펜 · 투수")
                    pitchers.forEachIndexed { idx, p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = p.playerCode.isNotBlank()) { onPitcherClick(p) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                PlayerAvatar(playerCode = p.playerCode, name = p.name, size = 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Text(
                                listOfNotNull(
                                    p.innings.takeIf { it.isNotBlank() }?.let { "${it}이닝" },
                                    p.pitchCount.takeIf { it > 0 }?.let { "${it}구" },
                                    "${p.strikeouts}K",
                                ).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (idx < pitchers.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineupPlayerRow(
    g: LotteGameInfo,
    slot: LineupSlot,
    showLotte: Boolean,
    onPlayerClick: (LineupSlot) -> Unit,
) {
    val isAtBat = g.status == GameStatus.LIVE &&
        slot.name == g.currentBatterName &&
        ((showLotte && g.isLotteBatting) || (!showLotte && !g.isLotteBatting))
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isAtBat) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable { onPlayerClick(slot) }
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("${slot.batOrder}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            PlayerAvatar(playerCode = slot.playerCode, name = slot.name, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(slot.name + if (slot.isSubstitute) " *" else "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(
            "${slot.position}  ${formatAvg(slot.seasonAvg)}  ${slot.todayHits}/${slot.todayAtBats}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LineupTeamChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = fg,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

@Composable
private fun DhGameSwitcher(
    games: List<MiniGame>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        games.forEachIndexed { idx, g ->
            val label = when {
                g.doubleHeaderNo in 1..2 -> "${g.doubleHeaderNo}차전"
                else -> "${idx + 1}차전"
            }
            val sub = when (g.status) {
                GameStatus.LIVE -> "LIVE"
                GameStatus.ENDED -> "종료"
                GameStatus.CANCELED -> "취소"
                GameStatus.BEFORE -> g.startTime.ifBlank { "예정" }
            }
            LineupTeamChip("$label · $sub", selected = g.gameId == selectedId) {
                onSelect(g.gameId)
            }
        }
    }
}

@Composable
private fun RelayTab(g: LotteGameInfo, snapshot: LiveSnapshot? = null, onRetry: () -> Unit = {}) {
    var filterMode by remember { mutableIntStateOf(0) } // 0 all, 1 scoring
    DetailLoadError(g.detailError, onRetry)
    if (g.status == GameStatus.LIVE) {
        SectionCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionHeader("필드 뷰")
                DiamondView(
                    on1 = g.onBase1, on2 = g.onBase2, on3 = g.onBase3,
                    outs = g.out, ball = g.ball, strike = g.strike,
                    inningLabel = g.inningLabel,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "타자  ${g.currentBatterName.ifBlank { "-" }}  ·  다음  ${g.nextBatterName.ifBlank { "-" }}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "투수  ${g.currentPitcherName.ifBlank { "-" }}" +
                        if (g.currentPitcherPitchCount > 0) "  (${g.currentPitcherPitchCount}구)" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionHeader("투구 뷰 · 존 차트")
            val pitches = g.pitchLocations.ifEmpty { snapshot?.pitchLocations.orEmpty() }
            if (pitches.isEmpty()) {
                Text(
                    "투구 추적 데이터가 아직 없습니다. (경기 중·종료 후 표시)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                com.bossxor.lottegiants.ui.components.PitchZoneChart(pitches)
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    SectionCard {
        Column {
            SectionHeader("문자 중계")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineupTeamChip("전체", selected = filterMode == 0) { filterMode = 0 }
                LineupTeamChip("득점·홈런", selected = filterMode == 1) { filterMode = 1 }
            }
            Spacer(Modifier.height(8.dp))
            if (g.recentTexts.isEmpty() && g.inning <= 0) {
                Text("중계 텍스트가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val innings = remember(g.inning, g.isTopInning, g.status, g.recentTexts.size, g.lotteInningScores.size) {
                    buildRelayInningKeys(g)
                }
                val liveLabel = remember(g.inning, g.isTopInning) {
                    inningHalfLabel(g.inning, g.isTopInning)
                }
                var selectedLabel by remember { mutableStateOf("") }
                var pinnedToLive by remember { mutableStateOf(true) }
                LaunchedEffect(liveLabel, innings) {
                    if (selectedLabel.isBlank() || pinnedToLive) {
                        selectedLabel = innings.firstOrNull { it.label == liveLabel }?.label
                            ?: innings.lastOrNull()?.label.orEmpty()
                    } else if (innings.none { it.label == selectedLabel }) {
                        selectedLabel = innings.lastOrNull()?.label.orEmpty()
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(innings, key = { it.label }) { key ->
                        LineupTeamChip(
                            label = key.label,
                            selected = selectedLabel == key.label,
                        ) {
                            selectedLabel = key.label
                            pinnedToLive = key.label == liveLabel
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val selected = innings.firstOrNull { it.label == selectedLabel }
                val texts = g.recentTexts
                    .filter { matchesRelayInning(it, selected) }
                    .filter {
                        if (filterMode == 0) true
                        else listOf("홈런", "득점", "타점", "역전", "동점", "끝내기").any { k -> it.text.contains(k) }
                    }
                    .sortedByDescending { it.seqno }
                if (texts.isEmpty()) {
                    Text(
                        if (g.recentTexts.isEmpty()) "중계 텍스트가 없습니다."
                        else "이 이닝 중계가 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                } else {
                    texts.forEach { t ->
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class RelayInningKey(val inning: Int, val isTop: Boolean, val label: String)

/** 1회초부터 현재(또는 종료) 이닝까지 초·말 칩 목록 */
private fun buildRelayInningKeys(g: LotteGameInfo): List<RelayInningKey> {
    val maxFromScores = maxOf(g.lotteInningScores.size, g.opponentInningScores.size)
    val maxFromTexts = g.recentTexts.maxOfOrNull { it.inning } ?: 0
    val maxInn = maxOf(g.inning, maxFromScores, maxFromTexts, 1)
    val keys = mutableListOf<RelayInningKey>()
    for (inn in 1..maxInn) {
        keys.add(RelayInningKey(inn, true, "${inn}회초"))
        val includeBottom = when {
            inn < g.inning -> true
            inn > g.inning -> true
            g.inning <= 0 -> true
            g.status == GameStatus.ENDED || g.status == GameStatus.CANCELED -> true
            !g.isTopInning -> true
            inn < maxInn -> true
            else -> false
        }
        if (includeBottom) keys.add(RelayInningKey(inn, false, "${inn}회말"))
    }
    // 텍스트에만 있는 half(초/말 미상 → N회)도 칩에 반영
    g.recentTexts
        .filter { it.isTopInning == null }
        .map { "${it.inning}회" }
        .distinct()
        .forEach { label ->
            if (keys.none { it.label == label }) {
                val inn = label.removeSuffix("회").toIntOrNull() ?: return@forEach
                keys.add(RelayInningKey(inn, true, label))
            }
        }
    return keys.sortedWith(compareBy({ it.inning }, { if (it.isTop) 0 else 1 }))
}

private fun matchesRelayInning(t: com.bossxor.lottegiants.domain.RelayText, selected: RelayInningKey?): Boolean {
    if (selected == null || t.inning != selected.inning) return false
    // 「N회」칩(초·말 통합)이거나, 초/말 미상이면 해당 이닝 칩에 모두 표시
    if (selected.label.endsWith("회") && !selected.label.endsWith("회초") && !selected.label.endsWith("회말")) {
        return true
    }
    return when (t.isTopInning) {
        null -> true
        else -> t.isTopInning == selected.isTop
    }
}

private fun inningHalfLabel(inning: Int, isTop: Boolean?): String = when (isTop) {
    true -> "${inning}회초"
    false -> "${inning}회말"
    null -> "${inning}회"
}

@Composable
private fun RecordTab(
    g: LotteGameInfo,
    onPlayerClick: (LineupSlot) -> Unit = {},
    onPitcherClick: (com.bossxor.lottegiants.domain.PitcherLine) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var showLotte by remember { mutableStateOf(true) }
    DetailLoadError(g.detailError, onRetry)
    if (g.status == GameStatus.LIVE || g.status == GameStatus.ENDED) {
        ScoreBoard(
            opponentName = g.opponentName,
            lotteScores = g.lotteInningScores,
            oppScores = g.opponentInningScores,
            lotteFirst = !g.isHome,
            lotteR = g.lotteScore,
            oppR = g.opponentScore,
            lotteH = g.lotteHits,
            oppH = g.opponentHits,
            lotteE = g.lotteErrors,
            oppE = g.opponentErrors,
        )
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Column {
                SectionHeader("팀 비교")
                InfoLine("안타", "롯데 ${g.lotteHits}  ·  ${g.opponentName} ${g.opponentHits}")
                InfoLine("실책", "롯데 ${g.lotteErrors}  ·  ${g.opponentName} ${g.opponentErrors}")
                InfoLine("사사구", "롯데 ${g.lotteBb}  ·  ${g.opponentName} ${g.opponentBb}")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineupTeamChip("롯데", selected = showLotte) { showLotte = true }
            LineupTeamChip(g.opponentName.ifBlank { "상대" }, selected = !showLotte) { showLotte = false }
        }
        Spacer(Modifier.height(10.dp))
        val batters = if (showLotte) g.lotteLineup else g.opponentLineup
        val bench = if (showLotte) g.lotteBenchBatters else g.opponentBenchBatters
        val pitchers = if (showLotte) g.lottePitchers else g.opponentPitchers
        BatterRecordCard("선발 타자", batters, g, showLotte, onPlayerClick)
        if (bench.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BatterRecordCard("교체 타자", bench, g, showLotte, onPlayerClick)
        }
        Spacer(Modifier.height(10.dp))
        SectionCard(padding = 12.dp) {
            Column {
                SectionHeader("투수 기록")
                Row(Modifier.fillMaxWidth()) {
                    Text("이름", Modifier.weight(1.1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ERA", Modifier.width(36.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text("이닝", Modifier.width(36.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text("안타", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text("실점", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text("삼진", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text("투구", Modifier.width(32.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                if (pitchers.isEmpty()) {
                    Text("투수 기록 없음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    pitchers.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = p.playerCode.isNotBlank()) { onPitcherClick(p) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
                                PlayerAvatar(playerCode = p.playerCode, name = p.name, size = 18.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Text(
                                p.seasonEra.ifBlank { "—" },
                                Modifier.width(36.dp),
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(p.innings.ifBlank { "-" }, Modifier.width(36.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${p.hits}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${p.runs}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${p.strikeouts}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text(
                                if (p.pitchCount > 0) "${p.pitchCount}" else "-",
                                Modifier.width(32.dp),
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        InningAtBatCard(g)
    } else {
        SectionCard {
            Text("경기가 시작되면 이닝별·타자·투수 기록이 표시됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InningAtBatCard(g: LotteGameInfo) {
    if (g.recentTexts.isEmpty()) return
    SectionCard(padding = 12.dp) {
        Column {
            SectionHeader("이닝별 타석 결과")
            val byInning = g.recentTexts
                .groupBy { it.inning }
                .toSortedMap()
            byInning.forEach { (inn, texts) ->
                Text("${inn}회", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                texts.sortedBy { it.seqno }.takeLast(6).forEach { t ->
                    Text(
                        t.text,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun formatAvg(avg: Double?): String {
    if (avg == null || avg <= 0.0) return "—"
    return String.format("%.3f", avg).removePrefix("0")
}

@Composable
private fun BatterRecordCard(
    title: String,
    batters: List<LineupSlot>,
    g: LotteGameInfo,
    showLotte: Boolean,
    onPlayerClick: (LineupSlot) -> Unit,
) {
    SectionCard(padding = 12.dp) {
        Column {
            SectionHeader(title)
            Row(Modifier.fillMaxWidth()) {
                Text("순", Modifier.width(22.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("이름", Modifier.weight(1.1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("시즌", Modifier.width(36.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                Text("타수", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                Text("안타", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                Text("타점", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                Text("득점", Modifier.width(28.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            if (batters.isEmpty()) {
                Text("타자 기록 없음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                batters.forEach { slot ->
                    val highlight = g.status == GameStatus.LIVE &&
                        slot.name == g.currentBatterName &&
                        ((showLotte && g.isLotteBatting) || (!showLotte && !g.isLotteBatting))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable(enabled = slot.playerCode.isNotBlank()) { onPlayerClick(slot) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${slot.batOrder}", Modifier.width(22.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(playerCode = slot.playerCode, name = slot.name, size = 18.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(slot.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text(formatAvg(slot.seasonAvg), Modifier.width(36.dp), fontSize = 11.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${slot.todayAtBats}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("${slot.todayHits}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                        Text("${slot.todayRbi}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("${slot.todayRun}", Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentFiveCard(games: List<LotteGameInfo>) {
    SectionCard {
        Column {
            SectionHeader("최근 ${games.size}경기")
            games.forEachIndexed { idx, g ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.gameDate.takeLast(5), Modifier.width(48.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (g.isHome) "vs ${g.opponentName}" else "@ ${g.opponentName}",
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    Text(
                        "${g.lotteScore}:${g.opponentScore}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when {
                            g.lotteScore > g.opponentScore -> WinGreen
                            g.lotteScore < g.opponentScore -> LoseRed
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            g.lotteScore > g.opponentScore -> "승"
                            g.lotteScore < g.opponentScore -> "패"
                            else -> "무"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            g.lotteScore > g.opponentScore -> WinGreen
                            g.lotteScore < g.opponentScore -> LoseRed
                            else -> LotteGold
                        },
                    )
                }
                if (idx < games.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun CompactRefresh(secondsUntilRefresh: Int, isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !isRefreshing, onClick = onRefresh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (isRefreshing) {
            Text("...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            Text(
                "${secondsUntilRefresh}초",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Refresh, contentDescription = "새로고침", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private data class ChipSpec(val text: String, val color: Color)

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HeroTeam(name: String, logoUrl: String, score: Int, showScore: Boolean, highlight: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        TeamLogo(logoUrl, size = 52)
        Spacer(Modifier.height(6.dp))
        Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1)
        if (showScore) {
            Text("$score", fontSize = 42.sp, fontWeight = FontWeight.Black, color = if (highlight) LotteGold else Color.White)
        }
    }
}

@Composable
private fun HeroCard(g: LotteGameInfo, onShare: () -> Unit = {}) {
    val showScore = g.status == GameStatus.LIVE || g.status == GameStatus.ENDED
    Column(
        Modifier
            .fillMaxWidth()
            .background(heroGradient(), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            HeroTeam(
                name = if (g.isHome) g.opponentName else "롯데",
                logoUrl = if (g.isHome) g.opponentLogoUrl else g.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL },
                score = if (g.isHome) g.opponentScore else g.lotteScore,
                showScore = showScore,
                highlight = if (g.isHome) g.opponentScore > g.lotteScore else g.lotteScore > g.opponentScore,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val heroChips = buildList {
                    if (g.doubleHeaderNo > 0) add(ChipSpec("DH${g.doubleHeaderNo}", LotteGold))
                    if (g.lotteRank > 0 || g.opponentRank > 0) {
                        add(
                            ChipSpec(
                                "${if (g.isHome) g.opponentRank else g.lotteRank}·${if (g.isHome) g.lotteRank else g.opponentRank}위",
                                Color.White.copy(alpha = 0.75f),
                            ),
                        )
                    }
                    if (g.lineupAnnounced && g.status == GameStatus.BEFORE) {
                        add(ChipSpec("라인업", WinGreen))
                    }
                }
                if (heroChips.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(heroChips) { chip -> StatusChip(chip.text, chip.color) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                when (g.status) {
                    GameStatus.LIVE -> {
                        StatusChip("LIVE", LotteRed)
                        Spacer(Modifier.height(6.dp))
                        Text(g.inningLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LotteGold)
                    }
                    GameStatus.ENDED -> {
                        StatusChip("경기종료", Color(0xFF9AA7C4))
                        Spacer(Modifier.height(6.dp))
                        val (label, color) = when {
                            g.lotteScore > g.opponentScore -> "롯데 승" to WinGreen
                            g.lotteScore < g.opponentScore -> "롯데 패" to LoseRed
                            else -> "무승부" to LotteGold
                        }
                        Text(label, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
                    }
                    GameStatus.BEFORE -> {
                        StatusChip("오늘 ${g.startTime}", LotteGold)
                        Spacer(Modifier.height(6.dp))
                        Text("VS", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                    GameStatus.CANCELED -> StatusChip(g.cancelLabel, LoseRed)
                }
                if (g.stadium.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(g.stadium, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            HeroTeam(
                name = if (g.isHome) "롯데" else g.opponentName,
                logoUrl = if (g.isHome) g.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL } else g.opponentLogoUrl,
                score = if (g.isHome) g.lotteScore else g.opponentScore,
                showScore = showScore,
                highlight = if (g.isHome) g.lotteScore > g.opponentScore else g.opponentScore > g.lotteScore,
                modifier = Modifier.weight(1f),
            )
        }
        if (g.status == GameStatus.LIVE || g.status == GameStatus.ENDED) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onShare) {
                Text("공유", color = Color.White)
            }
        }
    }
}

@Composable
private fun RecentResultCard(g: LotteGameInfo) {
    SectionCard {
        Column {
            SectionHeader("최근 경기 결과")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(LOTTE_LOGO_URL, size = 36)
                Spacer(Modifier.width(8.dp))
                Text("롯데", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${g.lotteScore} : ${g.opponentScore}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        g.lotteScore > g.opponentScore -> WinGreen
                        g.lotteScore < g.opponentScore -> LoseRed
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(g.opponentName, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                TeamLogo(g.opponentLogoUrl, size = 36)
            }
        }
    }
}

@Composable
private fun NextGameContent(g: LotteGameInfo) {
    val dDay = remember(g.gameDate) {
        runCatching {
            val d = java.time.LocalDate.parse(g.gameDate.take(10))
            java.time.temporal.ChronoUnit.DAYS.between(com.bossxor.lottegiants.domain.kboToday(), d).toInt()
        }.getOrNull()
    }
    SectionCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다음 경기", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (dDay != null) {
                    Text(
                        when {
                            dDay == 0 -> "오늘"
                            dDay == 1 -> "내일"
                            dDay > 1 -> "D-$dDay"
                            else -> "종료"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(LOTTE_LOGO_URL, size = 48)
                Text("  vs  ", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TeamLogo(g.opponentLogoUrl, size = 48)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(g.opponentName, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        if (g.isHome) "사직 · 홈" else "${g.stadium.ifBlank { "원정" }}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${g.gameDate}  ${g.startTime}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(0.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionBanner(
    needNotif: Boolean,
    needBattery: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onOpenSettings,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        needNotif -> "알림 권한이 꺼져 있습니다"
                        else -> "배터리 예외가 필요합니다"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(
                    "실시간 스코어·위젯을 위해 설정에서 허용해 주세요.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                "닫기",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class KeyPlayerPick(
    val name: String,
    val playerCode: String,
    val metric: String,
    val label: String,
)

/**
 * 경기 중·종료면 오늘 실제로 활약한 타자, 경기 전이면 네이버 프리뷰가 최근 5경기로 뽑은 타자.
 * 둘 다 없을 때만 시즌 리더보드 상위 타자로 떨어지므로 날마다 같은 이름이 나오지 않는다.
 */
private fun pickKeyPlayer(
    game: LotteGameInfo?,
    leaders: List<com.bossxor.lottegiants.domain.LeaderPlayer>,
): KeyPlayerPick? {
    if (game == null) return null

    if (game.status == GameStatus.LIVE || game.status == GameStatus.ENDED) {
        val best = (game.lotteLineup + game.lotteBenchBatters)
            .filter { it.name.isNotBlank() }
            .filter { it.todayHits > 0 || it.todayRbi > 0 || it.todayRun > 0 }
            .maxByOrNull { it.todayHits * 3 + it.todayRbi * 2 + it.todayRun }
        if (best != null) {
            val metric = buildList {
                if (best.todayAtBats > 0) add("${best.todayAtBats}타수 ${best.todayHits}안타")
                else if (best.todayHits > 0) add("${best.todayHits}안타")
                if (best.todayRbi > 0) add("${best.todayRbi}타점")
                if (best.todayRun > 0) add("${best.todayRun}득점")
            }.joinToString(" · ")
            return KeyPlayerPick(best.name, best.playerCode, metric, "오늘의 키플레이어")
        }
    }

    val fromPreview = game.preview?.lotteKeyBatter?.takeIf { it.name.isNotBlank() }
    if (fromPreview != null) {
        val metric = when {
            fromPreview.recentAvg.isNotBlank() ->
                "최근 5경기 ${fromPreview.recentAvg}" +
                    if (fromPreview.recentRbi > 0) " · ${fromPreview.recentRbi}타점" else ""
            fromPreview.vsOpponentAvg.isNotBlank() ->
                "${game.opponentName}전 ${fromPreview.vsOpponentAvg}"
            fromPreview.avg.isNotBlank() -> "시즌 ${fromPreview.avg}"
            else -> ""
        }
        return KeyPlayerPick(fromPreview.name, fromPreview.playerCode, metric, "주목할 타자")
    }

    val leader = leaders.firstOrNull { it.isLotte && !it.isPitcher } ?: return null
    val metric = when {
        leader.avg.isNotBlank() -> "시즌 타율 ${leader.avg}"
        leader.ops.isNotBlank() -> "시즌 OPS ${leader.ops}"
        leader.hr > 0 -> "시즌 ${leader.hr}홈런"
        else -> ""
    }
    return KeyPlayerPick(leader.name, leader.playerCode, metric, "시즌 팀 최고 타자")
}

@Composable
private fun KeyPlayerChip(
    leaders: List<com.bossxor.lottegiants.domain.LeaderPlayer>,
    game: LotteGameInfo?,
    onClick: (String, String) -> Unit,
) {
    val pick = pickKeyPlayer(game, leaders) ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = { onClick(pick.playerCode, pick.name) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pick.label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(pick.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            if (pick.metric.isNotBlank()) {
                Text(pick.metric, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLoadError(message: String, onRetry: () -> Unit) {
    if (message.isBlank()) return
    SectionCard {
        Column(Modifier.fillMaxWidth()) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "다시 시도",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
