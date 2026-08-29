package com.bossxor.lottegiants.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_LOGO_URL
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.WinProbPoint
import com.bossxor.lottegiants.domain.cancelLabel
import com.bossxor.lottegiants.domain.estimateLotteWinProb
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.teamAccentColor
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.teamNameToCode
import com.bossxor.lottegiants.widget.WidgetAssets
import kotlinx.coroutines.runBlocking

object NotificationHelper {

    const val CHANNEL_LIVE = "live_score"
    /**
     * 스코어카드(커스텀 RemoteViews)용.
     * colorized는 DEFAULT 이상에서만 적용된다. 기존 live_score는 LOW라 회색 테두리가 남았다.
     */
    const val CHANNEL_LIVE_CARD = "live_score_card_v3"
    /**
     * Now Bar/Live Update용.
     * v2: One UI 9는 채널이 DEFAULT여도 되지만 HIGH가 칩 노출이 더 잘 된다.
     * 채널 중요도는 생성 후 코드로 못 바꿔서 ID를 올렸다.
     */
    const val CHANNEL_LIVE_NOW = "live_score_nowbar_v2"
    const val CHANNEL_SCORE = "event_score"
    const val CHANNEL_CONCEDE = "event_concede"
    const val CHANNEL_PITCHER = "event_pitcher"
    const val CHANNEL_HOMERUN = "event_homerun"
    const val CHANNEL_CHANCE = "event_chance"
    const val CHANNEL_LEAD = "event_lead"
    const val CHANNEL_INNING = "event_inning"
    const val CHANNEL_EIGHTH = "event_eighth"
    const val CHANNEL_EXTRA = "event_extra"
    const val CHANNEL_GAME = "event_game"
    const val CHANNEL_PREGAME = "event_pregame"
    // v2: 기존 채널은 중요도가 낮아 헤드업으로 뜨지 않았다. 채널 중요도는 생성 후 코드로 못 바꾼다.
    const val CHANNEL_LINEUP = "event_lineup_v2"
    const val CHANNEL_CANCEL = "event_cancel"
    const val CHANNEL_FAVORITE = "event_favorite"
    const val CHANNEL_ROSTER = "event_roster"
    const val CHANNEL_RACE = "event_race"

    const val LIVE_NOTIFICATION_ID = 1001

    /** 종료·취소 알림은 2시간 뒤 스스로 사라진다 (서비스가 멈춘 뒤에도 남는 걸 막는다). */
    private const val FINISHED_NOTIFICATION_TIMEOUT_MS = 2 * 60 * 60 * 1000L

    private const val REGULATION_INNINGS = 9
    private const val COLOR_LOTTE = 0xFFD00F31.toInt()
    private const val COLOR_NAVY = 0xFF0B2A4A.toInt()
    private const val COLOR_OPPONENT = 0xFF9AA0A6.toInt()
    private const val COLOR_TRACK = 0xFF4A4F55.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) =
            NotificationChannel(id, name, importance).also { nm.createNotificationChannel(it) }

        ch(CHANNEL_LIVE, "실시간 스코어", NotificationManager.IMPORTANCE_LOW)
        ch(CHANNEL_LIVE_CARD, "실시간 스코어카드", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_LIVE_NOW, "Now Bar 실시간 점수", NotificationManager.IMPORTANCE_HIGH)
        runCatching { nm.deleteNotificationChannel("live_score_nowbar") }
        runCatching { nm.deleteNotificationChannel("live_score_card_v2") }
        ch(CHANNEL_SCORE, "득점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CONCEDE, "실점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PITCHER, "투수 교체")
        ch(CHANNEL_HOMERUN, "홈런", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CHANCE, "득점권 찬스", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_LEAD, "역전/동점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_INNING, "이닝 교대")
        ch(CHANNEL_EIGHTH, "8회말", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_EXTRA, "연장", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_GAME, "경기 시작/종료", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PREGAME, "경기 30분 전")
        ch(CHANNEL_LINEUP, "선발 라인업", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CANCEL, "경기 취소", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_FAVORITE, "즐겨찾기 선수", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_ROSTER, "엔트리 등말소", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_RACE, "매직·트래직", NotificationManager.IMPORTANCE_DEFAULT)

        runCatching { nm.deleteNotificationChannel("event_lineup") }
    }

    fun buildLiveNotification(
        context: Context,
        game: LotteGameInfo?,
        mode: LiveDisplayMode = LiveDisplayMode.LOCK_NOW,
        winProbSeries: List<WinProbPoint> = emptyList(),
    ): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, "live")
                .putExtra(MainActivity.EXTRA_GAME_ID, game?.gameId.orEmpty()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val scoreTitle = if (game == null) {
            "롯데 라이브"
        } else {
            "롯데 ${game.lotteScore} : ${game.opponentScore} ${game.opponentName}"
        }
        val summary = gameSummary(game)
        val compactLine = gameCompactLine(game)
        val chipText = nowBarChipText(game)
        val headerLine = if (game != null && game.status == GameStatus.LIVE) {
            buildString {
                append(game.inningLabel)
                append(if (game.isLotteBatting) " · 롯데 공격" else " · 상대 공격")
            }
        } else {
            compactLine
        }
        val countBmp = game?.takeIf { it.status == GameStatus.LIVE }?.let {
            WidgetAssets.ballCountBitmap(
                context,
                it.ball,
                it.strike,
                it.out,
                it.onBase1,
                it.onBase2,
                it.onBase3,
            )
        }

        val (title, text) = when (mode) {
            LiveDisplayMode.STATUS_SCORE -> {
                val shortTitle = if (game == null) "롯데 라이브"
                else "롯데 ${game.lotteScore}:${game.opponentScore}"
                shortTitle to (game?.inningLabel ?: "")
            }
            LiveDisplayMode.FULL -> scoreTitle to summary
            LiveDisplayMode.LOCK_NOW -> scoreTitle to headerLine
        }

        // `상세 알림`만 스코어카드. `라이브 바`는 ProgressStyle이어야 Now Bar 칩으로 승격된다.
        val useCustom = mode == LiveDisplayMode.FULL &&
            game != null &&
            (game.status == GameStatus.LIVE || game.status == GameStatus.ENDED)
        // 경기가 끝나면 서비스가 멈춰도 알림은 남는다. 손으로 지울 수 있게 두고 스스로 만료시킨다.
        val finished = game != null &&
            (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED)

        val builder = NotificationCompat.Builder(
            context,
            if (useCustom) CHANNEL_LIVE_CARD else CHANNEL_LIVE_NOW,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(!finished)
            .setAutoCancel(finished)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(COLOR_LOTTE)
        if (finished) builder.setTimeoutAfter(FINISHED_NOTIFICATION_TIMEOUT_MS)

        val hide = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, GameAlarmReceiver::class.java).setAction(GameSchedulerWorker.ACTION_HIDE_LIVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (useCustom) {
            // DEFAULT+ 채널에서 colorized하면 알림 전체가 네이비로 칠해져 카드가 가로를 채운 것처럼 보인다.
            // DecoratedCustomViewStyle/액션은 쓰지 않는다 (칩 숨기기·안쪽 여백 원인).
            val card = buildLiveRemoteViews(context, game!!, winProbSeries)
            builder
                .setColor(COLOR_NAVY)
                .setColorized(true)
                .setCustomContentView(card)
                .setCustomBigContentView(card)
                .setCustomHeadsUpContentView(card)
                .setDeleteIntent(hide)
                .setSubText(null)
                .setShortCriticalText(null)
                .setRequestPromotedOngoing(false)
        } else {
            builder
                .setDeleteIntent(hide)
                .setSubText(chipText)
                .setShortCriticalText(chipText)
                .setColorized(false)
                .setStyle(liveProgressStyle(context, game, countBmp))
                .setRequestPromotedOngoing(!finished)
            if (countBmp != null) builder.setLargeIcon(countBmp)
        }

        return builder.build()
    }

    /** Now Bar 칩은 대략 7자면 잘린다. 점수는 `3:2`, 전이면 `18:30`. */
    fun nowBarChipText(game: LotteGameInfo?): String {
        val raw = when {
            game == null -> "대기"
            game.status == GameStatus.BEFORE -> {
                val t = game.startTime.trim()
                Regex("""\d{1,2}:\d{2}""").find(t)?.value ?: t.ifBlank { "예정" }
            }
            else -> "${game.lotteScore}:${game.opponentScore}"
        }
        return if (raw.length <= 7) raw else raw.take(7)
    }

    /**
     * 이닝을 구간으로 나눈 진행 바. 각 이닝은 초·말 2칸이고 득점한 이닝은 점으로 찍는다.
     * ProgressStyle은 Live Update로 승격 가능한 스타일이라 One UI Now Bar에도 그대로 실린다.
     */
    private fun liveProgressStyle(
        context: Context,
        game: LotteGameInfo?,
        countBmp: Bitmap? = null,
    ): NotificationCompat.ProgressStyle {
        val innings = maxOf(REGULATION_INNINGS, game?.inning ?: REGULATION_INNINGS)
        val total = innings * 2
        val current = when (game?.status) {
            GameStatus.LIVE ->
                ((game.inning - 1).coerceAtLeast(0) * 2 + if (game.isTopInning) 1 else 2)
                    .coerceIn(0, total)
            GameStatus.ENDED -> total
            else -> 0
        }

        fun scoringPoints(scores: List<String>, isBottomHalf: Boolean, color: Int) =
            scores.mapIndexedNotNull { i, raw ->
                if ((raw.trim().toIntOrNull() ?: 0) <= 0) return@mapIndexedNotNull null
                val pos = i * 2 + if (isBottomHalf) 2 else 1
                if (pos > total) null else NotificationCompat.ProgressStyle.Point(pos).setColor(color)
            }

        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                List(innings) { NotificationCompat.ProgressStyle.Segment(2).setColor(COLOR_TRACK) },
            )
            .setProgressPoints(
                if (game == null) {
                    emptyList()
                } else {
                    scoringPoints(game.lotteInningScores, game.isHome, COLOR_LOTTE) +
                        scoringPoints(game.opponentInningScores, !game.isHome, COLOR_OPPONENT)
                },
            )
            .setProgress(current)
            .setStyledByProgress(false)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_notification))
        if (countBmp != null) {
            style.setProgressStartIcon(IconCompat.createWithBitmap(countBmp))
        }
        return style
    }

    fun canPostNowBar(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    data class NowBarStatus(
        val apiOk: Boolean,
        val canPost: Boolean,
        val livePosted: Boolean,
        val promotable: Boolean,
        val promoted: Boolean,
    )

    fun nowBarStatus(context: Context): NowBarStatus {
        val apiOk = Build.VERSION.SDK_INT >= 36
        val canPost = canPostNowBar(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val posted = nm.activeNotifications.firstOrNull { it.id == LIVE_NOTIFICATION_ID }?.notification
        val sample = posted ?: buildLiveNotification(context, null)
        val promotable = apiOk && runCatching { sample.hasPromotableCharacteristics() }.getOrDefault(false)
        val promoted = apiOk && posted != null &&
            runCatching { posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0 }.getOrDefault(false)
        return NowBarStatus(
            apiOk = apiOk,
            canPost = canPost,
            livePosted = posted != null,
            promotable = promotable,
            promoted = promoted,
        )
    }

    fun nowBarStatusLabel(status: NowBarStatus): String = when {
        !status.apiOk -> "이 기기는 라이브 알림(Now Bar)을 지원하지 않습니다."
        !status.canPost ->
            "Now Bar가 꺼져 있습니다. One UI 9: 설정 → 알림 → 라이브 알림, 또는 설정 → 잠금화면 → Now bar에서 사직스코어를 켜 주세요."
        status.promoted -> "Now Bar에 표시 중입니다."
        status.livePosted && status.promotable ->
            "승격 가능한 알림입니다. 칩이 안 보이면 잠금화면 Now bar 목록에서 사직스코어를 켜 보세요."
        status.livePosted && !status.promotable ->
            "지금 알림은 승격 조건에 안 맞습니다. 아래 다시 표시를 눌러 주세요."
        else -> "실시간 스코어를 켜면 잠금화면·상태바 칩에 점수가 올라갑니다."
    }

    fun openNowBarSettings(context: Context) {
        val pkg = Uri.parse("package:${context.packageName}")
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val candidates = listOf(
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                .setData(pkg)
                .addFlags(flags),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(flags),
        )
        for (intent in candidates) {
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return
        }
    }

    /** 알림 접힘 상태용 한 줄 */
    private fun gameCompactLine(game: LotteGameInfo?): String {
        if (game == null) return "대기 중"
        if (game.status != GameStatus.LIVE) {
            return game.inningLabel.ifBlank { game.opponentName }
        }
        return buildString {
            append(game.inningLabel)
            append("  ${basesLabel(game)}")
            if (game.currentPitcherName.isNotBlank()) {
                append("  투 ${game.currentPitcherName}")
                if (game.currentPitcherPitchCount > 0) append("(${game.currentPitcherPitchCount})")
            }
            if (game.currentBatterName.isNotBlank()) {
                append("  타 ")
                if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                append(game.currentBatterName)
            }
        }
    }

    /** 루타앱 경기요약에 가까운 전체 텍스트 (요약 탭과 동일 소스) */
    private fun gameSummary(game: LotteGameInfo?): String {
        if (game == null) return "대기 중"
        if (game.status != GameStatus.LIVE) {
            return buildString {
                append(game.inningLabel.ifBlank { game.statusText.ifBlank { game.opponentName } })
                if (game.stadium.isNotBlank()) append("\n구장  ${game.stadium}")
                if (game.lotteStartingPitcher.isNotBlank() || game.opponentStartingPitcher.isNotBlank()) {
                    append("\n선발  롯데 ${game.lotteStartingPitcher.ifBlank { "-" }}")
                    append("  ·  ${game.opponentName} ${game.opponentStartingPitcher.ifBlank { "-" }}")
                }
            }
        }
        return buildString {
            append(game.inningLabel)
            if (game.isLotteBatting) append("  ·  롯데 공격") else append("  ·  상대 공격")
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

    private fun basesLabel(game: LotteGameInfo): String {
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

    private fun buildLiveRemoteViews(
        context: Context,
        game: LotteGameInfo,
        winProbSeries: List<WinProbPoint> = emptyList(),
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notification_live)
        val awayName = if (game.isHome) game.opponentName else "롯데"
        val homeName = if (game.isHome) "롯데" else game.opponentName
        val awayScore = if (game.isHome) game.opponentScore else game.lotteScore
        val homeScore = if (game.isHome) game.lotteScore else game.opponentScore
        val awayCode = if (game.isHome) {
            game.opponentCode.ifBlank { teamNameToCode(game.opponentName) }
        } else {
            LOTTE_TEAM_CODE
        }
        val homeCode = if (game.isHome) LOTTE_TEAM_CODE else {
            game.opponentCode.ifBlank { teamNameToCode(game.opponentName) }
        }
        val awayLogoUrl = if (game.isHome) {
            game.opponentLogoUrl.ifBlank { teamLogoUrl(awayCode) }
        } else {
            game.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL }
        }
        val homeLogoUrl = if (game.isHome) {
            game.lotteLogoUrl.ifBlank { LOTTE_LOGO_URL }
        } else {
            game.opponentLogoUrl.ifBlank { teamLogoUrl(homeCode) }
        }

        rv.setTextViewText(R.id.notif_away_name, awayName)
        rv.setTextViewText(R.id.notif_home_name, homeName)
        rv.setTextViewText(R.id.notif_away_score, "$awayScore")
        rv.setTextViewText(R.id.notif_home_score, "$homeScore")
        rv.setTextViewText(R.id.notif_inning, when (game.status) {
            GameStatus.ENDED -> "종료"
            GameStatus.CANCELED -> game.cancelLabel.ifBlank { "취소" }
            else -> game.inningLabel.ifBlank { "LIVE" }
        })
        // 루상·BSO는 진행 중일 때만 뜻이 있다. 끝난 경기에 빈 다이아몬드를 두면 주자가 있는 것처럼 읽힌다.
        val showBases = game.status == GameStatus.LIVE
        rv.setViewVisibility(R.id.notif_bases, if (showBases) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.notif_bso, if (showBases) View.VISIBLE else View.GONE)
        if (showBases) {
            rv.setImageViewResource(
                R.id.notif_bases,
                WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
            )
        }
        val pitcherLine = if (showBases) {
            buildString {
                append("투수 ")
                append(game.currentPitcherName.ifBlank { "-" })
                if (game.currentPitcherPitchCount > 0) {
                    append(" ")
                    append(game.currentPitcherPitchCount)
                    append("구")
                }
            }
        } else {
            buildString {
                append("승 ")
                append(game.winPitcherName.ifBlank { "-" })
                if (game.savePitcherName.isNotBlank()) {
                    append(" · 세 ")
                    append(game.savePitcherName)
                }
            }
        }
        val batterLine = if (showBases) {
            buildString {
                append("타자 ")
                if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                append(game.currentBatterName.ifBlank { "-" })
            }
        } else {
            "패 ${game.losePitcherName.ifBlank { "-" }}"
        }
        rv.setTextViewText(R.id.notif_pitcher_line, pitcherLine)
        rv.setTextViewText(R.id.notif_batter_line, batterLine)
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

        val lotteProb = winProbSeries.lastOrNull()?.homeProb
            ?: estimateLotteWinProb(game)
        val awayProb = if (game.isHome) (1.0 - lotteProb) else lotteProb
        val bar = WidgetAssets.winProbBarBitmap(
            leftProb = awayProb.toFloat(),
            leftColor = teamAccentColor(awayCode),
            rightColor = teamAccentColor(homeCode),
        )
        rv.setImageViewBitmap(R.id.notif_winprob_bar, bar)

        val awayBmp = runBlocking {
            WidgetAssets.loadTeamLogoBitmap(context, awayCode, awayLogoUrl, awayName)
        }
        val homeBmp = runBlocking {
            WidgetAssets.loadTeamLogoBitmap(context, homeCode, homeLogoUrl, homeName)
        }
        rv.setImageViewBitmap(R.id.notif_away_logo, awayBmp)
        rv.setImageViewBitmap(R.id.notif_home_logo, homeBmp)
        return rv
    }

    fun notifyEvent(
        context: Context,
        type: NotificationType,
        title: String,
        text: String,
        id: Int,
        gameId: String = "",
        detailTab: String? = null,
    ) {
        val channel = when (type) {
            NotificationType.SCORE -> CHANNEL_SCORE
            NotificationType.CONCEDING -> CHANNEL_CONCEDE
            NotificationType.PITCHER_CHANGE -> CHANNEL_PITCHER
            NotificationType.HOMERUN -> CHANNEL_HOMERUN
            NotificationType.SCORING_CHANCE -> CHANNEL_CHANCE
            NotificationType.LEAD_CHANGE -> CHANNEL_LEAD
            NotificationType.INNING_CHANGE -> CHANNEL_INNING
            NotificationType.EIGHTH_INNING -> CHANNEL_EIGHTH
            NotificationType.EXTRA_INNINGS -> CHANNEL_EXTRA
            NotificationType.GAME_START, NotificationType.GAME_END -> CHANNEL_GAME
            NotificationType.PREGAME_REMINDER -> CHANNEL_PREGAME
            NotificationType.LINEUP -> CHANNEL_LINEUP
            NotificationType.CANCELED -> CHANNEL_CANCEL
            NotificationType.ROSTER -> CHANNEL_ROSTER
            NotificationType.FAVORITE_AT_BAT,
            NotificationType.FAVORITE_PITCHING,
            NotificationType.FAVORITE_ROSTER,
            -> CHANNEL_FAVORITE
            NotificationType.RACE_NUMBER -> CHANNEL_RACE
        }
        val openTab = when (type) {
            NotificationType.ROSTER, NotificationType.FAVORITE_ROSTER -> "entry"
            NotificationType.RACE_NUMBER -> "standings"
            else -> "live"
        }
        val tab = detailTab ?: when (type) {
            NotificationType.SCORE, NotificationType.HOMERUN,
            NotificationType.CONCEDING, NotificationType.LEAD_CHANGE,
            NotificationType.PITCHER_CHANGE, NotificationType.FAVORITE_PITCHING,
            -> "relay"
            else -> null
        }
        val content = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
                .putExtra(MainActivity.EXTRA_GAME_ID, gameId)
                .apply { if (!tab.isNullOrBlank()) putExtra(MainActivity.EXTRA_DETAIL_TAB, tab) }
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
