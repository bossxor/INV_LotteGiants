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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bossxor.lottegiants.domain.playerPhotoCandidates
import com.bossxor.lottegiants.domain.teamLogoCandidates
import com.bossxor.lottegiants.ui.BaseOccupied
import com.bossxor.lottegiants.ui.LotteGold
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.WinGreen

/** 팀 엠블럼 — 네이버 CDN이 막히면 KBO NCP 엠블럼으로 넘긴다 */
@Composable
fun TeamLogo(url: String, size: Int = 40, modifier: Modifier = Modifier) {
    val urls = remember(url) { teamLogoCandidates(kboUrl = url) }
    var idx by remember(url) { mutableIntStateOf(0) }
    val model = urls.getOrNull(idx) ?: return
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size.dp),
        onError = { if (idx < urls.lastIndex) idx++ },
    )
}

/** 선수 사진 — KBO/네이버 URL을 순서대로 시도하고, 전부 실패하면 이니셜 */
@Composable
fun PlayerAvatar(
    playerCode: String,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    extraUrls: List<String> = emptyList(),
) {
    val urls = remember(playerCode, extraUrls) {
        (extraUrls + playerPhotoCandidates(playerCode)).distinct().filter { it.isNotBlank() }
    }
    var idx by remember(playerCode) { mutableIntStateOf(0) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val url = urls.getOrNull(idx)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
                onError = { idx++ },
            )
        } else {
            Text(
                name.take(1).ifBlank { "?" },
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.38f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 카드 — 테두리 없이 배경만 한 겹 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 통일된 섹션 제목 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun DiamondView(
    on1: Boolean,
    on2: Boolean,
    on3: Boolean,
    outs: Int = 0,
    ball: Int = 0,
    strike: Int = 0,
    inningLabel: String = "",
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val occupied = BaseOccupied
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val canvasSize = if (compact) 72.dp else 120.dp
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (inningLabel.isNotBlank()) {
            Text(
                inningLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = LotteGold,
            )
            Spacer(Modifier.height(4.dp))
        }
        Canvas(modifier = Modifier.size(canvasSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f + 4.dp.toPx()
            val r = size.minDimension * 0.32f
            // 홈 플레이트 방향 표시용 필드 라인
            val home = Offset(cx, cy + r)
            val first = Offset(cx + r, cy)
            val second = Offset(cx, cy - r)
            val third = Offset(cx - r, cy)
            val dirt = Path().apply {
                moveTo(second.x, second.y)
                lineTo(first.x, first.y)
                lineTo(home.x, home.y)
                lineTo(third.x, third.y)
                close()
            }
            drawPath(dirt, color = empty.copy(alpha = 0.12f))
            drawPath(dirt, color = outline, style = Stroke(width = 2.5f))

            fun drawBase(p: Offset, filled: Boolean, label: String) {
                val s = 13.dp.toPx()
                val diamond = Path().apply {
                    moveTo(p.x, p.y - s)
                    lineTo(p.x + s, p.y)
                    lineTo(p.x, p.y + s)
                    lineTo(p.x - s, p.y)
                    close()
                }
                drawPath(diamond, color = if (filled) occupied else empty.copy(alpha = 0.55f))
                if (filled) {
                    drawPath(diamond, color = Color(0xFF1A3300), style = Stroke(width = 2.4f))
                }
            }
            drawBase(first, on1, "1")
            drawBase(second, on2, "2")
            drawBase(third, on3, "3")
            // 홈
            val hs = 9.dp.toPx()
            val homePath = Path().apply {
                moveTo(home.x, home.y - hs)
                lineTo(home.x + hs * 0.7f, home.y)
                lineTo(home.x, home.y + hs * 0.5f)
                lineTo(home.x - hs * 0.7f, home.y)
                close()
            }
            drawPath(homePath, color = Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CountDots("B", ball, 4, WinGreen)
            CountDots("S", strike, 3, LotteGold)
            CountDots("O", outs, 3, LotteRed)
        }
    }
}

@Composable
fun CountDots(label: String, count: Int, max: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(max) { i ->
                Box(
                    Modifier
                        .size(11.dp)
                        .background(if (i < count) color else color.copy(alpha = 0.2f), CircleShape)
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
    lotteFirst: Boolean,
    lotteR: Int,
    oppR: Int,
    lotteH: Int,
    oppH: Int,
    lotteE: Int,
    oppE: Int,
    focusTeamName: String = "롯데",
) {
    val innings = maxOf(lotteScores.size, oppScores.size, 9)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("팀", Modifier.width(44.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(innings) { i ->
                Text(
                    "${i + 1}", Modifier.weight(1f), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("R", Modifier.width(24.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("H", Modifier.width(22.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("E", Modifier.width(20.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        val rows = if (lotteFirst) {
            listOf(
                Triple(focusTeamName, lotteScores, Triple(lotteR, lotteH, lotteE)) to true,
                Triple(opponentName, oppScores, Triple(oppR, oppH, oppE)) to false,
            )
        } else {
            listOf(
                Triple(opponentName, oppScores, Triple(oppR, oppH, oppE)) to false,
                Triple(focusTeamName, lotteScores, Triple(lotteR, lotteH, lotteE)) to true,
            )
        }
        rows.forEachIndexed { idx, (data, highlight) ->
            val (name, scores, rhe) = data
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, Modifier.width(44.dp), fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                repeat(innings) { i ->
                    Text(scores.getOrNull(i) ?: "-", Modifier.weight(1f), fontSize = 12.sp)
                }
                Text("${rhe.first}", Modifier.width(24.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${rhe.second}", Modifier.width(22.dp), fontSize = 12.sp)
                Text("${rhe.third}", Modifier.width(20.dp), fontSize = 12.sp)
            }
            if (idx == 0) Spacer(Modifier.height(4.dp))
        }
    }
}
