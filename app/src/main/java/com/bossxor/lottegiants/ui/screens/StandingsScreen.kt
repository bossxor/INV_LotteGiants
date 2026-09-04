package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.RaceSummary
import com.bossxor.lottegiants.domain.RemainingOpponent
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.formatMdDate
import com.bossxor.lottegiants.domain.formatStandingStreak
import com.bossxor.lottegiants.domain.isTeamHome
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.lotteRaceSummary
import com.bossxor.lottegiants.domain.remainingOpponentsFrom
import com.bossxor.lottegiants.domain.shareRaceText
import com.bossxor.lottegiants.domain.teamCodeToName
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.WinGreen
import com.bossxor.lottegiants.ui.ScoreShare
import com.bossxor.lottegiants.ui.components.ScreenTitle
import androidx.compose.ui.platform.LocalContext
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.SparklineChart
import com.bossxor.lottegiants.ui.components.TeamLogo
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(
    standings: List<TeamStanding>,
    teamCard: LotteTeamCard? = null,
    batterLeaders: List<LeaderPlayer> = emptyList(),
    pitcherLeaders: List<LeaderPlayer> = emptyList(),
    onOpenLeaders: () -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    seasonGames: List<MiniGame> = emptyList(),
    onAppear: () -> Unit = {},
) {
    var baseTeamId by remember(standings) {
        mutableStateOf(
            standings.firstOrNull { it.teamId == LOTTE_TEAM_CODE }?.teamId
                ?: standings.firstOrNull()?.teamId.orEmpty(),
        )
    }
    val baseTeam = remember(standings, baseTeamId) {
        standings.firstOrNull { it.teamId == baseTeamId }
    }
    val lotteStanding = remember(standings) {
        standings.firstOrNull { it.teamId == LOTTE_TEAM_CODE }
    }
    val remaining = remember(seasonGames) {
        remainingOpponentsFrom(seasonGames, kboToday().toString())
    }
    val race = remember(standings, seasonGames) {
        lotteRaceSummary(standings, remainingOpponents = remaining, seasonGames = seasonGames, todayIso = kboToday().toString())
    }
    val context = LocalContext.current

    LaunchedEffect(Unit) { onAppear() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                ScreenTitle("순위")
            }

            race?.let { summary ->
                item {
                    RaceStatusCard(
                        summary = summary,
                        onShare = {
                            ScoreShare.shareText(context, "사직스코어 레이스", shareRaceText(summary))
                        },
                    )
                }
                val homeLeft = summary.remainingOpponents.sumOf { it.home }
                val awayLeft = summary.remainingOpponents.sumOf { it.away }
                if (homeLeft + awayLeft > 0 || summary.remainingOpponents.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "잔여 상대",
                            subtitle = buildString {
                                if (homeLeft > 0) append("홈 $homeLeft")
                                if (homeLeft > 0 && awayLeft > 0) append(" · ")
                                if (awayLeft > 0) append("원정 $awayLeft")
                            },
                        )
                    }
                    item {
                        SectionCard {
                            RemainingOpponentsGrid(summary.remainingOpponents)
                        }
                    }
                }
                if (summary.upcomingGames.isNotEmpty()) {
                    item {
                        val first = summary.upcomingGames.first()
                        val last = summary.upcomingGames.last()
                        SectionHeader(
                            title = "다음 ${summary.upcomingGames.size}경기",
                            subtitle = if (first.gameDate == last.gameDate) {
                                formatMdDate(first.gameDate)
                            } else {
                                "${formatMdDate(first.gameDate)} ~ ${formatMdDate(last.gameDate)}"
                            },
                        )
                    }
                    item {
                        SectionCard(padding = 8.dp) {
                            UpcomingGamesList(summary.upcomingGames)
                        }
                    }
                }
            }

            // ── 섹션 1: 롯데 시즌 트렌드 ──
            if (teamCard != null) {
                item {
                    SectionHeader(
                        title = "롯데 시즌 트렌드",
                        subtitle = "순위 변동 · 주간 타율 · 주간 방어율",
                    )
                }
                item {
                    KeuboStyleCharts(teamCard)
                }
            }

            // ── 섹션 2: 선수 타이틀 ──
            item {
                SectionHeader(
                    title = "선수 타이틀",
                    subtitle = "리그 타자 · 투수 순위",
                )
            }
            item {
                SectionCard(modifier = Modifier.clickable(onClick = onOpenLeaders)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("타이틀 순위", fontWeight = FontWeight.Bold)
                            Text(
                                "타자 · 투수",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "전체 보기 ›",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ── 섹션 3: KBO 순위표 ──
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 6.dp),
                    ) {
                        SectionHeader(
                            title = "KBO 순위표",
                            subtitle = "게임차 = ${baseTeam?.teamName ?: "롯데"} 기준 · 탭으로 기준팀 변경",
                        )
                        if (lotteStanding != null) {
                            Spacer(Modifier.height(6.dp))
                            StandingRow(
                                t = lotteStanding,
                                base = baseTeam,
                                isBase = lotteStanding.teamId == baseTeamId,
                                isLotte = true,
                                compact = true,
                                onClick = { baseTeamId = lotteStanding.teamId },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                            Text(
                                "순위",
                                Modifier.weight(StandingCol.rank),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "팀",
                                Modifier.weight(StandingCol.team),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "경기",
                                Modifier.weight(StandingCol.games),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "승-무-패",
                                Modifier.weight(StandingCol.record),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "연속",
                                Modifier.weight(StandingCol.streak),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "승률",
                                Modifier.weight(StandingCol.wra),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "게임차",
                                Modifier.weight(StandingCol.gb),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }

            if (standings.isEmpty()) {
                item {
                    EmptyRetry(message = "순위 데이터를 불러오지 못했습니다.", onRetry = onRefresh)
                }
            } else {
                itemsIndexed(standings, key = { _, t -> t.teamId }) { index, t ->
                    Column {
                        // 5–6위 사이 포스트시즌 컷
                        if (index == 5) {
                            PostseasonCutLine()
                            Spacer(Modifier.height(6.dp))
                        }
                        StandingRow(
                            t = t,
                            base = baseTeam,
                            isBase = t.teamId == baseTeamId,
                            isLotte = t.teamId == LOTTE_TEAM_CODE,
                            onClick = { baseTeamId = t.teamId },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PostseasonCutLine() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        )
        Text(
            "포스트시즌 컷",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun KeuboStyleCharts(teamCard: LotteTeamCard) {
    val primary = MaterialTheme.colorScheme.primary
    val rankValues = teamCard.rankHistory.map { it.rank.toDouble() }
    val battingValues = teamCard.weeklyBatting.map { it.value }
    val pitchingValues = teamCard.weeklyPitching.map { it.value }
    val lastAvg = teamCard.weeklyBatting.lastOrNull()?.value
    val lastEra = teamCard.weeklyPitching.lastOrNull()?.value
    val avgText = lastAvg?.let { String.format(Locale.US, ".%03d", ((it % 1) * 1000).toInt()) }
    val eraText = lastEra?.let { String.format(Locale.US, "%.2f", it) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard(modifier = Modifier.weight(1.15f), padding = 8.dp) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "시즌 순위 변동",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (teamCard.currentRank > 0) {
                        Text(
                            "${teamCard.currentRank}위",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = primary,
                        )
                    }
                }
                if (teamCard.streak.isNotBlank()) {
                    Text(
                        teamCard.streak,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (rankValues.size >= 2) {
                    SparklineChart(
                        values = rankValues,
                        invertY = true,
                        yMin = 1.0,
                        yMax = 10.0,
                        lineColor = primary,
                        height = 104.dp,
                        strokeWidth = 2f,
                    )
                } else {
                    Text("데이터 준비 중", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactSparkCard(
                title = "주간 팀 타율",
                value = avgText,
                rank = teamCard.weeklyBattingRank?.let { "${it}위" },
                values = battingValues,
                lineColor = LotteGold,
            )
            CompactSparkCard(
                title = "주간 팀 방어율",
                value = eraText,
                rank = teamCard.weeklyPitchingRank?.let { "${it}위" },
                values = pitchingValues,
                lineColor = primary,
            )
        }
    }
}

@Composable
private fun CompactSparkCard(
    title: String,
    value: String?,
    rank: String?,
    values: List<Double>,
    lineColor: Color,
) {
    SectionCard(padding = 8.dp) {
        Column {
            Text(
                buildString {
                    append(title)
                    if (!value.isNullOrBlank()) append(" $value")
                    if (!rank.isNullOrBlank()) append(" ($rank)")
                },
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            if (values.size >= 2) {
                SparklineChart(
                    values = values,
                    lineColor = lineColor,
                    height = 40.dp,
                    strokeWidth = 1.8f,
                    fillAlpha = 0.06f,
                )
            } else {
                Text("데이터 준비 중", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

private val HomeBlue = Color(0xFF2F6FED)
private val AwayRed = Color(0xFFE23B3B)

@Composable
private fun RaceStatusCard(summary: RaceSummary, onShare: () -> Unit) {
    val formLine = summary.lines.firstOrNull { it.startsWith("최근") }.orEmpty()
    val raceLines = summary.lines.filterNot {
        it.startsWith("최근") || it.startsWith("잔여 홈")
    }
    SectionCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.headline,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = LotteRed,
                )
                Text(
                    "공유",
                    modifier = Modifier.clickable(onClick = onShare),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (formLine.isNotBlank()) {
                RecentFormLine(formLine)
            }
            raceLines.forEach { line ->
                Text(
                    line,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentFormLine(line: String) {
    val marks = line.substringAfter("경기 ").trim()
    val label = line.substringBefore("경기 ").trim() + "경기"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            marks.forEach { ch ->
                val color = when (ch) {
                    '승' -> WinGreen
                    '패' -> LoseRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text("$ch", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemainingOpponentsGrid(opponents: List<RemainingOpponent>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opponents.forEach { opp ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TeamLogo(teamLogoUrl(opp.code), size = 22)
                    Text(opp.name.ifBlank { opp.code }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${opp.games}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingGamesList(games: List<MiniGame>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        games.forEachIndexed { index, g ->
            UpcomingGameRow(g)
            if (index < games.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun UpcomingGameRow(g: MiniGame) {
    val home = g.isTeamHome(LOTTE_TEAM_CODE) == true
    val oppCode = (if (home) g.awayTeamCode else g.homeTeamCode).ifBlank {
        if (home) g.awayName else g.homeName
    }
    val oppName = teamCodeToName(oppCode).ifBlank { if (home) g.awayName else g.homeName }
    val time = g.startTime.trim()
    val stadium = g.stadium.trim().ifBlank { if (home) "사직" else "" }
    val haColor = if (home) HomeBlue else AwayRed
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TeamLogo(teamLogoUrl(oppCode), size = 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    buildString {
                        append(formatMdDate(g.gameDate))
                        if (time.isNotBlank()) {
                            append("  ")
                            append(time)
                        }
                        if (g.doubleHeaderNo > 0) append("  DH${g.doubleHeaderNo}")
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(oppName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (stadium.isNotBlank()) {
                Text(stadium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = haColor.copy(alpha = 0.12f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Text(
                if (home) "홈" else "원정",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = haColor,
            )
        }
    }
}

private object StandingCol {
    const val rank = 0.09f
    const val team = 0.22f
    const val games = 0.10f
    const val record = 0.17f
    const val streak = 0.11f
    const val wra = 0.14f
    const val gb = 0.17f
}

private fun gamesBehindVsBase(team: TeamStanding, base: TeamStanding?): Double? {
    if (base == null) return null
    if (team.teamId == base.teamId) return 0.0
    return ((team.win - team.lose) - (base.win - base.lose)) / 2.0
}

private fun formatGb(gb: Double?): String {
    if (gb == null) return "-"
    if (abs(gb) < 0.05) return "-"
    val absStr = if (gb % 1.0 == 0.0) {
        abs(gb).toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", abs(gb))
    }
    return if (gb > 0) "+$absStr" else "-$absStr"
}

@Composable
private fun StandingRow(
    t: TeamStanding,
    base: TeamStanding?,
    isBase: Boolean,
    onClick: () -> Unit,
    isLotte: Boolean = false,
    compact: Boolean = false,
) {
    val gb = gamesBehindVsBase(t, base)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
        color = if (isBase) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textColor = if (isLotte) LotteRed else MaterialTheme.colorScheme.onSurface
            val weight = when {
                isBase || isLotte -> FontWeight.Bold
                else -> FontWeight.Medium
            }
            Text(
                "${t.ranking}",
                Modifier.weight(StandingCol.rank),
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
            Row(Modifier.weight(StandingCol.team), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(teamLogoUrl(t.teamId), size = if (compact) 22 else 26)
                Spacer(Modifier.width(6.dp))
                Text(
                    t.teamName,
                    color = textColor,
                    fontWeight = weight,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            Text(
                "${t.gameCount}",
                Modifier.weight(StandingCol.games),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                "${t.win}-${t.draw}-${t.lose}",
                Modifier.weight(StandingCol.record),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            val streak = formatStandingStreak(t.streak)
            Text(
                streak,
                Modifier.weight(StandingCol.streak),
                color = when {
                    streak.endsWith("승") -> WinGreen
                    streak.endsWith("패") -> LoseRed
                    else -> textColor
                },
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                String.format(Locale.US, "%.3f", t.wra),
                Modifier.weight(StandingCol.wra),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                formatGb(gb),
                Modifier.weight(StandingCol.gb),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
        }
    }
}
