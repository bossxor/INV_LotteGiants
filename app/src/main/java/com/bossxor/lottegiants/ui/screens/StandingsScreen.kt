package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.components.ScreenTitle
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.SparklineChart
import com.bossxor.lottegiants.ui.components.TeamLogo
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StandingsScreen(
    standings: List<TeamStanding>,
    teamCard: LotteTeamCard? = null,
    batterLeaders: List<LeaderPlayer> = emptyList(),
    pitcherLeaders: List<LeaderPlayer> = emptyList(),
    onOpenLeaders: () -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
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
                ScreenTitle("순위", "한 팀을 누르면 그 팀 기준 게임차")
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
                                Modifier.weight(0.10f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "팀",
                                Modifier.weight(0.27f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "경기",
                                Modifier.weight(0.11f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "승-무-패",
                                Modifier.weight(0.20f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "승률",
                                Modifier.weight(0.14f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "게임차",
                                Modifier.weight(0.18f),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .width(3.dp)
                    .height(12.dp)
                    .background(LotteRed, RoundedCornerShape(2.dp)),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 11.dp, top = 2.dp),
        )
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
    val accent = MaterialTheme.colorScheme.primary

    val border = BorderStroke(
        0.5.dp,
        if (isBase) accent.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
        color = if (isBase) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textColor = if (isBase) accent else MaterialTheme.colorScheme.onSurface
            val weight = when {
                isBase || isLotte -> FontWeight.Bold
                else -> FontWeight.Medium
            }
            Text(
                "${t.ranking}",
                Modifier.weight(0.10f),
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
            Row(Modifier.weight(0.27f), verticalAlignment = Alignment.CenterVertically) {
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
                Modifier.weight(0.11f),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                "${t.win}-${t.draw}-${t.lose}",
                Modifier.weight(0.20f),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                String.format(Locale.US, "%.3f", t.wra),
                Modifier.weight(0.14f),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
            Text(
                formatGb(gb),
                Modifier.weight(0.18f),
                color = textColor,
                fontWeight = weight,
                fontSize = 12.sp,
            )
        }
    }
}
