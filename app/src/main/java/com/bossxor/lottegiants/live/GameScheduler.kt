package com.bossxor.lottegiants.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class GameSchedulerWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = GiantsRepository.get(applicationContext)
        val snap = runCatching { repo.refreshSnapshot() }.getOrElse { return Result.retry() }
        WidgetUpdater.updateAll(applicationContext)

        val game = snap.lotteGame
        // 라이브 폴링이 없어도 취소·시작 등 상태 전이 알림
        NotificationHelper.createChannels(applicationContext)
        runCatching {
            EventDetector(repo.store).process(applicationContext, game)
        }

        when (game?.status) {
            GameStatus.LIVE -> LiveScoreService.start(applicationContext)
            GameStatus.BEFORE -> {
                scheduleExactStart(applicationContext, game.gameDate, game.startTime)
                schedulePregameReminder(applicationContext, game.gameDate, game.startTime, game.opponentName, game.stadium, game.lotteStartingPitcher)
            }
            GameStatus.ENDED, GameStatus.CANCELED -> LiveScoreService.stop(applicationContext)
            null -> {
                // 다음 경기 있으면 그날 알람
                snap.nextLotteGame?.let { next ->
                    scheduleExactStart(applicationContext, next.gameDate, next.startTime)
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "giants_scheduler"

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<GameSchedulerWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun scheduleExactStart(context: Context, date: String, time: String) {
            val trigger = parseGameMillis(date, time) ?: return
            // 시작 2분 전에 서비스 기동
            val at = (trigger - 2 * 60_000L).coerceAtLeast(System.currentTimeMillis() + 5_000L)
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 3001,
                Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_START_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarmSafe(am, at, pi)
        }

        fun schedulePregameReminder(
            context: Context,
            date: String,
            time: String,
            opponent: String,
            stadium: String,
            pitcher: String,
        ) {
            val trigger = parseGameMillis(date, time) ?: return
            val at = trigger - 30 * 60_000L
            if (at <= System.currentTimeMillis()) return
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_PREGAME).apply {
                putExtra("opponent", opponent)
                putExtra("stadium", stadium)
                putExtra("pitcher", pitcher)
            }
            val pi = PendingIntent.getBroadcast(
                context, 3002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarmSafe(am, at, pi)
        }

        private fun setAlarmSafe(am: AlarmManager, at: Long, pi: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        private fun parseGameMillis(date: String, time: String): Long? = runCatching {
            val dt = LocalDateTime.parse("$date $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            dt.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        }.getOrNull()

        const val ACTION_START_LIVE = "com.bossxor.lottegiants.START_LIVE"
        const val ACTION_PREGAME = "com.bossxor.lottegiants.PREGAME"
    }
}

class GameAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            GameSchedulerWorker.ACTION_START_LIVE,
            Intent.ACTION_BOOT_COMPLETED -> {
                LiveScoreService.start(context)
                GameSchedulerWorker.enqueue(context)
            }
            GameSchedulerWorker.ACTION_PREGAME -> {
                val opponent = intent.getStringExtra("opponent").orEmpty()
                val stadium = intent.getStringExtra("stadium").orEmpty()
                val pitcher = intent.getStringExtra("pitcher").orEmpty()
                val goAsync = goAsync()
                Thread {
                    try {
                        val store = GiantsRepository.get(context).store
                        runBlocking {
                            if (store.isNotificationEnabled(NotificationType.PREGAME_REMINDER)) {
                                NotificationHelper.createChannels(context)
                                NotificationHelper.notifyEvent(
                                    context,
                                    NotificationType.PREGAME_REMINDER,
                                    "30분 뒤 경기 시작",
                                    "vs $opponent · $stadium · 선발 ${pitcher.ifBlank { "미정" }}",
                                    2701
                                )
                            }
                        }
                    } finally {
                        goAsync.finish()
                    }
                }.start()
            }
        }
    }
}
