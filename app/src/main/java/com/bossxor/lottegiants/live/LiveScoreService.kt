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
import com.bossxor.lottegiants.domain.LiveDisplayMode
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
 * 경기 중 5초(라이브)·20초(경기 전) 간격으로 폴링해 위젯·알림·이벤트를 갱신한다.
 */
class LiveScoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val detector by lazy { EventDetector(GiantsRepository.get(this).store) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createChannels(this)
        scope.launch {
            val store = GiantsRepository.get(this@LiveScoreService).store
            if (!store.isLiveScoreEnabled()) {
                stopSelf()
                return@launch
            }
            val mode = store.liveDisplayMode()
            val snap = store.loadSnapshot()
            val notification = NotificationHelper.buildLiveNotification(
                this@LiveScoreService,
                snap?.lotteGame,
                mode,
                snap?.winProbSeries.orEmpty(),
            )
            ServiceCompat.startForeground(
                this@LiveScoreService,
                NotificationHelper.LIVE_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            startPolling()
        }
        return START_STICKY
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            val repo = GiantsRepository.get(this@LiveScoreService)
            while (isActive) {
                if (!repo.store.isLiveScoreEnabled()) {
                    stopSelf()
                    break
                }
                val mode = repo.store.liveDisplayMode()
                val snap = runCatching { repo.refreshSnapshot(force = true) }.getOrNull()
                val game = snap?.lotteGame
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                val live = NotificationHelper.buildLiveNotification(
                    this@LiveScoreService,
                    game,
                    mode,
                    snap?.winProbSeries.orEmpty(),
                )
                nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, live)
                if (Build.VERSION.SDK_INT >= 36) {
                    val ok = runCatching { live.hasPromotableCharacteristics() }.getOrDefault(false)
                    if (!ok) android.util.Log.w(TAG, "live notification is not promotable")
                }
                WidgetUpdater.updateAll(this@LiveScoreService)
                WearBridge.syncSnapshot(this@LiveScoreService, snap)
                detector.process(this@LiveScoreService, game)
                if (game?.status == GameStatus.ENDED) {
                    val st = runCatching { repo.fetchStandings() }.getOrDefault(emptyList())
                    detector.processRace(this@LiveScoreService, st)
                }
                // 등말소는 매 폴링마다 — 다른 앱보다 빠르게
                val moves = runCatching { repo.fetchRecentRosterMoves(2) }.getOrDefault(emptyList())
                detector.processRosterMoves(this@LiveScoreService, moves)

                when (game?.status) {
                    GameStatus.LIVE -> delay(5_000L)
                    GameStatus.BEFORE -> delay(20_000L)
                    GameStatus.ENDED, GameStatus.CANCELED, null -> {
                        delay(3_000L)
                        // 서비스를 멈추면 알림도 같이 사라진다. 최종 스코어카드는 떼어 놓고
                        // 한 번 더 올려야 NO_CLEAR가 빠져 손으로 지울 수 있다. 만료는 setTimeoutAfter.
                        if (game != null) {
                            ServiceCompat.stopForeground(
                                this@LiveScoreService,
                                ServiceCompat.STOP_FOREGROUND_DETACH,
                            )
                            nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, live)
                        }
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
        private const val TAG = "LiveScoreService"

        fun start(context: Context) {
            val i = Intent(context, LiveScoreService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveScoreService::class.java))
        }

        /** 설정 변경 후 알림 즉시 반영 */
        fun restart(context: Context) {
            stop(context)
            start(context)
        }
    }
}
