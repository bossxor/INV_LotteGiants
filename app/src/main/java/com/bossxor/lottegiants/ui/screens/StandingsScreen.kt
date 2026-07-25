package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.ui.LotteRed
import java.util.Locale

@Composable
fun StandingsScreen(standings: List<TeamStanding>) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("KBO 순위", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
            listOf("순위" to 0.12f, "팀" to 0.22f, "경기" to 0.12f, "승-패" to 0.22f, "승률" to 0.16f, "GB" to 0.16f).forEach { (t, w) ->
                Text(t, Modifier.weight(w), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        standings.forEach { t ->
            val isLotte = t.teamId == LOTTE_TEAM_CODE
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (isLotte) LotteRed else MaterialTheme.colorScheme.onSurface
                val weight = if (isLotte) FontWeight.Bold else FontWeight.Normal
                Text("${t.ranking}", Modifier.weight(0.12f), color = color, fontWeight = weight, fontSize = 13.sp)
                Text(t.teamName, Modifier.weight(0.22f), color = color, fontWeight = weight, fontSize = 13.sp, maxLines = 1)
                Text("${t.gameCount}", Modifier.weight(0.12f), color = color, fontWeight = weight, fontSize = 13.sp)
                Text("${t.win}-${t.lose}", Modifier.weight(0.22f), color = color, fontWeight = weight, fontSize = 13.sp)
                Text(String.format(Locale.US, "%.3f", t.wra), Modifier.weight(0.16f), color = color, fontWeight = weight, fontSize = 13.sp)
                Text(
                    if (t.gameBehind == 0.0) "-" else String.format(Locale.US, "%.1f", t.gameBehind),
                    Modifier.weight(0.16f),
                    color = color,
                    fontWeight = weight,
                    fontSize = 13.sp
                )
            }
        }
    }
}
