package com.bossxor.lottegiants.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.WinGreen
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.TeamLogo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    selectedDate: LocalDate,
    games: List<MiniGame>,
    loading: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    calendarMonth: YearMonth,
    monthGames: List<MiniGame>,
    onSelectMonth: (YearMonth) -> Unit,
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
) {
    var mode by remember { mutableIntStateOf(0) } // 0 list, 1 calendar
    val today = remember { LocalDate.now() }
    val listMonth = remember(selectedDate) { YearMonth.from(selectedDate) }
    val monthDates = remember(listMonth) {
        (1..listMonth.lengthOfMonth()).map { listMonth.atDay(it) }
    }
    val dateListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var swipeLock by remember { mutableStateOf(false) }
    var dateNavDirection by remember { mutableIntStateOf(1) }
    var lastSyncedMonth by remember { mutableStateOf(listMonth) }

    LaunchedEffect(selectedDate, mode) {
        if (mode != 0 || monthDates.isEmpty()) return@LaunchedEffect
        val index = (selectedDate.dayOfMonth - 1).coerceIn(0, monthDates.lastIndex)
        val monthNow = YearMonth.from(selectedDate)
        val monthChanged = monthNow != lastSyncedMonth
        lastSyncedMonth = monthNow
        if (monthChanged) {
            dateListState.scrollToItem(index)
            delay(16)
            centerDateChip(dateListState, index, animate = false)
        } else {
            val onScreen = dateListState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (onScreen) {
                centerDateChip(dateListState, index, animate = true, thresholdPx = 28)
            } else {
                dateListState.animateScrollToItem(index)
                delay(16)
                centerDateChip(dateListState, index, animate = true, thresholdPx = 12)
            }
        }
    }

    fun shiftDay(delta: Long) {
        dateNavDirection = if (delta >= 0) 1 else -1
        onSelectDate(selectedDate.plusDays(delta))
    }

    fun withSwipeLock(block: () -> Unit) {
        if (swipeLock) return
        swipeLock = true
        block()
        scope.launch {
            delay(220)
            swipeLock = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "결과",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                ModeChip("리스트", mode == 0) { mode = 0 }
                Spacer(Modifier.width(6.dp))
                ModeChip("캘린더", mode == 1) { mode = 1 }
            }
            Spacer(Modifier.height(10.dp))

            if (mode == 0) {
                var lotteOnly by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("전체", !lotteOnly) { lotteOnly = false }
                    ModeChip("롯데만", lotteOnly) { lotteOnly = true }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
                    }
                    AnimatedContent(
                        targetState = selectedDate,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            val dir = dateNavDirection
                            (
                                fadeIn(tween(180)) +
                                    slideInHorizontally(tween(200)) { full -> dir * full / 4 }
                                ) togetherWith (
                                fadeOut(tween(140)) +
                                    slideOutHorizontally(tween(180)) { full -> -dir * full / 4 }
                                )
                        },
                        label = "result_date_title",
                    ) { date ->
                        Text(
                            date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = { shiftDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
                    }
                }
                LazyRow(
                    state = dateListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    items(monthDates, key = { it.toString() }) { date ->
                        DateChip(
                            date = date,
                            selected = date == selectedDate,
                            isToday = date == today,
                            onClick = {
                                dateNavDirection = if (date.isAfter(selectedDate)) 1 else -1
                                onSelectDate(date)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val visibleGames = remember(games, lotteOnly) {
                    val filtered = if (lotteOnly) {
                        games.filter {
                            it.homeTeamCode == LOTTE_TEAM_CODE || it.awayTeamCode == LOTTE_TEAM_CODE ||
                                it.homeName.contains("롯데") || it.awayName.contains("롯데")
                        }
                    } else games
                    filtered.sortedWith(lotteFirstComparator())
                }
                val lotteGames = visibleGames.filter {
                    it.homeTeamCode == LOTTE_TEAM_CODE || it.awayTeamCode == LOTTE_TEAM_CODE ||
                        it.homeName.contains("롯데") || it.awayName.contains("롯데")
                }
                val swipeModifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .swipeChangeDay(
                        enabled = !swipeLock,
                        onSwipe = { delta -> withSwipeLock { shiftDay(delta) } },
                    )
                when {
                    loading && games.isEmpty() -> {
                        Box(swipeModifier, contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    visibleGames.isEmpty() -> {
                        Box(swipeModifier) {
                            EmptyRetry(
                                message = "해당 일자 KBO 경기가 없습니다.",
                                onRetry = onRefresh,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = swipeModifier,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (lotteGames.size >= 2) {
                                item {
                                    Text("더블헤더 · 롯데 ${lotteGames.size}경기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            items(visibleGames, key = { it.gameId }) { g ->
                                val dhIndex = lotteGames.indexOfFirst { it.gameId == g.gameId }
                                if (lotteGames.size >= 2 && dhIndex >= 0) {
                                    Text("${dhIndex + 1}경기", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                ResultGameCard(g)
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            } else {
                CalendarMonthView(
                    modifier = Modifier.weight(1f),
                    month = calendarMonth,
                    selectedDate = selectedDate,
                    monthGames = monthGames,
                    dayGames = games,
                    today = today,
                    onSelectDate = onSelectDate,
                    onSelectMonth = onSelectMonth,
                    onRetry = onRefresh,
                    onSwipeDay = { delta -> withSwipeLock { shiftDay(delta) } },
                    swipeEnabled = !swipeLock,
                )
            }
        }
    }
}

private suspend fun centerDateChip(
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    animate: Boolean,
    thresholdPx: Int = 8,
) {
    val layout = state.layoutInfo
    val viewport = layout.viewportEndOffset - layout.viewportStartOffset
    if (viewport <= 0) return
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val centered = item.offset - (viewport - item.size) / 2
    if (abs(centered) <= thresholdPx) return
    if (animate) {
        state.animateScrollToItem(index, scrollOffset = centered)
    } else {
        state.scrollToItem(index, scrollOffset = centered)
    }
}

@Composable
fun EmptyRetry(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "다시 시도",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun DateChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val targetBg = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val targetFg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bg by animateColorAsState(targetBg, animationSpec = tween(180), label = "date_chip_bg")
    val fg by animateColorAsState(targetFg, animationSpec = tween(180), label = "date_chip_fg")
    Column(
        Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(koreanDow(date.dayOfWeek), fontSize = 11.sp, color = fg.copy(alpha = 0.8f))
        Text("${date.dayOfMonth}", fontWeight = FontWeight.Bold, color = fg)
        if (isToday && !selected) {
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun CalendarMonthView(
    modifier: Modifier = Modifier,
    month: YearMonth,
    selectedDate: LocalDate,
    monthGames: List<MiniGame>,
    dayGames: List<MiniGame>,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    onSwipeDay: (Long) -> Unit = {},
    swipeEnabled: Boolean = true,
) {
    val byDate = remember(monthGames) {
        monthGames.groupBy { it.gameDate.takeIf { d -> d.isNotBlank() } ?: "" }
    }
    val firstDow = month.atDay(1).dayOfWeek.value % 7
    val cells = remember(month) {
        buildList {
            repeat(firstDow) { add(null as LocalDate?) }
            for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
            while (size % 7 != 0) add(null)
        }
    }
    val selectedGames = dayGames.ifEmpty {
        monthGames.filter { it.gameDate == selectedDate.toString() }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .swipeChangeDay(enabled = swipeEnabled, onSwipe = onSwipeDay),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelectMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onSelectMonth(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, w ->
                Text(
                    w,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = when (i) {
                        0 -> LoseRed
                        6 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(0.85f)) {
                            if (date != null) {
                                val key = date.toString()
                                val cellGames = byDate[key].orEmpty()
                                val lotte = cellGames.firstOrNull { it.isLotteGame() }
                                val lotteHome = lotte?.isLotteHome()
                                val ended = lotte?.status == GameStatus.ENDED
                                val lotteWon = lotte?.lotteResult() == true
                                val lotteLost = lotte?.lotteResult() == false
                                val draw = ended && lotte != null && lotte.homeScore == lotte.awayScore
                                val cellLabel = when {
                                    ended && lotteWon -> "승"
                                    ended && lotteLost -> "패"
                                    ended && draw -> "무"
                                    lotteHome == true -> "홈"
                                    lotteHome == false -> "원정"
                                    else -> null
                                }
                                val cellLabelColor = when {
                                    ended && lotteWon -> WinGreen
                                    ended && lotteLost -> LoseRed
                                    ended && draw -> LotteGold
                                    lotteHome == true -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val border = when {
                                    date == selectedDate -> MaterialTheme.colorScheme.primary
                                    date == today -> MaterialTheme.colorScheme.outline
                                    else -> Color.Transparent
                                }
                                val borderWidth = if (date == selectedDate) 2.dp else 1.dp
                                val cellBg = when {
                                    ended && lotteWon -> WinGreen.copy(alpha = 0.10f)
                                    ended && lotteLost -> LoseRed.copy(alpha = 0.10f)
                                    ended && draw -> LotteGold.copy(alpha = 0.12f)
                                    lotteHome == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    lotteHome == false -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    else -> Color.Transparent
                                }
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(borderWidth, border, RoundedCornerShape(8.dp))
                                        .background(cellBg)
                                        .clickable { onSelectDate(date) }
                                        .padding(horizontal = 2.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        fontSize = 11.sp,
                                        fontWeight = if (date == selectedDate) FontWeight.Bold else FontWeight.Medium,
                                        color = when (date.dayOfWeek) {
                                            DayOfWeek.SUNDAY -> LoseRed
                                            DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (cellLabel != null) {
                                        Text(
                                            cellLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = cellLabelColor,
                                            maxLines = 1,
                                        )
                                    } else if (date == today) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    } else if (cellGames.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("승", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WinGreen)
            Text("패", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LoseRed)
            Text("무", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LotteGold)
            Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("홈/원정 = 예정·진행", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 경기", Locale.KOREAN)),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (selectedGames.isEmpty()) {
            EmptyRetry(message = "경기가 없습니다.", onRetry = onRetry)
        } else {
            selectedGames.sortedWith(lotteFirstComparator()).forEach { g ->
                ResultGameCard(g)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ResultGameCard(g: MiniGame) {
    val lotte = g.isLotteGame()
    val won = g.lotteResult()
    val lotteHome = g.isLotteHome()
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(g.awayLogoUrl, size = 22)
                    Spacer(Modifier.width(6.dp))
                    Text(g.awayName, fontWeight = if (g.awayName.contains("롯데")) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text("vs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(g.homeName, fontWeight = if (g.homeName.contains("롯데")) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    TeamLogo(g.homeLogoUrl, size = 22)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (lotteHome != null) {
                        StatusPill(
                            if (lotteHome) "홈" else "원정",
                            if (lotteHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (g.doubleHeaderNo > 0) {
                        StatusPill("DH${g.doubleHeaderNo}", LotteGold)
                    }
                    val lotteRank = if (g.homeTeamCode == com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE) {
                        g.homeRank
                    } else {
                        g.awayRank
                    }
                    if (lotteRank > 0) {
                        StatusPill("${lotteRank}위", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (g.lineupAnnounced && g.status == GameStatus.BEFORE) {
                        StatusPill("라인업", WinGreen)
                    }
                    when (g.status) {
                        GameStatus.LIVE -> StatusPill("LIVE", LotteRed)
                        GameStatus.ENDED -> {
                            when {
                                won == true -> StatusPill("롯데 승", WinGreen)
                                won == false -> StatusPill("롯데 패", LoseRed)
                                lotte && g.homeScore == g.awayScore -> StatusPill("무승부", LotteGold)
                                else -> StatusPill("종료", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        GameStatus.BEFORE -> StatusPill(g.startTime.ifBlank { "예정" }, LotteGold)
                        GameStatus.CANCELED -> StatusPill(
                            g.statusText.ifBlank { "취소" },
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (g.stadium.isNotBlank() || g.broadChannel.isNotBlank()) {
                    Text(
                        listOf(g.stadium, g.broadChannel).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (g.status == GameStatus.ENDED && (g.winPitcherName.isNotBlank() || g.losePitcherName.isNotBlank())) {
                    Text(
                        "승 ${g.winPitcherName.ifBlank { "-" }}  ·  패 ${g.losePitcherName.ifBlank { "-" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (g.status != GameStatus.BEFORE && g.status != GameStatus.CANCELED) {
                Text(
                    "${g.awayScore} : ${g.homeScore}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        won == true -> WinGreen
                        won == false -> LoseRed
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

private fun MiniGame.isLotteGame(): Boolean =
    homeName.contains("롯데") || awayName.contains("롯데") ||
        homeTeamCode == LOTTE_TEAM_CODE || awayTeamCode == LOTTE_TEAM_CODE

/** 롯데 경기면 홈 여부, 아니면 null */
private fun MiniGame.isLotteHome(): Boolean? {
    if (!isLotteGame()) return null
    return homeName.contains("롯데") || homeTeamCode == LOTTE_TEAM_CODE
}

private fun MiniGame.lotteResult(): Boolean? {
    if (status != GameStatus.ENDED || !isLotteGame()) return null
    if (homeScore == awayScore) return null
    val lotteHome = homeName.contains("롯데") || homeTeamCode == LOTTE_TEAM_CODE
    val lotteScore = if (lotteHome) homeScore else awayScore
    val oppScore = if (lotteHome) awayScore else homeScore
    return lotteScore > oppScore
}

private fun lotteFirstComparator(): Comparator<MiniGame> =
    compareByDescending<MiniGame> { it.isLotteGame() }.thenBy { it.startTime }

private fun koreanDow(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

/** 경기 목록·캘린더 하단에서 좌우 스와이프로 날짜 이동 */
private fun Modifier.swipeChangeDay(
    enabled: Boolean,
    onSwipe: (Long) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    then(
        pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                onDragEnd = {
                    when {
                        total > 80f -> onSwipe(-1)
                        total < -80f -> onSwipe(1)
                    }
                    total = 0f
                },
            )
        },
    )
}
