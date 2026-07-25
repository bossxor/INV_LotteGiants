package com.bossxor.lottegiants.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.inningLabel

private val Navy = Color(0xFF041E42)
private val Red = Color(0xFFD00F31)
private val Gold = Color(0xFFC9A227)
private val Muted = Color(0xFFAAB4CB)

class LotteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = GiantsRepository.get(context).store.loadSnapshot()
        provideContent {
            GlanceTheme {
                WidgetContent(snap)
            }
        }
    }
}

@Composable
private fun WidgetContent(snap: LiveSnapshot?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = Navy, night = Navy))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val game = snap?.lotteGame
        when {
            game != null && game.status == GameStatus.LIVE -> LiveWidget(game)
            game != null && game.status == GameStatus.ENDED -> EndedWidget(game)
            game != null && game.status == GameStatus.BEFORE -> BeforeWidget(game)
            snap?.nextLotteGame != null -> BeforeWidget(snap.nextLotteGame!!)
            else -> Text(
                "롯데 경기 없음",
                style = TextStyle(color = ColorProvider(Muted, Muted), fontSize = 14.sp)
            )
        }
    }
}

@Composable
private fun LiveWidget(g: LotteGameInfo) {
    val white = ColorProvider(Color.White, Color.White)
    val red = ColorProvider(Red, Red)
    val muted = ColorProvider(Muted, Muted)
    val gold = ColorProvider(Gold, Gold)
    Text("롯데 LIVE", style = TextStyle(color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    Spacer(GlanceModifier.height(4.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${g.lotteScore}", style = TextStyle(color = red, fontSize = 32.sp, fontWeight = FontWeight.Bold))
        Text(" : ", style = TextStyle(color = white, fontSize = 24.sp))
        Text("${g.opponentScore}", style = TextStyle(color = white, fontSize = 32.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text(g.opponentName, style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Text(g.inningLabel, style = TextStyle(color = gold, fontSize = 12.sp))
        }
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
        "B${g.ball} S${g.strike} O${g.out}  ·  ${basesText(g)}",
        style = TextStyle(color = muted, fontSize = 12.sp)
    )
    if (g.currentPitcherName.isNotBlank() || g.currentBatterName.isNotBlank()) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            "투 ${g.currentPitcherName.ifBlank { "-" }}  타 ${g.currentBatterName.ifBlank { "-" }}",
            style = TextStyle(color = white, fontSize = 12.sp)
        )
    }
}

@Composable
private fun EndedWidget(g: LotteGameInfo) {
    val white = ColorProvider(Color.White, Color.White)
    val red = ColorProvider(Red, Red)
    val muted = ColorProvider(Muted, Muted)
    Text("경기 종료", style = TextStyle(color = muted, fontSize = 11.sp))
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "롯데 ${g.lotteScore} : ${g.opponentScore} ${g.opponentName}",
        style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    )
    val result = when {
        g.lotteScore > g.opponentScore -> "승리"
        g.lotteScore < g.opponentScore -> "패배"
        else -> "무승부"
    }
    Text(result, style = TextStyle(color = red, fontSize = 13.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun BeforeWidget(g: LotteGameInfo) {
    val white = ColorProvider(Color.White, Color.White)
    val muted = ColorProvider(Muted, Muted)
    val gold = ColorProvider(Gold, Gold)
    Text("다음 경기", style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "${g.gameDate} ${g.startTime}",
        style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    )
    Text(
        "vs ${g.opponentName} · ${g.stadium}",
        style = TextStyle(color = muted, fontSize = 12.sp)
    )
    if (g.lotteStartingPitcher.isNotBlank()) {
        Text("선발 ${g.lotteStartingPitcher}", style = TextStyle(color = white, fontSize = 12.sp))
    }
}

private fun basesText(g: LotteGameInfo): String {
    val parts = buildList {
        if (g.onBase1) add("1")
        if (g.onBase2) add("2")
        if (g.onBase3) add("3")
    }
    return if (parts.isEmpty()) "주자없음" else "주자 ${parts.joinToString(",")}"
}

class LotteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LotteWidget()
}
