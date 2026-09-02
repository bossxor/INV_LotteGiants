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
import com.bossxor.lottegiants.domain.isCanceledGame
import com.bossxor.lottegiants.domain.shouldEmitAlert
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.ZonedDateTime
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
        runCatching { pollRosterAlerts(applicationContext, detector, repo) }
        runCatching {
            val st = repo.fetchStandings()
            detector.processRace(
                applicationContext,
                st,
                com.bossxor.lottegiants.domain.raceRelevantGames(snap),
            )
        }

        val todayGames = snap.todayLotteGames
        todayGames.filter { it.isCanceledGame() }.forEach { mini ->
            cancelGameAlarms(applicationContext, mini.gameId)
        }
        game?.takeIf { it.isCanceledGame() }?.let { g ->
            cancelGameAlarms(applicationContext, g.gameId)
        }

        val lead = repo.store.liveLeadMinutes()
        val hasLive = todayGames.any { it.status == GameStatus.LIVE } || game?.status == GameStatus.LIVE
        if (hasLive) {
            LiveScoreService.start(applicationContext)
        }
        val befores = todayGames.filter { it.status == GameStatus.BEFORE && !it.isCanceledGame() }
        if (befores.isNotEmpty()) {
            befores.forEach { mini -> scheduleForMini(applicationContext, mini, lead) }
            val withinLead = befores.any { mini ->
                val date = mini.gameDate.ifBlank { com.bossxor.lottegiants.domain.kboToday().toString() }
                val start = parseGameMillis(date, mini.startTime) ?: return@any false
                start - System.currentTimeMillis() in 0..(lead * 60_000L)
            }
            if (withinLead) LiveScoreService.start(applicationContext)
        } else if (game?.status == GameStatus.BEFORE && !game.isCanceledGame()) {
            scheduleExactStart(applicationContext, game.gameDate, game.startTime, game.gameId, lead)
            schedulePregameReminder(
                applicationContext, game.gameDate, game.startTime,
                game.opponentName, game.stadium, game.lotteStartingPitcher, game.gameId,
            )
            val start = parseGameMillis(game.gameDate, game.startTime)
            if (start != null && start - System.currentTimeMillis() in 0..(lead * 60_000L)) {
                LiveScoreService.start(applicationContext)
            }
        } else if (!hasLive) {
            when {
                game == null || game.status == GameStatus.ENDED || game.isCanceledGame() -> {
                    snap.nextLotteGame?.takeIf { !it.isCanceledGame() }?.let { next ->
                        scheduleExactStart(applicationContext, next.gameDate, next.startTime, next.gameId, lead)
                        schedulePregameReminder(
                            applicationContext, next.gameDate, next.startTime,
                            next.opponentName, next.stadium, next.lotteStartingPitcher, next.gameId,
                        )
                    }
                }
                else -> {}
            }
        }
        scheduleRosterPoll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "giants_scheduler"
        /** 라인업 fast poll 간격 (경기 6시간 전~시작 후 30분) */
        private const val FAST_POLL_INTERVAL_MS = 20_000L
        /** 엔트리 등말소 전용 poll 간격 (08:00~23:00) */
        private const val ROSTER_POLL_INTERVAL_MS = 30_000L
        private const val LINEUP_POLL_WINDOW_MS = 6 * 60 * 60_000L
        private const val ROSTER_POLL_START_HOUR = 8
        private const val ROSTER_POLL_END_HOUR = 23

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<GameSchedulerWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun scheduleExactStart(
            context: Context,
            date: String,
            time: String,
            gameId: String = "",
            leadMinutes: Int = com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_DEFAULT,
        ) {
            val trigger = parseGameMillis(date, time) ?: return
            val lead = com.bossxor.lottegiants.domain.clampLiveLeadMinutes(leadMinutes)
            val at = (trigger - lead * 60_000L).coerceAtLeast(System.currentTimeMillis() + 5_000L)
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_START_LIVE)
                .putExtra("gameId", gameId)
            val pi = PendingIntent.getBroadcast(
                context, requestCode(0x3001, gameId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarmSafe(am, at, pi)
            scheduleFastPoll(context, date, time, gameId)
        }

        /** 경기 당일 6시간 전부터 20초마다 라인업·스냅샷 검사 */
        fun scheduleFastPoll(context: Context, date: String, time: String, gameId: String = "") {
            val start = parseGameMillis(date, time) ?: return
            val windowStart = start - LINEUP_POLL_WINDOW_MS
            val windowEnd = start + 30 * 60_000L
            val now = System.currentTimeMillis()
            if (now > windowEnd) return
            val nextAt = when {
                now < windowStart -> windowStart
                else -> now + FAST_POLL_INTERVAL_MS
            }
            if (nextAt <= now) return
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java).setAction(ACTION_FAST_POLL)
                .putExtra("gameId", gameId)
                .putExtra("gameDate", date)
                .putExtra("startTime", time)
            val pi = PendingIntent.getBroadcast(
                context, requestCode(0x3003, gameId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setAlarmSafe(am, nextAt, pi)
        }

        /** 08:00~23:00 KST — KBO 공식 GetRoster로 엔트리 등말소를 45초마다 검사 */
        fun scheduleRosterPoll(context: Context) {
            val zone = KBO_ZONE
            val now = ZonedDateTime.now(zone)
            val today = now.toLocalDate()
            val pollStart = today.atTime(ROSTER_POLL_START_HOUR, 0).atZone(zone)
            val pollEnd = today.atTime(ROSTER_POLL_END_HOUR, 0).atZone(zone)
            val nextAt = when {
                now.isAfter(pollEnd) ->
                    today.plusDays(1).atTime(ROSTER_POLL_START_HOUR, 0).atZone(zone)
                        .toInstant().toEpochMilli()
                now.isBefore(pollStart) -> pollStart.toInstant().toEpochMilli()
                else -> System.currentTimeMillis() + ROSTER_POLL_INTERVAL_MS
            }
            val am = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, GameAlarmReceiver::class.java)
                .setAction(ACTION_ROSTER_POLL)
            val pi = PendingIntent.getBroadcast(
                context, 0x3004,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setAlarmSafe(am, nextAt, pi)
        }

        suspend fun pollLineupAlert(context: Context, detector: EventDetector, repo: GiantsRepository) {
            runCatching {
                detector.process(context, repo.refreshLineupAlert())
            }
        }

        suspend fun pollRosterAlerts(context: Context, detector: EventDetector, repo: GiantsRepository) {
            runCatching {
                val kboMoves = repo.pollRosterMovesForAlert()
                if (kboMoves.isNotEmpty()) {
                    detector.processRosterMoves(context, kboMoves)
                } else {
                    detector.processRosterMoves(context, repo.fetchRecentRosterMoves(2))
                }
            }
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

        private fun scheduleForMini(
            context: Context,
            mini: MiniGame,
            leadMinutes: Int = com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_DEFAULT,
        ) {
            if (mini.isCanceledGame()) {
                cancelGameAlarms(context, mini.gameId)
                return
            }
            val lotteHome = mini.homeTeamCode.equals("LT", ignoreCase = true) ||
                mini.homeName.contains("롯데")
            val opponent = if (lotteHome) mini.awayName else mini.homeName
            val pitcher = if (lotteHome) mini.homeStarter else mini.awayStarter
            val date = mini.gameDate.ifBlank { com.bossxor.lottegiants.domain.kboToday().toString() }
            scheduleExactStart(context, date, mini.startTime, mini.gameId, leadMinutes)
            schedulePregameReminder(
                context, date, mini.startTime, opponent, mini.stadium, pitcher, mini.gameId,
            )
        }

        fun cancelGameAlarms(context: Context, gameId: String) {
            if (gameId.isBlank()) return
            val am = context.getSystemService(AlarmManager::class.java)
            fun cancel(action: String, base: Int) {
                val intent = Intent(context, GameAlarmReceiver::class.java).setAction(action)
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode(base, gameId),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pi != null) am.cancel(pi)
            }
            cancel(ACTION_START_LIVE, 0x3001)
            cancel(ACTION_PREGAME, 0x3002)
            cancel(ACTION_FAST_POLL, 0x3003)
        }

        /** 예약 알람(30분 전·시작)을 실행해도 되는지 — 취소된 경기면 false */
        suspend fun isGameStillScheduled(context: Context, gameId: String): Boolean {
            if (gameId.isBlank()) return true
            val snap = runCatching { GiantsRepository.get(context).refreshSnapshot(force = true) }.getOrNull()
                ?: return true
            snap.todayLotteGames.firstOrNull { it.gameId == gameId }?.let { mini ->
                if (mini.isCanceledGame()) {
                    cancelGameAlarms(context, gameId)
                    return false
                }
            }
            snap.lotteGame?.takeIf { it.gameId == gameId }?.let { g ->
                if (g.isCanceledGame()) {
                    cancelGameAlarms(context, gameId)
                    return false
                }
            }
            return true
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
        const val ACTION_FAST_POLL = "com.bossxor.lottegiants.FAST_POLL"
        const val ACTION_ROSTER_POLL = "com.bossxor.lottegiants.ROSTER_POLL"
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
                            if (gameId.isNotBlank() &&
                                !GameSchedulerWorker.isGameStillScheduled(context, gameId)
                            ) {
                                return@runBlocking
                            }
                            if (gameId.isNotBlank()) {
                                GiantsRepository.get(context).store.setPreferredLiveGameId(gameId)
                            }
                            LiveScoreService.start(context)
                            GameSchedulerWorker.enqueue(context)
                            GameSchedulerWorker.scheduleRosterPoll(context)
                        }
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
                            if (gameId.isNotBlank() &&
                                !GameSchedulerWorker.isGameStillScheduled(context, gameId)
                            ) {
                                return@runBlocking
                            }
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
                NotificationHelper.cancelLive(context)
            }
            GameSchedulerWorker.ACTION_FAST_POLL -> {
                val gameId = intent?.getStringExtra("gameId").orEmpty()
                val date = intent?.getStringExtra("gameDate").orEmpty()
                val time = intent?.getStringExtra("startTime").orEmpty()
                val goAsync = goAsync()
                Thread {
                    try {
                        runBlocking {
                            if (gameId.isNotBlank() &&
                                !GameSchedulerWorker.isGameStillScheduled(context, gameId)
                            ) {
                                return@runBlocking
                            }
                            val repo = GiantsRepository.get(context)
                            NotificationHelper.createChannels(context)
                            val detector = EventDetector(repo.store)
                            GameSchedulerWorker.pollLineupAlert(context, detector, repo)
                            GameSchedulerWorker.pollRosterAlerts(context, detector, repo)
                            WidgetUpdater.updateAll(context)
                            val game = repo.refreshLineupAlert()
                            val status = game?.status
                            if (status == GameStatus.LIVE || status == GameStatus.BEFORE) {
                                LiveScoreService.start(context)
                            }
                            if (date.isNotBlank() && time.isNotBlank() &&
                                status != GameStatus.ENDED && status != GameStatus.CANCELED
                            ) {
                                GameSchedulerWorker.scheduleFastPoll(context, date, time, gameId)
                            }
                        }
                    } finally {
                        goAsync.finish()
                    }
                }.start()
            }
            GameSchedulerWorker.ACTION_ROSTER_POLL -> {
                val goAsync = goAsync()
                Thread {
                    try {
                        runBlocking {
                            val repo = GiantsRepository.get(context)
                            NotificationHelper.createChannels(context)
                            val detector = EventDetector(repo.store)
                            GameSchedulerWorker.pollRosterAlerts(context, detector, repo)
                            GameSchedulerWorker.pollLineupAlert(context, detector, repo)
                            GameSchedulerWorker.scheduleRosterPoll(context)
                        }
                    } finally {
                        goAsync.finish()
                    }
                }.start()
            }
        }
    }
}
