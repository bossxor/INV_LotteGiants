package com.bossxor.lottegiants.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.parseKboStartMillis

/**
 * 등말소(08–23시) 또는 라인업 창(경기 6시간 전~시작 후 30분)에만 켠다.
 * 라인업 15초 · 등말소 25초. 알람과 겹치면 [AlertPollGate]가 건너뛴다.
 */
class AlertWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!runBlocking { shouldRun() }) {
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationHelper.createChannels(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationHelper.buildAlertWatchNotification(this),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (pollJob?.isActive == true) return START_STICKY
        pollJob = scope.launch {
            var failStreak = 0
            while (isActive) {
                if (!runBlocking { shouldRun() }) {
                    stopSelf()
                    break
                }
                val repo = GiantsRepository.get(this@AlertWatchService)
                val detector = EventDetector(repo.store)
                val ok = runCatching {
                    GameSchedulerWorker.pollRosterAlerts(this@AlertWatchService, detector, repo)
                    GameSchedulerWorker.pollLineupAlert(this@AlertWatchService, detector, repo)
                }.isSuccess
                failStreak = if (ok) 0 else (failStreak + 1).coerceAtMost(4)
                delay(POLL_INTERVAL_MS + failStreak * 10_000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        running = false
        super.onDestroy()
    }

    private suspend fun shouldRun(): Boolean = Companion.shouldRun(this)

    private fun inWatchHours(): Boolean = Companion.inWatchHours()

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val POLL_INTERVAL_MS = 15_000L

        @Volatile
        private var running = false

        fun inWatchHours(): Boolean {
            val hour = ZonedDateTime.now(KBO_ZONE).hour
            return hour in AlertWatchGate.ROSTER_START_HOUR until AlertWatchGate.ROSTER_END_HOUR
        }

        suspend fun shouldRun(context: Context): Boolean {
            val store = GiantsRepository.get(context).store
            val lineupOn = store.isNotificationEnabled(NotificationType.LINEUP)
            val rosterOn = store.isNotificationEnabled(NotificationType.ROSTER) ||
                store.isNotificationEnabled(NotificationType.FAVORITE_ROSTER)
            if (!lineupOn && !rosterOn) return false
            val snap = store.loadSnapshot()
            val game = snap?.lotteGame ?: snap?.nextLotteGame
            val start = game?.let { parseKboStartMillis(it.gameDate, it.startTime) }
            return AlertWatchGate.shouldWatch(
                nowHour = ZonedDateTime.now(KBO_ZONE).hour,
                nowMillis = System.currentTimeMillis(),
                lineupEnabled = lineupOn,
                rosterEnabled = rosterOn,
                gameStartMillis = start,
            )
        }

        fun startIfNeeded(context: Context) {
            val app = context.applicationContext
            val go = runBlocking { shouldRun(app) }
            if (!go) {
                stop(app)
                return
            }
            if (running) return
            running = true
            app.startForegroundService(Intent(app, AlertWatchService::class.java))
        }

        fun stop(context: Context) {
            running = false
            context.applicationContext.stopService(Intent(context.applicationContext, AlertWatchService::class.java))
        }
    }
}
