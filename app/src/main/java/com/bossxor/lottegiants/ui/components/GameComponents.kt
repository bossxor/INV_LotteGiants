package com.bossxor.lottegiants.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.ui.LotteRed

@Composable
fun DiamondView(
    on1: Boolean,
    on2: Boolean,
    on3: Boolean,
    modifier: Modifier = Modifier,
) {
    val occupied = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Canvas(modifier = Modifier.size(88.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.28f
        val bases = listOf(
            Offset(cx, cy - r), // 2
            Offset(cx + r, cy), // 1
            Offset(cx - r, cy), // 3
        )
        val filled = listOf(on2, on1, on3)
        val path = Path().apply {
            moveTo(bases[0].x, bases[0].y)
            lineTo(bases[1].x, bases[1].y)
            lineTo(cx, cy + r)
            lineTo(bases[2].x, bases[2].y)
            close()
        }
        drawPath(path, color = empty.copy(alpha = 0.2f))
        drawPath(path, color = empty, style = Stroke(width = 2f))
        bases.forEachIndexed { i, p ->
            val s = 10.dp.toPx()
            val diamond = Path().apply {
                moveTo(p.x, p.y - s)
                lineTo(p.x + s, p.y)
                lineTo(p.x, p.y + s)
                lineTo(p.x - s, p.y)
                close()
            }
            drawPath(diamond, color = if (filled[i]) occupied else empty)
        }
    }
}

@Composable
fun CountDots(label: String, count: Int, max: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(max) { i ->
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (i < count) color else color.copy(alpha = 0.25f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun ScoreBoard(
    opponentName: String,
    lotteScores: List<String>,
    oppScores: List<String>,
    lotteR: Int,
    oppR: Int,
    lotteH: Int,
    oppH: Int,
    lotteE: Int,
    oppE: Int,
) {
    val innings = maxOf(lotteScores.size, oppScores.size, 9)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("팀", Modifier.width(48.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(innings) { i ->
                Text("${i + 1}", Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("R", Modifier.width(22.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("H", Modifier.width(22.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("E", Modifier.width(22.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        ScoreRow(opponentName, oppScores, innings, oppR, oppH, oppE, false)
        Spacer(Modifier.height(4.dp))
        ScoreRow("롯데", lotteScores, innings, lotteR, lotteH, lotteE, true)
    }
}

@Composable
private fun ScoreRow(
    name: String,
    scores: List<String>,
    innings: Int,
    r: Int,
    h: Int,
    e: Int,
    highlight: Boolean,
) {
    val nameColor = if (highlight) LotteRed else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(48.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = nameColor, maxLines = 1)
        repeat(innings) { i ->
            val v = scores.getOrNull(i) ?: "-"
            Text(v, Modifier.weight(1f), fontSize = 12.sp)
        }
        Text("$r", Modifier.width(22.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("$h", Modifier.width(22.dp), fontSize = 12.sp)
        Text("$e", Modifier.width(22.dp), fontSize = 12.sp)
    }
}
