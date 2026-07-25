package com.bossxor.lottegiants.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bossxor.lottegiants.MainActivity
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.inningLabel

object NotificationHelper {

    const val CHANNEL_LIVE = "live_score"
    const val CHANNEL_SCORE = "event_score"
    const val CHANNEL_PITCHER = "event_pitcher"
    const val CHANNEL_HOMERUN = "event_homerun"
    const val CHANNEL_CHANCE = "event_chance"
    const val CHANNEL_LEAD = "event_lead"
    const val CHANNEL_INNING = "event_inning"
    const val CHANNEL_GAME = "event_game"
    const val CHANNEL_PREGAME = "event_pregame"
    const val CHANNEL_LINEUP = "event_lineup"
    const val CHANNEL_CANCEL = "event_cancel"

    const val LIVE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) =
            NotificationChannel(id, name, importance).also { nm.createNotificationChannel(it) }

        ch(CHANNEL_LIVE, "실시간 스코어 (Now Bar)", NotificationManager.IMPORTANCE_LOW)
        ch(CHANNEL_SCORE, "득점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PITCHER, "투수 교체")
        ch(CHANNEL_HOMERUN, "홈런", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_CHANCE, "득점권 찬스", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_LEAD, "역전/동점", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_INNING, "이닝 교대")
        ch(CHANNEL_GAME, "경기 시작/종료", NotificationManager.IMPORTANCE_HIGH)
        ch(CHANNEL_PREGAME, "경기 30분 전")
        ch(CHANNEL_LINEUP, "선발 라인업")
        ch(CHANNEL_CANCEL, "경기 취소", NotificationManager.IMPORTANCE_HIGH)
    }

    fun buildLiveNotification(context: Context, game: LotteGameInfo?): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title: String
        val text: String
        if (game == null) {
            title = "롯데 라이브"
            text = "대기 중"
        } else {
            title = "롯데 ${game.lotteScore} : ${game.opponentScore} ${game.opponentName}"
            text = buildString {
                append(game.inningLabel)
                if (game.status == GameStatus.LIVE) {
                    append("  B${game.ball} S${game.strike} O${game.out}")
                    if (game.currentBatterName.isNotBlank()) append("  ${game.currentBatterName}")
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Android 16+ Live Updates (Now Bar)
        try {
            val extras = builder.build().extras
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
        } catch (_: Exception) { /* older platform */ }

        // Prefer official API when available
        @Suppress("NewApi")
        try {
            builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        } catch (_: Exception) { }

        return builder.build()
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
            NotificationType.PITCHER_CHANGE -> CHANNEL_PITCHER
            NotificationType.HOMERUN -> CHANNEL_HOMERUN
            NotificationType.SCORING_CHANCE -> CHANNEL_CHANCE
            NotificationType.LEAD_CHANGE -> CHANNEL_LEAD
            NotificationType.INNING_CHANGE -> CHANNEL_INNING
            NotificationType.GAME_START_END -> CHANNEL_GAME
            NotificationType.PREGAME_REMINDER -> CHANNEL_PREGAME
            NotificationType.LINEUP -> CHANNEL_LINEUP
            NotificationType.CANCELED -> CHANNEL_CANCEL
        }
        val pi = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
