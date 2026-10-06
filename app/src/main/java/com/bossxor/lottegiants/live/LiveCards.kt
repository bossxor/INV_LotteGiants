package com.bossxor.lottegiants.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.destination
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.NowBarContent
import com.bossxor.lottegiants.domain.NowBarText
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_DEFAULT
import com.bossxor.lottegiants.domain.shouldPostLiveNotification
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.suspendLabel
import com.bossxor.lottegiants.domain.WinProb
import com.bossxor.lottegiants.domain.estimateLotteWinProb
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.teamAccentColor
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.teamNameToCode
import com.bossxor.lottegiants.widget.WidgetAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 라이브 알림 카드(RemoteViews)·요약 글자·양쪽 팀 계산. NotificationHelper에서 떼어 냈다. */

/** 왼쪽 = 원정(초 공격), 오른쪽 = 홈(말 공격). */
internal data class NowBarSides(val leftCode: String, val leftName: String, val rightCode: String, val rightName: String)

internal fun nowBarSides(game: LotteGameInfo): NowBarSides {
    val mine = game.focusTeamCode.ifBlank { LOTTE_TEAM_CODE }
    val opp = game.opponentCode.ifBlank { teamNameToCode(game.opponentName) }
    return if (game.isHome) NowBarSides(opp, game.opponentName, mine, game.focusName())
    else NowBarSides(mine, game.focusName(), opp, game.opponentName)
}

/** 루타앱 경기요약에 가까운 전체 텍스트 (요약 탭과 동일 소스) */
internal fun gameSummary(game: LotteGameInfo?): String {
    if (game == null) return "대기 중"
    if (game.status != GameStatus.LIVE) {
        return buildString {
            append(game.inningLabel.ifBlank { game.statusText.ifBlank { game.opponentName } })
            if (game.stadium.isNotBlank()) append("\n구장  ${game.stadium}")
            if (game.lotteStartingPitcher.isNotBlank() || game.opponentStartingPitcher.isNotBlank()) {
                append("\n선발  ${game.focusName()} ${game.lotteStartingPitcher.ifBlank { "-" }}")
                append("  ·  ${game.opponentName} ${game.opponentStartingPitcher.ifBlank { "-" }}")
            }
        }
    }
    return buildString {
        append(game.inningLabel)
        if (game.isLotteBatting) append("  ·  ${game.focusName()} 공격") else append("  ·  상대 공격")
        append("\n루상  ${basesLabel(game)}")
        append("\n투수  ${game.currentPitcherName.ifBlank { "-" }}")
        if (game.currentPitcherPitchCount > 0) append(" (${game.currentPitcherPitchCount}구)")
        append("\n타자  ")
        if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
        append(game.currentBatterName.ifBlank { "-" })
        if (game.nextBatterName.isNotBlank()) {
            append("\n다음 타자  ${game.nextBatterName}")
        }
        if (game.stadium.isNotBlank()) append("\n구장  ${game.stadium}")
    }
}

internal fun basesLabel(game: LotteGameInfo): String {
    val parts = buildList {
        if (game.onBase1) {
            add(if (game.runnerOn1Order > 0) "1루(${game.runnerOn1Order}번)" else "1루")
        }
        if (game.onBase2) {
            add(if (game.runnerOn2Order > 0) "2루(${game.runnerOn2Order}번)" else "2루")
        }
        if (game.onBase3) {
            add(if (game.runnerOn3Order > 0) "3루(${game.runnerOn3Order}번)" else "3루")
        }
    }
    return if (parts.isEmpty()) "주자 없음" else parts.joinToString("·")
}

internal data class CardSides(
    val awayName: String,
    val homeName: String,
    val awayScore: Int,
    val homeScore: Int,
    val awayCode: String,
    val homeCode: String,
    val awayLogoUrl: String,
    val homeLogoUrl: String,
)

internal fun cardSides(game: LotteGameInfo): CardSides {
    val oppCode = game.opponentCode.ifBlank { teamNameToCode(game.opponentName) }
    val focusCode = game.focusTeamCode.ifBlank { LOTTE_TEAM_CODE }
    val lotteLogo = game.lotteLogoUrl.ifBlank { teamLogoUrl(focusCode) }
    val oppLogo = game.opponentLogoUrl.ifBlank { teamLogoUrl(oppCode) }
    return CardSides(
        awayName = if (game.isHome) game.opponentName else game.focusName(),
        homeName = if (game.isHome) game.focusName() else game.opponentName,
        awayScore = if (game.isHome) game.opponentScore else game.lotteScore,
        homeScore = if (game.isHome) game.lotteScore else game.opponentScore,
        awayCode = if (game.isHome) oppCode else focusCode,
        homeCode = if (game.isHome) focusCode else oppCode,
        awayLogoUrl = if (game.isHome) oppLogo else lotteLogo,
        homeLogoUrl = if (game.isHome) lotteLogo else oppLogo,
    )
}

internal fun kickoffTime(game: LotteGameInfo): String {
    val t = game.startTime.trim()
    return Regex("""\d{1,2}:\d{2}""").find(t)?.value ?: t.ifBlank { "예정" }
}

internal fun statusPillText(game: LotteGameInfo): String = when {
    game.isSuspended -> game.suspendLabel
    game.status == GameStatus.ENDED -> "종료"
    game.status == GameStatus.CANCELED -> game.cancelLabel.ifBlank { "취소" }
    game.status == GameStatus.BEFORE -> kickoffTime(game)
    else -> game.inningLabel.ifBlank { "LIVE" }
}

internal fun inningPillBackground(game: LotteGameInfo): Int = when {
    game.isSuspended -> R.drawable.notif_inning_pill_gold
    game.status == GameStatus.LIVE -> R.drawable.notif_inning_pill
    game.status == GameStatus.CANCELED -> R.drawable.notif_inning_pill_cancel
    game.status == GameStatus.ENDED -> R.drawable.notif_inning_pill_muted
    else -> R.drawable.notif_inning_pill_gold
}

internal fun awayStarter(game: LotteGameInfo): String =
    if (game.isHome) game.opponentStartingPitcher else game.lotteStartingPitcher

internal fun homeStarter(game: LotteGameInfo): String =
    if (game.isHome) game.lotteStartingPitcher else game.opponentStartingPitcher

/** 접힌 알림·헤드업용 한 줄 카드 (큰 카드는 64dp에서 잘린다) */
internal fun buildLiveCompactViews(context: Context, game: LotteGameInfo): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.notification_live_compact)
    val side = cardSides(game)
    rv.setTextViewText(R.id.notif_c_away_score, "${side.awayScore}")
    rv.setTextViewText(R.id.notif_c_home_score, "${side.homeScore}")
    rv.setTextViewText(R.id.notif_c_inning, statusPillText(game))
    rv.setInt(R.id.notif_c_inning, "setBackgroundResource", inningPillBackground(game))

    val live = game.status == GameStatus.LIVE && !game.isSuspended
    val before = game.status == GameStatus.BEFORE
    rv.setViewVisibility(R.id.notif_c_bases, if (live) View.VISIBLE else View.GONE)
    if (live) {
        rv.setImageViewResource(
            R.id.notif_c_bases,
            WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
        )
    }
    // 끝난 경기는 점수·종료만으로 충분하다. 좁은 한 줄에 승패까지 넣으면 잘린다.
    // 경기 전은 구장(또는 선발)을 오른쪽에 둔다.
    val note = when {
        live -> ""
        before -> buildString {
            append(if (game.isHome) "홈" else "원정")
            if (game.stadium.isNotBlank()) append(" · ${game.stadium}")
        }
        else -> ""
    }
    rv.setViewVisibility(R.id.notif_c_note, if (note.isNotBlank()) View.VISIBLE else View.GONE)
    if (note.isNotBlank()) rv.setTextViewText(R.id.notif_c_note, note)
    rv.setImageViewBitmap(
        R.id.notif_c_away_logo,
        WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.awayCode, side.awayName),
    )
    rv.setImageViewBitmap(
        R.id.notif_c_home_logo,
        WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.homeCode, side.homeName),
    )
    return rv
}

internal fun buildScoreOnlyViews(context: Context, game: LotteGameInfo): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.notification_live_score)
    val side = cardSides(game)
    rv.setTextViewText(R.id.notif_s_away_score, "${side.awayScore}")
    rv.setTextViewText(R.id.notif_s_home_score, "${side.homeScore}")
    rv.setImageViewBitmap(
        R.id.notif_s_away_logo,
        WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.awayCode, side.awayName),
    )
    rv.setImageViewBitmap(
        R.id.notif_s_home_logo,
        WidgetAssets.loadTeamLogoBitmapCachedOnly(context, side.homeCode, side.homeName),
    )
    return rv
}

internal fun buildLiveRemoteViews(
    context: Context,
    game: LotteGameInfo,
    winProbSeries: List<WinProbPoint> = emptyList(),
    nowBar: NowBarContent? = null,
    pregameProb: Double? = null,
): RemoteViews {
    val rv = RemoteViews(
        context.packageName,
        if (nowBar != null) R.layout.notification_nowbar else R.layout.notification_live,
    )
    val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val side = cardSides(game)
    val awayName = side.awayName
    val homeName = side.homeName
    val awayCode = side.awayCode
    val homeCode = side.homeCode
    val awayLogoUrl = side.awayLogoUrl
    val homeLogoUrl = side.homeLogoUrl

    val awayRank = if (game.isHome) game.opponentRank else game.lotteRank
    val homeRank = if (game.isHome) game.lotteRank else game.opponentRank
    // 라이브 바 카드는 원정/홈 글자 없이 순위만 (없으면 숨김). 상세 알림은 예전처럼 원정/홈.
    rv.setTextViewText(
        R.id.notif_away_place,
        if (awayRank > 0) "${awayRank}위" else if (nowBar != null) "" else "원정",
    )
    rv.setTextViewText(
        R.id.notif_home_place,
        if (homeRank > 0) "${homeRank}위" else if (nowBar != null) "" else "홈",
    )
    rv.setTextViewText(R.id.notif_away_name, awayName)
    rv.setTextViewText(R.id.notif_home_name, homeName)
    rv.setTextViewText(R.id.notif_away_score, "${side.awayScore}")
    rv.setTextViewText(R.id.notif_home_score, "${side.homeScore}")
    if (nowBar != null) {
        rv.setTextViewText(R.id.notif_pill_away_score, "${side.awayScore}")
        rv.setTextViewText(R.id.notif_pill_home_score, "${side.homeScore}")
        rv.setImageViewBitmap(
            R.id.notif_pill_away_logo,
            WidgetAssets.loadTeamLogoBitmapCachedOnly(context, awayCode, awayName),
        )
        rv.setImageViewBitmap(
            R.id.notif_pill_home_logo,
            WidgetAssets.loadTeamLogoBitmapCachedOnly(context, homeCode, homeName),
        )
    }
    rv.setTextViewText(R.id.notif_inning, statusPillText(game))
    if (game.stadium.isNotBlank()) {
        rv.setViewVisibility(R.id.notif_venue, View.VISIBLE)
        rv.setTextViewText(R.id.notif_venue, game.stadium)
    } else {
        rv.setViewVisibility(R.id.notif_venue, View.GONE)
    }
    rv.setInt(R.id.notif_inning, "setBackgroundResource", inningPillBackground(game))
    // 루상·BSO는 진행 중일 때만 뜻이 있다. 끝난 경기에 빈 다이아몬드를 두면 주자가 있는 것처럼 읽힌다.
    val showBases = game.status == GameStatus.LIVE && !game.isSuspended
    rv.setViewVisibility(R.id.notif_bases, if (showBases) View.VISIBLE else View.GONE)
    rv.setViewVisibility(R.id.notif_bso, if (showBases) View.VISIBLE else View.GONE)
    if (showBases) {
        rv.setImageViewResource(
            R.id.notif_bases,
            WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
        )
    }
    val pitcherLine: String
    val batterLine: String
    when (game.status) {
        GameStatus.LIVE -> {
            pitcherLine = buildString {
                append("투수").append(if (nowBar != null) '\n' else ' ')
                append(game.currentPitcherName.ifBlank { "-" })
                if (game.currentPitcherPitchCount > 0) {
                    append(" ")
                    append(game.currentPitcherPitchCount)
                    append("구")
                }
            }
            batterLine = buildString {
                append("타자").append(if (nowBar != null) '\n' else ' ')
                if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                append(game.currentBatterName.ifBlank { "-" })
            }
        }
        GameStatus.BEFORE -> {
            pitcherLine = "선발 ${awayStarter(game).ifBlank { "-" }}"
            batterLine = "선발 ${homeStarter(game).ifBlank { "-" }}"
        }
        GameStatus.CANCELED -> {
            pitcherLine = if (game.stadium.isNotBlank()) "구장 ${game.stadium}" else ""
            batterLine = ""
        }
        GameStatus.ENDED -> {
            pitcherLine = buildString {
                append("승 ")
                append(game.winPitcherName.ifBlank { "-" })
                if (game.savePitcherName.isNotBlank()) {
                    append(" · 세 ")
                    append(game.savePitcherName)
                }
            }
            batterLine = "패 ${game.losePitcherName.ifBlank { "-" }}"
        }
    }
    rv.setTextViewText(R.id.notif_pitcher_line, pitcherLine)
    rv.setTextViewText(R.id.notif_batter_line, batterLine)
    // 라이브 바: 경기 전만 승률. 경기 중·후는 스코어보드(승률 바 숨김).
    // 상세 알림(FULL): 기존처럼 진행·종료 승률 바.
    val showWinProb = if (nowBar != null) {
        game.status == GameStatus.BEFORE && pregameProb != null
    } else {
        WinProb.shouldShowWinProbBar(game)
    }
    rv.setViewVisibility(R.id.notif_winprob_row, if (showWinProb) View.VISIBLE else View.GONE)
    fun setDots(ids: IntArray, count: Int, kind: Char) {
        ids.forEachIndexed { i, id ->
            rv.setImageViewResource(id, WidgetAssets.countDot(i < count, kind))
        }
    }
    val ball = if (showBases) game.ball else 0
    val strike = if (showBases) game.strike else 0
    val out = if (showBases) game.out else 0
    setDots(intArrayOf(R.id.notif_b0, R.id.notif_b1, R.id.notif_b2, R.id.notif_b3), ball, 'B')
    setDots(intArrayOf(R.id.notif_s0, R.id.notif_s1, R.id.notif_s2), strike, 'S')
    setDots(intArrayOf(R.id.notif_o0, R.id.notif_o1, R.id.notif_o2), out, 'O')

    val lotteProb = pregameProb.takeIf { game.status == GameStatus.BEFORE }
        ?: WinProb.resolveDisplayFocusProb(game, winProbSeries.lastOrNull()?.homeProb)
        ?: estimateLotteWinProb(game)
    val (awayProb, homeProb) = WinProb.awayHomeFromFocus(game, lotteProb)
    if (showWinProb) {
        val (awayPct, homePct) = WinProb.displayPercents(awayProb, homeProb)
        rv.setTextViewText(R.id.notif_winprob_left, "${awayName} ${awayPct}%")
        rv.setTextViewText(R.id.notif_winprob_right, "${homePct}% ${homeName}")
        val bar = WidgetAssets.winProbBarBitmap(
            leftProb = awayProb.toFloat(),
            leftColor = WidgetAssets.winProbBarColor(awayCode),
            rightColor = WidgetAssets.winProbBarColor(homeCode),
        )
        rv.setImageViewBitmap(R.id.notif_winprob_bar, bar)
    }

    // 라이브 바: 경기 전 일시·구장, 경기 후 다음 경기를 맨 아래 한 줄로. 상세 알림은 없음.
    val infoText = ""
    rv.setViewVisibility(R.id.notif_info_line, if (infoText.isNotBlank()) View.VISIBLE else View.GONE)
    if (infoText.isNotBlank()) rv.setTextViewText(R.id.notif_info_line, infoText)
    // 라이브 바 경기 중·종료: 이닝별 점수표 + R/H/E (승률 바 대신)
    val showBoard = nowBar != null &&
        (game.status == GameStatus.LIVE || game.status == GameStatus.ENDED)
    rv.setViewVisibility(R.id.notif_scoreboard, if (showBoard) View.VISIBLE else View.GONE)
    if (showBoard) rv.setImageViewBitmap(R.id.notif_scoreboard, NowBarArt.scoreboard(game, night))
    if (nowBar != null) {
        val ink = if (night) 0xFFF2F2F2.toInt() else 0xFF1B1B1F.toInt()
        val sub = if (night) 0xFFB5B8BF.toInt() else 0xFF5F636B.toInt()
        intArrayOf(
            R.id.notif_away_name, R.id.notif_home_name,
            R.id.notif_away_score, R.id.notif_home_score,
            R.id.notif_pitcher_line, R.id.notif_batter_line, R.id.notif_info_line,
        ).forEach { rv.setTextColor(it, ink) }
        intArrayOf(
            R.id.notif_away_place, R.id.notif_home_place, R.id.notif_venue,
            R.id.notif_winprob_left, R.id.notif_winprob_right,
        ).forEach { rv.setTextColor(it, sub) }
    }

    val awayBmp = WidgetAssets.loadTeamLogoBitmapCachedOnly(context, awayCode, awayName)
    val homeBmp = WidgetAssets.loadTeamLogoBitmapCachedOnly(context, homeCode, homeName)
    rv.setImageViewBitmap(R.id.notif_away_logo, awayBmp)
    rv.setImageViewBitmap(R.id.notif_home_logo, homeBmp)
    return rv
}
