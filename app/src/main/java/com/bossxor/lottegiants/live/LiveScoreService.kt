package com.bossxor.lottegiants.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 경기 중 10~15초 간격으로 폴링해 위젯·Now Bar·이벤트 알림을 갱신한다.
 */
class LiveScoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val detector by lazy { EventDetector(GiantsRepository.get(this).store) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createChannels(this)
        val notification = NotificationHelper.buildLiveNotification(this, null)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.LIVE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            val repo = GiantsRepository.get(this@LiveScoreService)
            while (isActive) {
                val snap = runCatching { repo.refreshSnapshot() }.getOrNull()
                val game = snap?.lotteGame
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(
                    NotificationHelper.LIVE_NOTIFICATION_ID,
                    NotificationHelper.buildLiveNotification(this@LiveScoreService, game)
                )
                WidgetUpdater.updateAll(this@LiveScoreService)
                detector.process(this@LiveScoreService, game)

                when (game?.status) {
                    GameStatus.LIVE -> delay(12_000L)
                    GameStatus.BEFORE -> {
                        // 시작 임박이면 계속, 아니면 서비스 종료
                        delay(60_000L)
                        if (game.status == GameStatus.BEFORE) {
                            // still before after refresh path handled next loop
                        }
                    }
                    GameStatus.ENDED, GameStatus.CANCELED, null -> {
                        delay(5_000L)
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, LiveScoreService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveScoreService::class.java))
        }
    }
}
