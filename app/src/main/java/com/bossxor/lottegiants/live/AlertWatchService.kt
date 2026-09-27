package com.bossxor.lottegiants.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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
import java.time.ZonedDateTime
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.parseKboStartMillis
import kotlin.coroutines.coroutineContext

/**
 * 등말소(14–23시) 또는 라인업 창(경기 6시간 전~시작 후 30분)에만 켠다.
 * 라인업 창 15초 · 그 외 45초. 알람과 겹치면 [AlertPollGate]가 건너뛴다.
 *
 * Android 15+ dataSync FGS 일일 한도를 피하려고 [specialUse] 타입을 쓴다.
 */
class AlertWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createChannels(this)
        if (!promoteToForeground()) {
            // FGS 불가 시에도 알람·워커로 감시 유지 (프로세스 강제종료만 피함)
            fallbackToAlarms()
            stopSelfSafely()
            return START_NOT_STICKY
        }
        scope.launch {
            if (!shouldRun()) {
                stopSelfSafely()
                return@launch
            }
            if (pollJob?.isActive == true) return@launch
            pollJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private fun promoteToForeground(): Boolean {
        if (foregroundStarted) return true
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                NotificationHelper.buildAlertWatchNotification(this),
                foregroundType(),
            )
            foregroundStarted = true
            true
        }.getOrElse { t ->
            Log.e(TAG, "startForeground failed; alarm-only fallback", t)
            CrashGuard.recordCrash(this, t)
            false
        }
    }

    private suspend fun pollLoop() {
        var failStreak = 0
        while (coroutineContext.isActive) {
            try {
                if (!shouldRun()) {
                    stopSelfSafely()
                    break
                }
                val repo = GiantsRepository.get(this@AlertWatchService)
                val detector = EventDetector(repo.store)
                val ok = runCatching {
                    GameSchedulerWorker.pollRosterAlerts(this@AlertWatchService, detector, repo)
                    GameSchedulerWorker.pollLineupAlert(this@AlertWatchService, detector, repo)
                }.isSuccess
                failStreak = if (ok) 0 else (failStreak + 1).coerceAtMost(4)
                val snap = runCatching { repo.store.loadSnapshot() }.getOrNull()
                val game = snap?.lotteGame ?: snap?.nextLotteGame
                val start = game?.let { parseKboStartMillis(it.gameDate, it.startTime) }
                delay(AlertWatchGate.pollIntervalMs(System.currentTimeMillis(), start) + failStreak * 10_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "alert watch poll failed", t)
                failStreak = (failStreak + 1).coerceAtMost(4)
                delay(AlertWatchGate.ROSTER_POLL_MS + failStreak * 10_000L)
            }
        }
    }

    private fun stopSelfSafely() {
        if (foregroundStarted) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            foregroundStarted = false
        }
        running = false
        stopSelf()
    }

    private fun fallbackToAlarms() {
        runCatching {
            GameSchedulerWorker.enqueue(applicationContext)
            GameSchedulerWorker.scheduleKboDayRollover(applicationContext)
            GameSchedulerWorker.scheduleRosterPoll(applicationContext)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        fallbackToAlarms()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        if (foregroundStarted) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            foregroundStarted = false
        }
        running = false
        super.onDestroy()
    }

    private suspend fun shouldRun(): Boolean = Companion.shouldRun(this)

    companion object {
        private const val TAG = "AlertWatchService"
        private const val NOTIFICATION_ID = 9001

        @Volatile
        private var running = false

        /** API 34+ specialUse, 그 아래는 dataSync(한도 완화 전 OEM). */
        fun foregroundType(): Int = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }

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
            CoroutineScope(Dispatchers.IO).launch {
                val go = runCatching { shouldRun(app) }.getOrDefault(false)
                if (!go) {
                    stop(app)
                    return@launch
                }
                if (running) return@launch
                running = true
                runCatching {
                    app.startForegroundService(Intent(app, AlertWatchService::class.java))
                }.onFailure { t ->
                    running = false
                    Log.e(TAG, "startForegroundService failed", t)
                    CrashGuard.recordCrash(app, t)
                    runCatching {
                        GameSchedulerWorker.enqueue(app)
                        GameSchedulerWorker.scheduleRosterPoll(app)
                    }
                }
            }
        }

        fun stop(context: Context) {
            running = false
            context.applicationContext.stopService(Intent(context.applicationContext, AlertWatchService::class.java))
        }
    }
}
