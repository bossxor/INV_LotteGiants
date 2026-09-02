package com.bossxor.lottegiants.live

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.isCanceledGame
import com.bossxor.lottegiants.domain.kboToday

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
        GameSchedulerWorker.scheduleRosterPoll(app)
        scheduleTodayFastPolls(app, repo)
        GameSchedulerWorker.enqueue(app)
        enqueueImmediate(app)
        AlertWatchService.startIfNeeded(app)
    }

    fun runAsync(context: Context) {
        Thread {
            kotlinx.coroutines.runBlocking { run(context.applicationContext) }
        }.start()
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
