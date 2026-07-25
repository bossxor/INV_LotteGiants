package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.components.CountDots
import com.bossxor.lottegiants.ui.components.DiamondView
import com.bossxor.lottegiants.ui.components.ScoreBoard

@Composable
fun LiveScreen(snapshot: LiveSnapshot?, error: String?, loading: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("롯데 자이언츠", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LotteRed)
        Text("실시간 현황", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        when {
            loading && snapshot == null -> {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            snapshot?.lotteGame != null -> LiveGameContent(snapshot.lotteGame!!)
            snapshot?.nextLotteGame != null -> NextGameContent(snapshot.nextLotteGame!!)
            else -> {
                Text("오늘 롯데 경기가 없습니다.", style = MaterialTheme.typography.bodyLarge)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LiveGameContent(g: LotteGameInfo) {
    // Score header
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(if (g.isHome) g.opponentName else "롯데", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${if (g.isHome) g.opponentScore else g.lotteScore}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = if (!g.isHome) LotteRed else MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.inningLabel, fontWeight = FontWeight.SemiBold, color = LotteGold)
            Text(
                when (g.status) {
                    GameStatus.LIVE -> "LIVE"
                    GameStatus.ENDED -> "종료"
                    GameStatus.BEFORE -> "예정"
                    GameStatus.CANCELED -> "취소"
                },
                fontSize = 12.sp,
                color = if (g.status == GameStatus.LIVE) LotteRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (g.stadium.isNotBlank()) {
                Text(g.stadium, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(if (g.isHome) "롯데" else g.opponentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${if (g.isHome) g.lotteScore else g.opponentScore}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = if (g.isHome) LotteRed else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (g.status == GameStatus.LIVE || g.status == GameStatus.ENDED) {
        Spacer(Modifier.height(16.dp))
        ScoreBoard(
            opponentName = g.opponentName,
            lotteScores = g.lotteInningScores,
            oppScores = g.opponentInningScores,
            lotteR = g.lotteScore,
            oppR = g.opponentScore,
            lotteH = g.lotteHits,
            oppH = g.opponentHits,
            lotteE = g.lotteErrors,
            oppE = g.opponentErrors,
        )
    }

    if (g.status == GameStatus.LIVE) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DiamondView(g.onBase1, g.onBase2, g.onBase3)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CountDots("B", g.ball, 4, LotteGold)
                CountDots("S", g.strike, 3, LotteRed)
                CountDots("O", g.out, 3, MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            InfoLine("투수", g.currentPitcherName.ifBlank { "-" })
            InfoLine("타자", buildString {
                if (g.currentBatterOrder > 0) append("${g.currentBatterOrder}번 ")
                append(g.currentBatterName.ifBlank { "-" })
                if (g.isLotteBatting) append(" (롯데)")
            })
            InfoLine("다음 타자", g.nextBatterName.ifBlank { "-" })
        }
    }

    if (g.status == GameStatus.ENDED) {
        Spacer(Modifier.height(12.dp))
        val result = when {
            g.lotteScore > g.opponentScore -> "롯데 승리"
            g.lotteScore < g.opponentScore -> "롯데 패배"
            else -> "무승부"
        }
        Text(result, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LotteRed)
        if (g.winPitcherName.isNotBlank()) Text("승: ${g.winPitcherName}  패: ${g.losePitcherName}")
    }

    Spacer(Modifier.height(16.dp))
    Text("선발", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text("롯데 투수  ${g.lotteStartingPitcher.ifBlank { "미정" }}")
    Text("${g.opponentName} 투수  ${g.opponentStartingPitcher.ifBlank { "미정" }}")

    if (g.lotteLineup.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("롯데 타순", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            g.lotteLineup.forEach { slot ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${slot.batOrder}. ${slot.name}${if (slot.isSubstitute) " *" else ""}",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${slot.position}  ${slot.todayHits}/${slot.todayAtBats}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (g.recentTexts.isNotEmpty() && g.status == GameStatus.LIVE) {
        Spacer(Modifier.height(16.dp))
        Text("최근 중계", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        g.recentTexts.take(8).forEach { t ->
            Text(
                "${t.inning}회  ${t.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun NextGameContent(g: LotteGameInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text("다음 경기", fontWeight = FontWeight.Bold, color = LotteGold)
        Spacer(Modifier.height(8.dp))
        Text("${g.gameDate}  ${g.startTime}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(if (g.isHome) "vs ${g.opponentName} (홈)" else "@ ${g.opponentName} (원정)", fontSize = 18.sp)
        if (g.stadium.isNotBlank()) Text(g.stadium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("선발  ${g.lotteStartingPitcher.ifBlank { "미정" }} vs ${g.opponentStartingPitcher.ifBlank { "미정" }}")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(0.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.7f), fontWeight = FontWeight.SemiBold)
    }
}
