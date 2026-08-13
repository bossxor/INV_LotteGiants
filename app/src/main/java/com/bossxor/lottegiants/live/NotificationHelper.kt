package com.bossxor.lottegiants.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.inningLabel
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.widget.WidgetAssets
import kotlinx.coroutines.runBlocking

object NotificationHelper {

    const val CHANNEL_LIVE = "live_score"
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
    const val CHANNEL_LINEUP = "event_lineup"
    const val CHANNEL_CANCEL = "event_cancel"
    const val CHANNEL_FAVORITE = "event_favorite"
    const val CHANNEL_ROSTER = "event_roster"

    const val LIVE_NOTIFICATION_ID = 1001

    private const val REGULATION_INNINGS = 9
    private const val COLOR_LOTTE = 0xFFD00F31.toInt()
    private const val COLOR_OPPONENT = 0xFF9AA0A6.toInt()
    private const val COLOR_TRACK = 0xFF4A4F55.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) =
            NotificationChannel(id, name, importance).also { nm.createNotificationChannel(it) }

        ch(CHANNEL_LIVE, "실시간 스코어", NotificationManager.IMPORTANCE_LOW)
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
        ch(CHANNEL_LINEUP, "선발 라인업")
        ch(CHANNEL_CANCEL, "경기 취소", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_FAVORITE, "즐겨찾기 선수", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CHANNEL_ROSTER, "엔트리 등말소", NotificationManager.IMPORTANCE_HIGH)
    }

    fun buildLiveNotification(
        context: Context,
        game: LotteGameInfo?,
        mode: LiveDisplayMode = LiveDisplayMode.LOCK_NOW,
    ): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, "live"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val scoreTitle = if (game == null) {
            "롯데 라이브"
        } else {
            "롯데 ${game.lotteScore} : ${game.opponentScore} ${game.opponentName}"
        }
        val summary = gameSummary(game)
        val compactLine = gameCompactLine(game)
        val chipText = if (game == null) {
            "대기"
        } else {
            "롯데 ${game.lotteScore}:${game.opponentScore} · ${game.inningLabel}"
        }
        val headerLine = if (game != null && game.status == GameStatus.LIVE) {
            game.inningLabel
        } else {
            compactLine
        }
        val countBmp = game?.takeIf { it.status == GameStatus.LIVE }?.let {
            WidgetAssets.ballCountBitmap(context, it.ball, it.strike, it.out)
        }

        val (title, text) = when (mode) {
            LiveDisplayMode.STATUS_SCORE -> {
                val shortTitle = if (game == null) "롯데 라이브"
                else "롯데 ${game.lotteScore}:${game.opponentScore}"
                shortTitle to (game?.inningLabel ?: "")
            }
            LiveDisplayMode.FULL, LiveDisplayMode.LOCK_NOW -> scoreTitle to headerLine
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(mode != LiveDisplayMode.STATUS_SCORE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSubText(if (mode == LiveDisplayMode.STATUS_SCORE) chipText else null)
            .setShortCriticalText(chipText)
            .setColor(COLOR_LOTTE)

        // 커스텀 RemoteViews가 붙은 알림은 Live Update로 승격될 수 없다 (플랫폼 제약).
        // 따라서 '상세' 모드에서만 커스텀 뷰를 쓰고, 나머지는 승격 가능한 표준 스타일을 쓴다.
        val useCustom = mode == LiveDisplayMode.FULL &&
            game != null &&
            game.status == GameStatus.LIVE
        if (countBmp != null && !useCustom) {
            builder.setLargeIcon(countBmp)
        }
        when {
            useCustom -> builder
                .setCustomContentView(buildLiveRemoteViews(context, game!!, big = false))
                .setCustomBigContentView(buildLiveRemoteViews(context, game, big = true))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            // 진행 중 경기는 이닝 진행 바로 — Now Bar에서 구글/네이버 스포츠처럼 크게 뜬다
            game != null && game.status == GameStatus.LIVE && mode != LiveDisplayMode.STATUS_SCORE ->
                builder.setStyle(liveProgressStyle(context, game, countBmp))

            mode != LiveDisplayMode.STATUS_SCORE -> builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(scoreTitle)
                    .bigText(summary),
            )
        }

        if (!useCustom) {
            builder.setRequestPromotedOngoing(true)
        }

        return builder.build()
    }

    /**
     * 이닝을 구간으로 나눈 진행 바. 각 이닝은 초·말 2칸이고 득점한 이닝은 점으로 찍는다.
     * ProgressStyle은 Live Update로 승격 가능한 스타일이라 One UI Now Bar에도 그대로 실린다.
     */
    private fun liveProgressStyle(
        context: Context,
        game: LotteGameInfo,
        countBmp: Bitmap? = null,
    ): NotificationCompat.ProgressStyle {
        val innings = maxOf(REGULATION_INNINGS, game.inning)
        val total = innings * 2
        val current = ((game.inning - 1).coerceAtLeast(0) * 2 + if (game.isTopInning) 1 else 2)
            .coerceIn(0, total)

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
                scoringPoints(game.lotteInningScores, game.isHome, COLOR_LOTTE) +
                    scoringPoints(game.opponentInningScores, !game.isHome, COLOR_OPPONENT),
            )
            .setProgress(current)
            .setStyledByProgress(false)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_notification))
        if (countBmp != null) {
            style.setProgressStartIcon(IconCompat.createWithBitmap(countBmp))
        }
        return style
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

    private fun buildLiveRemoteViews(context: Context, game: LotteGameInfo, big: Boolean): RemoteViews {
        val rv = RemoteViews(
            context.packageName,
            if (big) R.layout.notification_live_big else R.layout.notification_live,
        )
        rv.setTextViewText(
            R.id.notif_score,
            "롯데 ${game.lotteScore} : ${game.opponentScore} ${game.opponentName}",
        )
        rv.setTextViewText(
            R.id.notif_inning,
            buildString {
                append(game.inningLabel)
                if (game.isLotteBatting) append(" · 롯데 공격") else append(" · 상대 공격")
            },
        )
        rv.setTextViewText(R.id.notif_count_text, basesLabel(game))
        val batterLabel = buildString {
            if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
            append(game.currentBatterName.ifBlank { "-" })
        }
        rv.setTextViewText(
            R.id.notif_players_line,
            buildString {
                append("투 ${game.currentPitcherName.ifBlank { "-" }}")
                if (game.currentPitcherPitchCount > 0) append("(${game.currentPitcherPitchCount}구)")
                append("  ·  타 $batterLabel")
            },
        )
        rv.setImageViewResource(
            R.id.notif_bases,
            WidgetAssets.basesDrawable(game.onBase1, game.onBase2, game.onBase3),
        )
        fun setDots(ids: IntArray, count: Int, kind: Char) {
            ids.forEachIndexed { i, id ->
                rv.setImageViewResource(id, WidgetAssets.countDot(i < count, kind))
            }
        }
        setDots(intArrayOf(R.id.notif_b0, R.id.notif_b1, R.id.notif_b2, R.id.notif_b3), game.ball, 'B')
        setDots(intArrayOf(R.id.notif_s0, R.id.notif_s1, R.id.notif_s2), game.strike, 'S')
        setDots(intArrayOf(R.id.notif_o0, R.id.notif_o1, R.id.notif_o2), game.out, 'O')

        if (big) {
            rv.setTextViewText(R.id.notif_bases_label, "루상  ${basesLabel(game)}")
            rv.setTextViewText(
                R.id.notif_pitcher_name,
                buildString {
                    append(game.currentPitcherName.ifBlank { "-" })
                    if (game.currentPitcherPitchCount > 0) append(" (${game.currentPitcherPitchCount}구)")
                },
            )
            rv.setTextViewText(R.id.notif_batter_name, batterLabel)
            rv.setTextViewText(
                R.id.notif_next_batter,
                if (game.nextBatterName.isNotBlank()) "다음 타자  ${game.nextBatterName}" else "",
            )
            val batterCode = (game.lotteLineup + game.opponentLineup + game.lotteBenchBatters + game.opponentBenchBatters)
                .firstOrNull { it.name == game.currentBatterName }?.playerCode.orEmpty()
            val pitcherBmp = runBlocking {
                WidgetAssets.loadPlayerBitmap(context, game.currentPitcherCode, playerPhotoUrl(game.currentPitcherCode))
            }
            val batterBmp = runBlocking {
                WidgetAssets.loadPlayerBitmap(context, batterCode, playerPhotoUrl(batterCode))
            }
            setPhotoOrFallback(rv, R.id.notif_pitcher_photo, pitcherBmp)
            setPhotoOrFallback(rv, R.id.notif_batter_photo, batterBmp)
        }
        return rv
    }

    private fun setPhotoOrFallback(rv: RemoteViews, id: Int, bmp: Bitmap?) {
        if (bmp != null) rv.setImageViewBitmap(id, bmp)
        else rv.setImageViewResource(id, R.drawable.ic_notification)
    }

    fun notifyEvent(
        context: Context,
        type: NotificationType,
        title: String,
        text: String,
        id: Int,
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
            NotificationType.FAVORITE_AT_BAT, NotificationType.FAVORITE_ROSTER -> CHANNEL_FAVORITE
        }
        val openTab = when (type) {
            NotificationType.ROSTER, NotificationType.FAVORITE_ROSTER -> "entry"
            else -> "live"
        }
        val content = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
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
