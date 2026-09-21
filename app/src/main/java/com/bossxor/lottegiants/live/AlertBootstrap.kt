package com.bossxor.lottegiants.live

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bossxor.lottegiants.data.AlertHistoryItem
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.belongsToKboToday
import com.bossxor.lottegiants.domain.dhSuffix
import com.bossxor.lottegiants.domain.isCanceledGame
import com.bossxor.lottegiants.domain.kboToday
import com.bossxor.lottegiants.domain.shouldEmitAlert
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 앱을 열지 않아도 엔트리·라인업 알림이 돌아가게 부팅·알람·워커 진입점을 맞춘다.
 */
object AlertBootstrap {

    suspend fun run(context: Context) {
        val app = context.applicationContext
        val repo = GiantsRepository.get(app)
        NotificationHelper.createChannels(app)
        val detector = EventDetector(repo.store)
        GameSchedulerWorker.pollRosterAlerts(app, detector, repo)
        GameSchedulerWorker.pollLineupAlert(app, detector, repo)
        maybeMorningBrief(app, repo)
        GameSchedulerWorker.scheduleRosterPoll(app)
        scheduleTodayFastPolls(app, repo)
        GameSchedulerWorker.enqueue(app)
        GameSchedulerWorker.scheduleKboDayRollover(app)
        enqueueImmediate(app)
        AlertWatchService.startIfNeeded(app)
    }

    fun runAsync(context: Context) {
        Thread {
            kotlinx.coroutines.runBlocking { run(context.applicationContext) }
        }.start()
    }

    /** 오전 8~11시, 당일 1회. 오늘/다음 경기가 BEFORE·LIVE면 「오늘의 경기」. */
    suspend fun maybeMorningBrief(context: Context, repo: GiantsRepository) {
        val hour = ZonedDateTime.now(KBO_ZONE).hour
        if (hour !in 8..11) return
        val today = kboToday().toString()
        if (repo.store.morningBriefDay() == today) return
        val snap = repo.store.loadSnapshot()
            ?: runCatching { repo.refreshSnapshot(force = false) }.getOrNull()
            ?: return
        val game = listOfNotNull(snap.lotteGame, snap.nextLotteGame)
            .firstOrNull {
                (it.status == GameStatus.BEFORE || it.status == GameStatus.LIVE) &&
                    (it.belongsToKboToday(today) || it.gameDate.take(10) == today)
            } ?: return
        val body = buildString {
            append("vs ${game.opponentName}")
            if (game.stadium.isNotBlank()) append(" · ${game.stadium}")
            if (game.startTime.isNotBlank()) append(" · ${game.startTime}")
            val sp = game.lotteStartingPitcher.ifBlank { "" }
            if (sp.isNotBlank()) append(" · 선발 $sp")
            val dh = dhSuffix(game.doubleHeaderNo).trim()
            if (dh.isNotBlank()) append(" · $dh")
        }
        val allow = shouldEmitAlert(
            typeEnabled = repo.store.isNotificationEnabled(NotificationType.PREGAME_REMINDER),
            liveOnly = false,
            gameIsLive = false,
            quietEnabled = repo.store.quietHoursEnabled(),
            quietStartHour = repo.store.quietStartHour(),
            quietEndHour = repo.store.quietEndHour(),
            now = LocalTime.now(KBO_ZONE),
            type = NotificationType.PREGAME_REMINDER,
        )
        if (allow) {
            NotificationHelper.notifyEvent(
                context,
                NotificationType.PREGAME_REMINDER,
                "오늘의 경기",
                body,
                2100,
                game.gameId,
            )
            repo.store.appendAlertHistory(
                AlertHistoryItem(
                    millis = System.currentTimeMillis(),
                    type = NotificationType.PREGAME_REMINDER.name,
                    title = "오늘의 경기",
                    text = body,
                ),
            )
        }
        // 알림이 꺼져 있어도 하루 키는 남겨 반복 시도만 막는다
        repo.store.setMorningBriefDay(today)
    }

    suspend fun scheduleTodayFastPolls(context: Context, repo: GiantsRepository) {
        val snap = repo.store.loadSnapshot()
            ?: runCatching { repo.refreshSnapshot(force = true) }.getOrNull()
            ?: return
        val today = kboToday().toString()
        snap.todayLotteGames
            .filter { !it.isCanceledGame() && it.status == GameStatus.BEFORE }
            .forEach { mini ->
                val date = mini.gameDate.ifBlank { today }
                GameSchedulerWorker.scheduleFastPoll(context, date, mini.startTime, mini.gameId)
            }
        snap.lotteGame
            ?.takeIf { it.status == GameStatus.BEFORE && !it.isCanceledGame() }
            ?.let { g ->
                GameSchedulerWorker.scheduleFastPoll(context, g.gameDate, g.startTime, g.gameId)
            }
    }

    private fun enqueueImmediate(context: Context) {
        val req = OneTimeWorkRequestBuilder<GameSchedulerWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${GameSchedulerWorker.WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
