package com.bossxor.lottegiants.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.FavoritePlayer
import com.bossxor.lottegiants.domain.KBO_TEAMS
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.components.PlayerAvatar
import com.bossxor.lottegiants.ui.components.ScreenTitle
import com.bossxor.lottegiants.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    favoritePlayers: List<FavoritePlayer>,
    jerseyPlayers: List<EntryPlayer>,
    playersTeamCode: String,
    myTeamCode: String = LOTTE_TEAM_CODE,
    loading: Boolean,
    refreshing: Boolean = false,
    onSelectTeam: (String) -> Unit,
    onRefresh: () -> Unit,
    onAppear: () -> Unit = {},
    onRemoveFavorite: (String) -> Unit,
    onOpenPlayerSearch: () -> Unit,
    onFavoriteClick: (FavoritePlayer) -> Unit = {},
    onJerseyClick: (EntryPlayer) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val focusTeam = playersTeamCode.ifBlank { myTeamCode }

    LaunchedEffect(Unit) { onAppear() }
    LaunchedEffect(myTeamCode) {
        if (playersTeamCode.isBlank()) onSelectTeam(myTeamCode)
    }

    val filtered = remember(jerseyPlayers, query) {
        val q = query.trim()
        if (q.isEmpty()) jerseyPlayers
        else jerseyPlayers.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.backNumber.contains(q) ||
                it.position.contains(q, ignoreCase = true)
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                ScreenTitle(
                    title = "선수",
                    subtitle = "즐겨찾기 · 등번호 일람",
                )
                Spacer(Modifier.height(12.dp))
                Text("즐겨찾기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "타이틀·라인업·상세에서 ☆로 추가할 수 있습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenPlayerSearch,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("선수 검색해서 즐겨찾기", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    if (favoritePlayers.isEmpty()) {
                        Text(
                            "등록된 즐겨찾기 선수가 없습니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    } else {
                        favoritePlayers.forEachIndexed { i, fav ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onFavoriteClick(fav) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlayerAvatar(
                                    playerCode = fav.code,
                                    name = fav.name.ifBlank { fav.code },
                                    size = 40.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        fav.name.ifBlank { fav.code },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                    if (fav.team.isNotBlank()) {
                                        Text(
                                            fav.team,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    "삭제",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onRemoveFavorite(fav.code) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = LoseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                            if (i < favoritePlayers.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("등번호 일람", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "팀별 1군 등록 명단 · 등번호 오름차순",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KBO_TEAMS, key = { it.code }) { team ->
                        PlayersModeChip(team.shortName, focusTeam == team.code) {
                            onSelectTeam(team.code)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("이름 · 등번호 · 포지션") },
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
            }

            if (loading && filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LotteRed)
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        "선수 목록이 없습니다. 당겨서 새로고침해 보세요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(filtered, key = { "${it.backNumber}-${it.name}-${it.playerCode}" }) { p ->
                    JerseyPlayerRow(p, onClick = { onJerseyClick(p) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun JerseyPlayerRow(player: EntryPlayer, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            player.backNumber.ifBlank { "—" },
            modifier = Modifier.width(36.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = LotteRed,
        )
        PlayerAvatar(
            playerCode = player.playerCode,
            name = player.name,
            size = 36.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (player.position.isNotBlank()) {
                Text(
                    player.position,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayersModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = if (selected) LotteRed else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
    )
}
