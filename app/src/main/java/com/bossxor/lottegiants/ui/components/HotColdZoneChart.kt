package com.bossxor.lottegiants.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.HotColdZone

/**
 * 네이버 프리뷰 13존 핫/콜드 (포수 시점).
 * 1~9는 스트라이크존 3×3, 10~13은 존 밖.
 */
@Composable
fun HotColdZoneChart(
    zones: List<HotColdZone>,
    modifier: Modifier = Modifier,
) {
    val byZone = zones.filter { it.zone in 1..13 }.associateBy { it.zone }
    if (byZone.isEmpty()) return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val rows = listOf(
            listOf(11, 1, 2, 3, 12),
            listOf(11, 4, 5, 6, 12),
            listOf(10, 7, 8, 9, 13),
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.forEachIndexed { i, zone ->
                    val outer = i == 0 || i == 4
                    ZoneCell(
                        zone = byZone[zone],
                        modifier = Modifier
                            .weight(if (outer) 0.85f else 1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("차가움", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (1..5).forEach { step ->
                Box(
                    Modifier
                        .width(16.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatColor(step)),
                )
            }
            Text("뜨거움", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "포수 시점 · 존별 타율 (바깥은 존 밖)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ZoneCell(zone: HotColdZone?, modifier: Modifier = Modifier) {
    val heat = zone?.heat ?: 3
    val bg = heatColor(heat)
    val fg = if (heat <= 2 || heat >= 5) Color.White else Color(0xFF111827)
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formatAvg(zone?.avg),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatAvg(raw: String?): String {
    val t = raw?.trim().orEmpty()
    if (t.isBlank()) return "-"
    return if (t.startsWith("0.")) t.drop(1) else t
}

private fun heatColor(step: Int): Color = when (step.coerceIn(1, 5)) {
    1 -> Color(0xFF2563EB)
    2 -> Color(0xFF60A5FA)
    3 -> Color(0xFF9CA3AF)
    4 -> Color(0xFFF59E0B)
    else -> Color(0xFFDC2626)
}
