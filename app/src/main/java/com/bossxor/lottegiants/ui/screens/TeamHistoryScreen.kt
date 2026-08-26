package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LotteHistory
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.teamFullName
import com.bossxor.lottegiants.domain.teamHomeLabel
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.ui.components.SectionCard
import com.bossxor.lottegiants.ui.components.TeamLogo

@Composable
fun TeamHistoryScreen(
    onBack: () -> Unit,
    teamCode: String = LOTTE_TEAM_CODE,
    teamCard: LotteTeamCard? = null,
) {
    val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
    val isLotte = code.equals(LOTTE_TEAM_CODE, true)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("팀 히스토리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(teamLogoUrl(code), size = 48)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        teamFullName(code),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        teamHomeLabel(code),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (teamCard != null && (teamCard.currentRank > 0 || teamCard.streak.isNotBlank())) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("시즌 현황", fontWeight = FontWeight.Bold)
                    if (teamCard.currentRank > 0) {
                        Text("${teamCard.currentRank}위", fontSize = 14.sp)
                    }
                    if (teamCard.streak.isNotBlank()) {
                        Text(teamCard.streak, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (teamCard.gamesBehind != 0.0) {
                        Text("게임차 ${teamCard.gamesBehind}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (isLotte) {
            Spacer(Modifier.height(12.dp))
            LotteHistory.sections.forEach { section ->
                SectionCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        section.items.forEach { item ->
                            Text("·  $item", fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
