package com.bossxor.lottegiants.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_LOGO_URL
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.domain.teamLogoUrl
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF0B2A4A)
private val Red = Color(0xFFC8102E)
private val Gold = Color(0xFFC9A227)
private val Muted = Color(0xFFAAB4CB)
private val Green = Color(0xFF2EA35C)

class LotteWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = GiantsRepository.get(context).store.loadSnapshot()
        val game = snap?.lotteGame ?: snap?.nextLotteGame
        val lotteLogo = WidgetAssets.logoProvider(context, LOTTE_TEAM_CODE, LOTTE_LOGO_URL)
        val oppLogo = if (game != null) {
            WidgetAssets.logoProvider(
                context,
                game.opponentCode,
                game.opponentLogoUrl.ifBlank { teamLogoUrl(game.opponentCode) },
            )
        } else {
            ImageProvider(R.drawable.ic_notification)
        }
        val pitcherPhoto = if (game != null && game.status == GameStatus.LIVE) {
            WidgetAssets.playerProvider(
                context,
                game.currentPitcherCode,
                playerPhotoUrl(game.currentPitcherCode),
            )
        } else {
            ImageProvider(R.drawable.ic_notification)
        }
        val batterCode = game?.let { g ->
            (g.lotteLineup + g.opponentLineup + g.lotteBenchBatters + g.opponentBenchBatters)
                .firstOrNull { it.name == g.currentBatterName }?.playerCode.orEmpty()
        }.orEmpty()
        val batterPhoto = if (game != null && game.status == GameStatus.LIVE && batterCode.isNotBlank()) {
            WidgetAssets.playerProvider(context, batterCode, playerPhotoUrl(batterCode))
        } else {
            ImageProvider(R.drawable.ic_notification)
        }
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, "live")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            GlanceTheme {
                WidgetRoot(snap, lotteLogo, oppLogo, pitcherPhoto, batterPhoto, openIntent)
            }
        }
    }
}

@Composable
private fun WidgetRoot(
    snap: LiveSnapshot?,
    lotteLogo: ImageProvider,
    oppLogo: ImageProvider,
    pitcherPhoto: ImageProvider,
    batterPhoto: ImageProvider,
    openIntent: Intent,
) {
    val size = LocalSize.current
    val compact = size.width < 180.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = Navy, night = Navy))
            .padding(if (compact) 8.dp else 12.dp)
            .clickable(actionStartActivity(openIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val game = snap?.lotteGame
        val highlightActive = !snap?.highlightText.isNullOrBlank() &&
            (snap?.highlightUntilMillis ?: 0L) > System.currentTimeMillis()
        when {
            game != null && game.status == GameStatus.LIVE -> {
                if (compact) CompactLive(game) else LiveWide(game, snap, lotteLogo, oppLogo, pitcherPhoto, batterPhoto, highlightActive)
            }
            game != null && game.status == GameStatus.ENDED -> {
                if (compact) CompactScore(game, "종료") else EndedWide(game, lotteLogo, oppLogo, snap)
            }
            game != null && game.status == GameStatus.CANCELED -> {
                if (compact) CompactScore(game, game.cancelLabel)
                else CanceledWide(game, lotteLogo, oppLogo)
            }
            game != null && game.status == GameStatus.BEFORE -> {
                if (compact) CompactBefore(game) else BeforeWide(game, snap, lotteLogo, oppLogo)
            }
            snap?.nextLotteGame != null -> {
                val next = snap.nextLotteGame!!
                if (compact) CompactBefore(next) else BeforeWide(next, snap, lotteLogo, oppLogo)
            }
            else -> Text(
                "경기 없음",
                style = TextStyle(color = ColorProvider(Muted, Muted), fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun CompactLive(g: LotteGameInfo) {
    val white = ColorProvider(Color.White, Color.White)
    val red = ColorProvider(Red, Red)
    val gold = ColorProvider(Gold, Gold)
    Text("LIVE", style = TextStyle(color = red, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    Text(
        "${g.lotteScore}:${g.opponentScore}",
        style = TextStyle(color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    )
    Text(g.inningLabel, style = TextStyle(color = gold, fontSize = 11.sp))
    Image(
        provider = ImageProvider(WidgetAssets.basesDrawable(g.onBase1, g.onBase2, g.onBase3)),
        contentDescription = "bases",
        modifier = GlanceModifier.size(28.dp),
    )
}

@Composable
private fun CompactScore(g: LotteGameInfo, label: String) {
    val white = ColorProvider(Color.White, Color.White)
    val muted = ColorProvider(Muted, Muted)
    Text(label, style = TextStyle(color = muted, fontSize = 10.sp))
    Text(
        "${g.lotteScore}:${g.opponentScore}",
        style = TextStyle(color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    )
    Text(g.opponentName, style = TextStyle(color = muted, fontSize = 11.sp))
}

@Composable
private fun CompactBefore(g: LotteGameInfo) {
    val white = ColorProvider(Color.White, Color.White)
    val gold = ColorProvider(Gold, Gold)
    val muted = ColorProvider(Muted, Muted)
    Text("다음", style = TextStyle(color = gold, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    Text(g.startTime.ifBlank { g.gameDate }, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    Text("vs ${g.opponentName}", style = TextStyle(color = muted, fontSize = 11.sp))
}

@Composable
private fun CanceledWide(
    g: LotteGameInfo,
    lotteLogo: ImageProvider,
    oppLogo: ImageProvider,
) {
    val white = ColorProvider(Color.White, Color.White)
    val red = ColorProvider(Red, Red)
    val muted = ColorProvider(Muted, Muted)
    Text(
        g.cancelLabel,
        style = TextStyle(color = red, fontSize = 12.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(GlanceModifier.height(6.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(provider = if (g.isHome) oppLogo else lotteLogo, contentDescription = null, modifier = GlanceModifier.size(28.dp))
        Spacer(GlanceModifier.width(8.dp))
        Text(
            if (g.isHome) g.opponentName else "롯데",
            style = TextStyle(color = white, fontSize = 13.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text("vs", style = TextStyle(color = muted, fontSize = 12.sp))
        Text(
            if (g.isHome) "롯데" else g.opponentName,
            style = TextStyle(color = white, fontSize = 13.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Image(provider = if (g.isHome) lotteLogo else oppLogo, contentDescription = null, modifier = GlanceModifier.size(28.dp))
    }
    if (g.stadium.isNotBlank()) {
        Spacer(GlanceModifier.height(4.dp))
        Text(g.stadium, style = TextStyle(color = muted, fontSize = 11.sp))
    }
}

@Composable
private fun LiveWide(
    g: LotteGameInfo,
    snap: LiveSnapshot?,
    lotteLogo: ImageProvider,
    oppLogo: ImageProvider,
    pitcherPhoto: ImageProvider,
    batterPhoto: ImageProvider,
    highlightActive: Boolean,
) {
    val white = ColorProvider(Color.White, Color.White)
    val red = ColorProvider(Red, Red)
    val muted = ColorProvider(Muted, Muted)
    val gold = ColorProvider(Gold, Gold)
    if (highlightActive) {
        Text(
            snap?.highlightText.orEmpty(),
            style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("LIVE", style = TextStyle(color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(8.dp))
        Text(g.inningLabel, style = TextStyle(color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(8.dp))
        Text(
            if (g.isLotteBatting) "롯데 공격" else "롯데 수비",
            style = TextStyle(color = muted, fontSize = 11.sp),
        )
    }
    Spacer(GlanceModifier.height(4.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamSide(if (g.isHome) oppLogo else lotteLogo, if (g.isHome) g.opponentName else "롯데", if (g.isHome) g.opponentScore else g.lotteScore)
        Spacer(GlanceModifier.width(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(WidgetAssets.basesDrawable(g.onBase1, g.onBase2, g.onBase3)),
                contentDescription = "bases",
                modifier = GlanceModifier.size(52.dp),
            )
            Spacer(GlanceModifier.height(4.dp))
            BsoDotsRow(g.ball, g.strike, g.out)
        }
        Spacer(GlanceModifier.width(6.dp))
        TeamSide(if (g.isHome) lotteLogo else oppLogo, if (g.isHome) "롯데" else g.opponentName, if (g.isHome) g.lotteScore else g.opponentScore)
    }
    Spacer(GlanceModifier.height(6.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(provider = pitcherPhoto, contentDescription = "P", modifier = GlanceModifier.size(28.dp), contentScale = ContentScale.Crop)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "P ${g.currentPitcherName.ifBlank { "-" }}",
            style = TextStyle(color = white, fontSize = 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Image(provider = batterPhoto, contentDescription = "B", modifier = GlanceModifier.size(28.dp), contentScale = ContentScale.Crop)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "B ${g.currentBatterName.ifBlank { "-" }}",
            style = TextStyle(color = white, fontSize = 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
    if (g.nextBatterName.isNotBlank()) {
        Text("다음 ${g.nextBatterName}", style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
    }
    if (g.onBase2 || g.onBase3) {
        Text("득점권!", style = TextStyle(color = gold, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
    FormRow(snap)
}

@Composable
private fun BsoDotsRow(ball: Int, strike: Int, out: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("B", style = TextStyle(color = ColorProvider(Green, Green), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(2.dp))
        repeat(4) { i ->
            Image(
                provider = ImageProvider(WidgetAssets.countDot(i < ball, 'B')),
                contentDescription = null,
                modifier = GlanceModifier.size(7.dp),
            )
            Spacer(GlanceModifier.width(2.dp))
        }
        Spacer(GlanceModifier.width(4.dp))
        Text("S", style = TextStyle(color = ColorProvider(Gold, Gold), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(2.dp))
        repeat(3) { i ->
            Image(
                provider = ImageProvider(WidgetAssets.countDot(i < strike, 'S')),
                contentDescription = null,
                modifier = GlanceModifier.size(7.dp),
            )
            Spacer(GlanceModifier.width(2.dp))
        }
        Spacer(GlanceModifier.width(4.dp))
        Text("O", style = TextStyle(color = ColorProvider(Red, Red), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(2.dp))
        repeat(3) { i ->
            Image(
                provider = ImageProvider(WidgetAssets.countDot(i < out, 'O')),
                contentDescription = null,
                modifier = GlanceModifier.size(7.dp),
            )
            Spacer(GlanceModifier.width(2.dp))
        }
    }
}

@Composable
private fun BeforeWide(
    g: LotteGameInfo,
    snap: LiveSnapshot?,
    lotteLogo: ImageProvider,
    oppLogo: ImageProvider,
) {
    val white = ColorProvider(Color.White, Color.White)
    val muted = ColorProvider(Muted, Muted)
    val gold = ColorProvider(Gold, Gold)
    val pregame = isWithinMinutes(g, 30)
    Text(
        if (pregame) "경기 임박" else "다음 경기",
        style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(GlanceModifier.height(4.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(lotteLogo, contentDescription = "lotte", modifier = GlanceModifier.size(28.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text("롯데", style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(8.dp))
        Text("vs", style = TextStyle(color = muted, fontSize = 12.sp))
        Spacer(GlanceModifier.width(8.dp))
        Text(g.opponentName, style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(6.dp))
        Image(oppLogo, contentDescription = "opp", modifier = GlanceModifier.size(28.dp))
    }
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "${g.gameDate} ${g.startTime} · ${g.stadium}",
        style = TextStyle(color = muted, fontSize = 11.sp),
        maxLines = 1,
    )
    Text(
        "선발  ${g.lotteStartingPitcher.ifBlank { "미정" }} vs ${g.opponentStartingPitcher.ifBlank { "미정" }}",
        style = TextStyle(color = white, fontSize = 11.sp),
        maxLines = 1,
    )
    if (pregame && snap?.weather != null) {
        val w = snap.weather!!
        Text(
            "날씨 ${w.summary} ${w.temperatureC.toInt()}°",
            style = TextStyle(color = gold, fontSize = 11.sp),
            maxLines = 1,
        )
    }
    FormRow(snap)
}

@Composable
private fun EndedWide(
    g: LotteGameInfo,
    lotteLogo: ImageProvider,
    oppLogo: ImageProvider,
    snap: LiveSnapshot?,
) {
    val white = ColorProvider(Color.White, Color.White)
    val muted = ColorProvider(Muted, Muted)
    val red = ColorProvider(Red, Red)
    Text("경기 종료", style = TextStyle(color = muted, fontSize = 11.sp))
    Spacer(GlanceModifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(lotteLogo, contentDescription = null, modifier = GlanceModifier.size(24.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "롯데 ${g.lotteScore} : ${g.opponentScore} ${g.opponentName}",
            style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Image(oppLogo, contentDescription = null, modifier = GlanceModifier.size(24.dp))
    }
    val result = when {
        g.lotteScore > g.opponentScore -> "승리"
        g.lotteScore < g.opponentScore -> "패배"
        else -> "무승부"
    }
    Text(result, style = TextStyle(color = red, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    FormRow(snap)
}

@Composable
private fun TeamSide(logo: ImageProvider, name: String, score: Int) {
    val white = ColorProvider(Color.White, Color.White)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(logo, contentDescription = name, modifier = GlanceModifier.size(26.dp))
        Text(name, style = TextStyle(color = white, fontSize = 11.sp), maxLines = 1)
        Text("$score", style = TextStyle(color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun FormRow(snap: LiveSnapshot?) {
    val recent = snap?.recentLotteGames.orEmpty().take(5)
    if (recent.isEmpty()) return
    Spacer(GlanceModifier.height(3.dp))
    Row {
        recent.forEach { g ->
            val letter = WidgetAssets.formLetter(g.lotteScore, g.opponentScore)
            val color = when (letter) {
                "W" -> Green
                "L" -> Red
                else -> Gold
            }
            Text(
                "$letter ",
                style = TextStyle(color = ColorProvider(color, color), fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun isWithinMinutes(g: LotteGameInfo, minutes: Long): Boolean {
    return try {
        val date = LocalDate.parse(g.gameDate)
        val time = LocalTime.parse(g.startTime.padStart(5, '0'), DateTimeFormatter.ofPattern("H:mm"))
        val start = LocalDateTime.of(date, time)
        val now = LocalDateTime.now()
        !now.isAfter(start) && java.time.Duration.between(now, start).toMinutes() in 0..minutes
    } catch (_: Exception) {
        false
    }
}

class LotteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LotteWidget()
}
