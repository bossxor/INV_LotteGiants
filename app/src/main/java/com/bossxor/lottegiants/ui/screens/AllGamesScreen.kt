package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.ui.LotteRed

@Composable
fun AllGamesScreen(snapshot: LiveSnapshot?) {
    val games = snapshot?.otherGames.orEmpty()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("오늘 다른 경기", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("점수와 이닝만 표시", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        if (games.isEmpty()) {
            Text("오늘 다른 KBO 경기가 없습니다.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(games, key = { it.gameId }) { game ->
                    MiniGameCard(game)
                }
            }
        }
    }
}

@Composable
private fun MiniGameCard(g: MiniGame) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("${g.awayName} vs ${g.homeName}", fontWeight = FontWeight.SemiBold)
            Text(
                when (g.status) {
                    GameStatus.LIVE -> g.statusText.ifBlank { "진행 중" }
                    GameStatus.BEFORE -> g.startTime
                    GameStatus.ENDED -> "종료"
                    GameStatus.CANCELED -> "취소"
                },
                fontSize = 12.sp,
                color = if (g.status == GameStatus.LIVE) LotteRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (g.status != GameStatus.BEFORE && g.status != GameStatus.CANCELED) {
            Text(
                "${g.awayScore} : ${g.homeScore}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
