package com.bossxor.lottegiants.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bossxor.lottegiants.domain.PlayerDetail
import com.bossxor.lottegiants.ui.LotteGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailSheet(
    detail: PlayerDetail?,
    loading: Boolean,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when {
            loading && detail == null -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            detail == null -> {
                Text(
                    "선수 정보를 불러오지 못했습니다.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> PlayerDetailContent(detail, isFavorite, onToggleFavorite)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlayerDetailContent(
    d: PlayerDetail,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (d.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = d.photoUrl,
                    contentDescription = d.name,
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                if (d.backNumber.isNotBlank()) {
                    Text("No.${d.backNumber}", color = LotteGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(d.name, fontWeight = FontWeight.Black, fontSize = 22.sp)
                val meta = listOfNotNull(
                    d.position.takeIf { it.isNotBlank() },
                    d.hitType.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            if (d.playerCode.isNotBlank()) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "즐겨찾기",
                        tint = if (isFavorite) LotteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val phys = buildList {
            if (d.birth.isNotBlank()) add(formatBirth(d.birth))
            if (d.heightCm.isNotBlank()) add("${d.heightCm.trimEnd('0').trimEnd('.')}cm")
            if (d.weightKg.isNotBlank()) add("${d.weightKg.trimEnd('0').trimEnd('.')}kg")
        }
        if (phys.isNotEmpty()) {
            Text(phys.joinToString("  ·  "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }

        if (d.todayLine.isNotBlank()) {
            StatBox("오늘", d.todayLine)
            Spacer(Modifier.height(8.dp))
        }

        Text("시즌 성적", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (d.isPitcher) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("ERA", d.pitcherEra.ifBlank { "-" }, Modifier.weight(1f))
                StatBox("승-패", "${d.pitcherWins}-${d.pitcherLosses}", Modifier.weight(1f))
                StatBox("이닝", d.pitcherInn.ifBlank { "-" }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("탈삼진", "${d.pitcherSo}", Modifier.weight(1f))
                StatBox("세이브", "${d.pitcherSaves}", Modifier.weight(1f))
                StatBox("홀드", "${d.pitcherHolds}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("WHIP", d.pitcherWhip.ifBlank { "-" }, Modifier.weight(1f))
                StatBox("경기", "${d.seasonGames}", Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("타율", d.seasonAvg.ifBlank { "-" }, Modifier.weight(1f))
                StatBox("안타", "${d.seasonHits}", Modifier.weight(1f))
                StatBox("홈런", "${d.seasonHr}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("타점", "${d.seasonRbi}", Modifier.weight(1f))
                StatBox("출루", d.seasonObp.ifBlank { "-" }, Modifier.weight(1f))
                StatBox("장타", d.seasonSlg.ifBlank { "-" }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("OPS", d.seasonOps.ifBlank { "-" }, Modifier.weight(1f))
                StatBox("도루", "${d.seasonSb}", Modifier.weight(1f))
                StatBox("경기", "${d.seasonGames}", Modifier.weight(1f))
            }
            if (d.seasonAb > 0) {
                Spacer(Modifier.height(8.dp))
                Text("타수 ${d.seasonAb}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

private fun formatBirth(raw: String): String {
    if (raw.length == 8) {
        return "${raw.substring(0, 4)}.${raw.substring(4, 6)}.${raw.substring(6, 8)}"
    }
    return raw
}
