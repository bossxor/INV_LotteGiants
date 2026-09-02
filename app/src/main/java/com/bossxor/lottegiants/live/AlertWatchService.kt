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

/**
 * 삼성 등 배터리 최적화에서 AlarmManager가 끊겨도 엔트리·라인업을 20초마다 본다.
 * WorkManager(최소 15분)만으로는 루타 대비 30분 늦어질 수 있어 포그라운드 감시를 둔다.
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
            while (isActive) {
                if (!inWatchHours() || !runBlocking { shouldRun() }) {
                    stopSelf()
                    break
                }
                val repo = GiantsRepository.get(this@AlertWatchService)
                val detector = EventDetector(repo.store)
                GameSchedulerWorker.pollRosterAlerts(this@AlertWatchService, detector, repo)
                GameSchedulerWorker.pollLineupAlert(this@AlertWatchService, detector, repo)
                delay(POLL_INTERVAL_MS)
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
        private const val POLL_INTERVAL_MS = 20_000L
        private const val WATCH_START_HOUR = 7
        private const val WATCH_END_HOUR = 24

        @Volatile
        private var running = false

        fun inWatchHours(): Boolean {
            val hour = ZonedDateTime.now(KBO_ZONE).hour
            return hour in WATCH_START_HOUR until WATCH_END_HOUR
        }

        suspend fun shouldRun(context: Context): Boolean {
            if (!inWatchHours()) return false
            val store = GiantsRepository.get(context).store
            return store.isNotificationEnabled(NotificationType.LINEUP) ||
                store.isNotificationEnabled(NotificationType.ROSTER) ||
                store.isNotificationEnabled(NotificationType.FAVORITE_ROSTER)
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
