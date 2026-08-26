package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.DayEntryChanges
import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.WinGreen
import com.bossxor.lottegiants.ui.components.SectionCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EntryBoardScreen(
    selectedDate: LocalDate,
    dayEntry: DayEntryChanges?,
    loading: Boolean,
    recentMoves: List<RosterMove>,
    changeDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onPlayerClick: (EntryPlayer) -> Unit = {},
    teamName: String = "롯데",
) {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val dates = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
    val listState = rememberLazyListState()
    val last7 = remember(today) { (0..6).map { today.minusDays(it.toLong()) } }
    val movesByDate = remember(recentMoves) { recentMoves.groupBy { it.moveDate } }
    val scope = rememberCoroutineScope()
    var swipeLock by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) { month = YearMonth.from(selectedDate) }
    LaunchedEffect(month, selectedDate) {
        val idx = dates.indexOf(selectedDate).coerceAtLeast(0)
        listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
    }

    fun withSwipeLock(block: () -> Unit) {
        if (swipeLock) return
        swipeLock = true
        block()
        scope.launch {
            delay(280)
            swipeLock = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(top = 4.dp, bottom = 4.dp)
            .pointerInput(selectedDate) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            total > 80f -> withSwipeLock { onSelectDate(selectedDate.minusDays(1)) }
                            total < -80f -> withSwipeLock { onSelectDate(selectedDate.plusDays(1)) }
                        }
                        total = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                )
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Column(Modifier.weight(1f)) {
                Text("엔트리 등말소", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("KBO 공시 · $teamName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("날짜별 상세", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = {
                val next = month.minusMonths(1)
                month = next
                onSelectDate(next.atDay(selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth())))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy.MM", Locale.KOREA)),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            IconButton(onClick = {
                val next = month.plusMonths(1)
                month = next
                onSelectDate(next.atDay(selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth())))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(dates, key = { it.toString() }) { date ->
                EntryDateChip(
                    date = date,
                    selected = date == selectedDate,
                    isToday = date == today,
                    hasChange = date in changeDates,
                    onClick = { onSelectDate(date) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREA)),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {
                        val reg = dayEntry?.registered.orEmpty()
                        val rem = dayEntry?.removed.orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (reg.isEmpty() && rem.isEmpty()) {
                                SectionCard {
                                    Text("이 날 ${teamName} 등말소 공시가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                if (reg.isNotEmpty()) {
                                    ChangeBlock(title = "등록", accent = WinGreen, players = reg, onPlayerClick = onPlayerClick)
                                }
                                if (rem.isNotEmpty()) {
                                    ChangeBlock(title = "말소", accent = LoseRed, players = rem, onPlayerClick = onPlayerClick)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("최근 7일 변동", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(last7, key = { it.toString() }) { day ->
                val key = day.toString()
                val dayMoves = movesByDate[key].orEmpty()
                val reg = dayMoves.filter { it.isRegister }
                val rem = dayMoves.filter { !it.isRegister }
                SectionCard(padding = 12.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                day.format(DateTimeFormatter.ofPattern("MM.dd (E)", Locale.KOREA)),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (dayMoves.isEmpty()) "변동 없음" else "등록 ${reg.size} · 말소 ${rem.size}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (dayMoves.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            reg.forEach { MoveLine(it.playerName, true) }
                            rem.forEach { MoveLine(it.playerName, false) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MoveLine(name: String, register: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(if (register) WinGreen else LoseRed, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(if (register) "등록" else "말소", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChangeBlock(
    title: String,
    accent: Color,
    players: List<EntryPlayer>,
    onPlayerClick: (EntryPlayer) -> Unit = {},
) {
    SectionCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("${players.size}명", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (players.isEmpty()) {
                Text("없음", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                players.forEachIndexed { i, p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick(p) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (p.backNumber.isNotBlank()) "No.${p.backNumber}" else "-",
                            Modifier.width(52.dp),
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            fontSize = 13.sp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val sub = listOfNotNull(
                                p.position.takeIf { it.isNotBlank() },
                                p.hitType.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (i < players.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryDateChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    hasChange: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            weekdayKo(date.dayOfWeek),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${date.dayOfMonth}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> MaterialTheme.colorScheme.onSurface
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(22.dp)
                .height(3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(5.dp)
                .background(
                    if (hasChange) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
        )
    }
}

private fun weekdayKo(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}
