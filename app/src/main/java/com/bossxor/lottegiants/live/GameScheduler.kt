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
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.shouldEmitAlert
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class GameSchedulerWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = GiantsRepository.get(applicationContext)
        val snap = runCatching { repo.refreshSnapshot() }.getOrElse { return Result.retry() }
        WidgetUpdater.updateAll(applicationContext)
        WearBridge.syncSnapshot(applicationContext, snap)

        val game = snap.lotteGame
        // 라이브 폴링이 없어도 취소·시작 등 상태 전이 알림
        NotificationHelper.createChannels(applicationContext)
        val detector = EventDetector(repo.store)
        runCatching { detector.process(applicationContext, game) }
        runCatching {
            val moves = repo.fetchRecentRosterMoves(3)
            detector.processRosterMoves(applicationContext, moves)
        }
        runCatching {
            val st = repo.fetchStandings()
            detector.processRace(applicationContext, st)
        }

        val todayGames = snap.todayLotteGames
        val hasLive = todayGames.any { it.status == GameStatus.LIVE } || game?.status == GameStatus.LIVE
        if (hasLive) {
            LiveScoreService.start(applicationContext)
        }
        val befores = todayGames.filter { it.status == GameStatus.BEFORE }
        if (befores.isNotEmpty()) {
            befores.forEach { mini -> scheduleForMini(applicationContext, mini) }
        } else if (game?.status == GameStatus.BEFORE) {
            scheduleExactStart(applicationContext, game.gameDate, game.startTime, game.gameId)
            schedulePregameReminder(
                applicationContext, game.gameDate, game.startTime,
                game.opponentName, game.stadium, game.lotteStartingPitcher, game.gameId,
            )
        } else if (!hasLive) {
            when (game?.status) {
                GameStatus.ENDED, GameStatus.CANCELED, null -> {
                    LiveScoreService.stop(applicationContext)
                    snap.nextLotteGame?.let { next ->
                        scheduleExactStart(applicationContext, next.gameDate, next.startTime, next.gameId)
                    }
                }
                else -> {}
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

        fun scheduleExactStart(context: Context, date: String, time: String, gameId: String = "") {
            val trigger = parseGameMillis(date, time) ?: return
            // 시작 2분 전에 서비스 기동
            val at = (trigger - 2 * 60_000L).coerceAtLeast(System.currentTimeMillis() + 5_000L)
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_START_LIVE)
                .putExtra("gameId", gameId)
            val pi = PendingIntent.getBroadcast(
                context, requestCode(0x3001, gameId),
                intent,
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
            gameId: String = "",
        ) {
            val trigger = parseGameMillis(date, time) ?: return
            val at = trigger - 30 * 60_000L
            if (at <= System.currentTimeMillis()) return
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_PREGAME).apply {
                putExtra("opponent", opponent)
                putExtra("stadium", stadium)
                putExtra("pitcher", pitcher)
                putExtra("gameId", gameId)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode(0x3002, gameId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarmSafe(am, at, pi)
        }

        private fun scheduleForMini(context: Context, mini: MiniGame) {
            val lotteHome = mini.homeTeamCode.equals("LT", ignoreCase = true) ||
                mini.homeName.contains("롯데")
            val opponent = if (lotteHome) mini.awayName else mini.homeName
            val pitcher = if (lotteHome) mini.homeStarter else mini.awayStarter
            val date = mini.gameDate.ifBlank { com.bossxor.lottegiants.domain.kboToday().toString() }
            scheduleExactStart(context, date, mini.startTime, mini.gameId)
            schedulePregameReminder(
                context, date, mini.startTime, opponent, mini.stadium, pitcher, mini.gameId,
            )
        }

        private fun requestCode(base: Int, gameId: String): Int {
            if (gameId.isBlank()) return base
            return base shl 16 or (gameId.hashCode() and 0xFFFF)
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

        private fun parseGameMillis(date: String, time: String): Long? =
            com.bossxor.lottegiants.domain.parseKboStartMillis(date, time)

        const val ACTION_START_LIVE = "com.bossxor.lottegiants.START_LIVE"
        const val ACTION_PREGAME = "com.bossxor.lottegiants.PREGAME"
        const val ACTION_HIDE_LIVE = "com.bossxor.lottegiants.HIDE_LIVE"
    }
}

class GameAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            GameSchedulerWorker.ACTION_START_LIVE,
            Intent.ACTION_BOOT_COMPLETED -> {
                val gameId = intent?.getStringExtra("gameId").orEmpty()
                val goAsync = goAsync()
                Thread {
                    try {
                        runBlocking {
                            if (gameId.isNotBlank()) {
                                GiantsRepository.get(context).store.setPreferredLiveGameId(gameId)
                            }
                        }
                        LiveScoreService.start(context)
                        GameSchedulerWorker.enqueue(context)
                    } finally {
                        goAsync.finish()
                    }
                }.start()
            }
            GameSchedulerWorker.ACTION_PREGAME -> {
                val opponent = intent?.getStringExtra("opponent").orEmpty()
                val stadium = intent?.getStringExtra("stadium").orEmpty()
                val pitcher = intent?.getStringExtra("pitcher").orEmpty()
                val gameId = intent?.getStringExtra("gameId").orEmpty()
                val goAsync = goAsync()
                Thread {
                    try {
                        val store = GiantsRepository.get(context).store
                        runBlocking {
                            val allow = shouldEmitAlert(
                                typeEnabled = store.isNotificationEnabled(NotificationType.PREGAME_REMINDER),
                                liveOnly = store.alertsLiveOnly(),
                                gameIsLive = false,
                                quietEnabled = store.quietHoursEnabled(),
                                quietStartHour = store.quietStartHour(),
                                quietEndHour = store.quietEndHour(),
                                now = LocalTime.now(KBO_ZONE),
                                type = NotificationType.PREGAME_REMINDER,
                            )
                            if (allow) {
                                NotificationHelper.createChannels(context)
                                val nid = 2701 + (gameId.hashCode() and 0xFF)
                                NotificationHelper.notifyEvent(
                                    context,
                                    NotificationType.PREGAME_REMINDER,
                                    "30분 뒤 경기 시작",
                                    "vs $opponent · $stadium · 선발 ${pitcher.ifBlank { "미정" }}",
                                    nid,
                                    gameId,
                                )
                            }
                        }
                    } finally {
                        goAsync.finish()
                    }
                }.start()
            }
            GameSchedulerWorker.ACTION_HIDE_LIVE -> {
                LiveScoreService.stop(context)
            }
        }
    }
}
