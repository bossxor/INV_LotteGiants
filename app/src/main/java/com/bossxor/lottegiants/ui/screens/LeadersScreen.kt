package com.bossxor.lottegiants.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.TitleRankEntry
import com.bossxor.lottegiants.domain.matchesTeam
import com.bossxor.lottegiants.domain.teamCodeToName
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.teamNameToCode
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.components.PlayerAvatar
import com.bossxor.lottegiants.ui.components.ScreenTitle
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.TeamLogo
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private data class TitleCategory(
    val key: String,
    val title: String,
    val requireQualified: Boolean,
    val ascending: Boolean,
    val valueOf: (LeaderPlayer) -> Double?,
    val format: (Double) -> String,
)

private val BATTER_TITLES = listOf(
    TitleCategory("avg", "타율", true, false, { it.avg.toDoubleOrNull() }) { formatRate(it) },
    TitleCategory("hr", "홈런", false, false, { it.hr.toDouble() }) { "${it.toInt()}" },
    TitleCategory("rbi", "타점", false, false, { it.rbi.toDouble() }) { "${it.toInt()}" },
    TitleCategory("hits", "안타", false, false, { it.hits.toDouble() }) { "${it.toInt()}" },
    TitleCategory("sb", "도루", false, false, { it.sb.toDouble() }) { "${it.toInt()}" },
    TitleCategory("ops", "OPS", true, false, { it.ops.toDoubleOrNull() }) { formatOps(it) },
    TitleCategory("obp", "출루율", true, false, { it.obp.toDoubleOrNull() }) { formatRate(it) },
    TitleCategory("slg", "장타율", true, false, { it.slg.toDoubleOrNull() }) { formatRate(it) },
)

private val PITCHER_TITLES = listOf(
    TitleCategory("era", "평균자책", true, true, { it.era.toDoubleOrNull() }) { formatEra(it) },
    TitleCategory("wins", "승리", false, false, { it.wins.toDouble() }) { "${it.toInt()}" },
    TitleCategory("so", "탈삼진", false, false, { it.so.toDouble() }) { "${it.toInt()}" },
    TitleCategory("saves", "세이브", false, false, { it.saves.toDouble() }) { "${it.toInt()}" },
    TitleCategory("holds", "홀드", false, false, { it.holds.toDouble() }) { "${it.toInt()}" },
    TitleCategory("whip", "WHIP", true, true, { it.whip.toDoubleOrNull() }) { formatEra(it) },
)

@Composable
fun LeadersScreen(
    batters: List<LeaderPlayer>,
    pitchers: List<LeaderPlayer>,
    favoriteCodes: Set<String> = emptySet(),
    onBack: (() -> Unit)? = null,
    onPlayerClick: (LeaderPlayer) -> Unit = {},
    onToggleFavorite: (LeaderPlayer) -> Unit = {},
    filterTeamCode: String = LOTTE_TEAM_CODE,
    onRetry: (() -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    var teamOnly by remember(filterTeamCode) {
        mutableStateOf(filterTeamCode.isNotBlank() && filterTeamCode != LOTTE_TEAM_CODE)
    }
    var query by remember { mutableStateOf("") }
    val season = remember { LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year } }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val teamLabel = teamCodeToName(filterTeamCode).ifBlank { "롯데" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ScreenTitle(
            title = "타이틀 순위",
            subtitle = teamLabel,
            leading = if (onBack != null) {
                {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("선수 이름 검색 · ☆로 즐겨찾기") },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleTabChip("타자 타이틀", selected = tab == 0) { tab = 0 }
            TitleTabChip("투수 타이틀", selected = tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            TitleTabChip("${teamLabel}만", selected = teamOnly) { teamOnly = !teamOnly }
        }
        Spacer(Modifier.height(14.dp))

        val categories = if (tab == 0) BATTER_TITLES else PITCHER_TITLES
        val pool = remember(tab, batters, pitchers, query) {
            val raw = if (tab == 0) batters else pitchers
            if (query.isBlank()) raw else raw.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

        if (pool.isEmpty()) {
            EmptyRetry(
                message = if (query.isNotBlank()) "검색 결과가 없습니다." else "순위 데이터를 불러오지 못했습니다.",
                onRetry = onRetry,
            )
        } else {
            categories.forEach { cat ->
                val key = "${tab}_${cat.key}"
                val showAll = expanded[key] == true
                val allEntries = remember(pool, cat.key) { buildTitleRanks(pool, cat).take(20) }
                val entries = if (teamOnly) allEntries.filter { it.player.matchesTeam(filterTeamCode) } else allEntries
                TitleSectionCard(
                    title = "${cat.title} ($season)",
                    entries = if (showAll) entries else entries.take(5),
                    expanded = showAll,
                    canExpand = entries.size > 5,
                    emptyMessage = if (teamOnly && entries.isEmpty()) "${teamLabel} 선수 없음" else null,
                    favoriteCodes = favoriteCodes,
                    onToggle = { expanded[key] = !showAll },
                    onPlayerClick = onPlayerClick,
                    onToggleFavorite = onToggleFavorite,
                    highlightTeamCode = filterTeamCode,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TitleTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) LotteRed else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
}

@Composable
private fun TitleSectionCard(
    title: String,
    entries: List<TitleRankEntry>,
    expanded: Boolean,
    canExpand: Boolean,
    emptyMessage: String? = null,
    favoriteCodes: Set<String> = emptySet(),
    onToggle: () -> Unit,
    onPlayerClick: (LeaderPlayer) -> Unit,
    onToggleFavorite: (LeaderPlayer) -> Unit = {},
    highlightTeamCode: String = LOTTE_TEAM_CODE,
) {
    SectionCard(padding = 0.dp) {
        Column {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            if (entries.isEmpty() && emptyMessage != null) {
                Text(
                    emptyMessage,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            entries.forEachIndexed { i, e ->
                TitlePlayerRow(
                    e,
                    isFavorite = e.player.playerCode in favoriteCodes,
                    highlight = e.player.matchesTeam(highlightTeamCode),
                    onClick = { onPlayerClick(e.player) },
                    onToggleFavorite = { onToggleFavorite(e.player) },
                )
                if (i < entries.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                    )
                }
            }
            if (canExpand) {
                Text(
                    if (expanded) "접기 ▲" else "더보기 (20위까지) ▼",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun TitlePlayerRow(
    e: TitleRankEntry,
    isFavorite: Boolean = false,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    val p = e.player
    val bg = if (highlight) LotteGold.copy(alpha = 0.10f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(LotteGold.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${e.rank}",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(40.dp)) {
            PlayerAvatar(
                playerCode = p.playerCode,
                name = p.name,
                size = 40.dp,
            )
            val code = teamNameToCode(p.team)
            if (code.isNotBlank()) {
                TeamLogo(
                    url = teamLogoUrl(code),
                    size = 16,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(p.team, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.playerCode.isNotBlank()) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "즐겨찾기",
                tint = if (isFavorite) LotteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onToggleFavorite),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            e.valueLabel,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun buildTitleRanks(pool: List<LeaderPlayer>, cat: TitleCategory): List<TitleRankEntry> {
    val filtered = pool
        .asSequence()
        .filter { !cat.requireQualified || it.qualified }
        .mapNotNull { p ->
            val v = cat.valueOf(p) ?: return@mapNotNull null
            p to v
        }
        .toList()
        .let { list ->
            if (cat.ascending) list.sortedBy { it.second } else list.sortedByDescending { it.second }
        }

    if (filtered.isEmpty()) return emptyList()

    val out = ArrayList<TitleRankEntry>(filtered.size)
    var prev: Double? = null
    var rank = 0
    filtered.forEachIndexed { index, (player, value) ->
        val same = prev != null && abs(prev!! - value) < 1e-9
        if (!same) rank = index + 1
        prev = value
        out += TitleRankEntry(rank = rank, player = player, valueLabel = cat.format(value))
    }
    return out
}

private fun formatRate(v: Double): String =
    String.format(Locale.US, "%.3f", v)

private fun formatOps(v: Double): String =
    String.format(Locale.US, "%.3f", v)

private fun formatEra(v: Double): String =
    String.format(Locale.US, "%.2f", v)
