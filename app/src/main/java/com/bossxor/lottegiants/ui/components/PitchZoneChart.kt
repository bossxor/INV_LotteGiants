package com.bossxor.lottegiants.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.PitchLocation
import com.bossxor.lottegiants.ui.LotteRed
import kotlin.math.abs

/**
 * 포수 시점 스트라이크존 + 투구 위치.
 * x: crossPlateX(ft, 가운데 0), y: 플레이트 높이(ft).
 */
@Composable
fun PitchZoneChart(
    pitches: List<PitchLocation>,
    modifier: Modifier = Modifier,
) {
    if (pitches.isEmpty()) {
        Text(
            "투구 추적 데이터가 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val topSz = pitches.map { it.topSz }.average().toFloat().coerceIn(2.8f, 4.0f)
    val bottomSz = pitches.map { it.bottomSz }.average().toFloat().coerceIn(1.2f, 2.2f)
    val recent = pitches.takeLast(40)
    val last = recent.lastOrNull()

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width = 220.dp, height = 260.dp)
                .padding(8.dp),
        ) {
            val pad = 16f
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            // 표시 범위: X -2..2 ft, Z 0.5..4.5 ft
            val xMin = -2.0f
            val xMax = 2.0f
            val zMin = 0.5f
            val zMax = 4.5f
            fun mapX(ft: Float): Float = pad + ((ft - xMin) / (xMax - xMin)) * w
            fun mapZ(ft: Float): Float = pad + h - ((ft - zMin) / (zMax - zMin)) * h

            // 배경
            drawRect(Color(0x11000000), Offset(pad, pad), Size(w, h))

            // 스트라이크 존 (폭 ±0.708 ft ≈ 17인치/2)
            val zoneHalf = 0.708f
            val zx0 = mapX(-zoneHalf)
            val zx1 = mapX(zoneHalf)
            val zy0 = mapZ(topSz)
            val zy1 = mapZ(bottomSz)
            drawRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(zx0, zy0),
                size = Size(zx1 - zx0, zy1 - zy0),
                style = Stroke(width = 2.5f),
            )
            // 9분할 가이드
            val zw = zx1 - zx0
            val zh = zy1 - zy0
            drawLine(Color.White.copy(alpha = 0.35f), Offset(zx0 + zw / 3f, zy0), Offset(zx0 + zw / 3f, zy1), 1f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(zx0 + 2 * zw / 3f, zy0), Offset(zx0 + 2 * zw / 3f, zy1), 1f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(zx0, zy0 + zh / 3f), Offset(zx1, zy0 + zh / 3f), 1f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(zx0, zy0 + 2 * zh / 3f), Offset(zx1, zy0 + 2 * zh / 3f), 1f)

            // 홈플레이트 힌트
            val plateY = mapZ(bottomSz - 0.15f)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(mapX(-0.7f), plateY), Offset(mapX(0.7f), plateY), 2f)

            recent.forEachIndexed { i, p ->
                val cx = mapX(p.x.coerceIn(xMin, xMax))
                val cy = mapZ(p.y.coerceIn(zMin, zMax))
                val isLast = i == recent.lastIndex
                val inZone = abs(p.x) <= zoneHalf && p.y in bottomSz..topSz
                val color = when {
                    isLast -> LotteRed
                    inZone -> Color(0xFF4CAF50)
                    else -> Color(0xFF90CAF9)
                }
                val r = if (isLast) 7f else 5f
                drawCircle(color, radius = r, center = Offset(cx, cy))
                if (isLast) {
                    drawCircle(Color.White, radius = r, center = Offset(cx, cy), style = Stroke(2f))
                }
            }
        }

        last?.let { p ->
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    if (p.speed > 0) append("${p.speed}km/h")
                    if (p.pitchType.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(p.pitchType)
                    }
                    if (p.inning > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${p.inning}회")
                    }
                }.ifBlank { "최근 투구" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(Color(0xFF4CAF50), "존 안")
            LegendDot(Color(0xFF90CAF9), "존 밖")
            LegendDot(LotteRed, "최근")
        }
        Text(
            "최근 ${recent.size}구 · 포수 시점",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.size(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
