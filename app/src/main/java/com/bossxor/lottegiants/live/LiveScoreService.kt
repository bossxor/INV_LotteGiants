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
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.shouldPostLiveNotification
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 경기 중 5초(라이브)·8초(경기 전) 간격으로 폴링해 위젯·알림·이벤트를 갱신한다.
 */
class LiveScoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var ignoreLeadWindow = false
    private val detector by lazy { EventDetector(GiantsRepository.get(this).store) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FORCE_SHOW, false) == true) {
            ignoreLeadWindow = true
        }
        NotificationHelper.createChannels(this)
        val repo = GiantsRepository.get(this)
        val enabled = runBlocking { repo.store.isLiveScoreEnabled() }
        if (!enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        val snap = runBlocking { repo.store.loadSnapshot() }
        val mode = runBlocking { repo.store.liveDisplayMode() }
        val lead = runBlocking { repo.store.liveLeadMinutes() }
        val game = liveGame(snap, lead)
        val notification = NotificationHelper.buildLiveNotification(
            this,
            game,
            mode,
            snap?.winProbSeries.orEmpty(),
        )
        ServiceCompat.startForeground(
            this,
            NotificationHelper.LIVE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!shouldShowLive(game, lead)) {
            hideLiveAndStop(remove = true)
            return START_NOT_STICKY
        }
        if (game?.status == GameStatus.ENDED || game?.status == GameStatus.CANCELED) {
            detachFinished(notification)
            return START_NOT_STICKY
        }
        startPolling()
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
                val lead = repo.store.liveLeadMinutes()
                val snap = runCatching { repo.refreshSnapshot(force = false) }.getOrNull()
                val game = liveGame(snap, lead)
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!shouldShowLive(game, lead)) {
                    hideLiveAndStop(remove = true)
                    break
                }
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
                val alertGame = if (game?.status == GameStatus.BEFORE) {
                    runCatching { repo.refreshLineupAlert() }.getOrNull() ?: game
                } else {
                    game
                }
                detector.process(this@LiveScoreService, alertGame)
                if (game?.status == GameStatus.ENDED) {
                    val st = runCatching { repo.fetchStandings() }.getOrDefault(emptyList())
                    detector.processRace(
                        this@LiveScoreService,
                        st,
                        com.bossxor.lottegiants.domain.raceRelevantGames(snap),
                    )
                }
                // 등말소 — KBO 공식 GetRoster 우선 (Keubo보다 빠름)
                val moves = runCatching { repo.pollRosterMovesForAlert() }.getOrDefault(emptyList())
                    .ifEmpty {
                        runCatching { repo.fetchRecentRosterMoves(2) }.getOrDefault(emptyList())
                    }
                detector.processRosterMoves(this@LiveScoreService, moves)

                when (game?.status) {
                    GameStatus.LIVE -> delay(5_000L)
                    GameStatus.BEFORE -> {
                        if (shouldShowLive(game, lead)) {
                            delay(8_000L)
                        } else {
                            hideLiveAndStop(remove = true)
                            break
                        }
                    }
                    GameStatus.ENDED, GameStatus.CANCELED, null -> {
                        delay(3_000L)
                        detachFinished(live)
                        break
                    }
                }
            }
        }
    }

    private fun liveGame(snap: LiveSnapshot?, lead: Int) =
        NotificationHelper.liveNotificationGame(
            snap,
            allowUpcoming = true,
            leadMinutes = lead,
            ignoreLeadWindow = ignoreLeadWindow,
        )

    private fun shouldShowLive(game: LotteGameInfo?, lead: Int): Boolean =
        ignoreLeadWindow || shouldPostLiveNotification(game, lead)

    private fun detachFinished(notification: android.app.Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, notification)
        stopSelf()
    }

    private fun hideLiveAndStop(remove: Boolean) {
        ServiceCompat.stopForeground(
            this,
            if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH,
        )
        if (remove) NotificationHelper.cancelLive(this)
        stopSelf()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveScoreService"
        private const val EXTRA_FORCE_SHOW = "force_show"

        fun start(context: Context, forceShow: Boolean = false) {
            val i = Intent(context, LiveScoreService::class.java)
            if (forceShow) i.putExtra(EXTRA_FORCE_SHOW, true)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveScoreService::class.java))
        }

        /** 설정 변경 후 알림 즉시 반영. 이미 떠 있으면 같은 ID로 덮어써서 예전 디자인이 깜빡이지 않게 한다. */
        fun restart(context: Context) {
            start(context)
        }

        /** `다시 표시`: 스냅샷을 새로 받은 뒤 표시 시작 시간과 무관하게 알림을 올린다. */
        suspend fun reshow(context: Context): Boolean {
            val app = context.applicationContext
            val repo = GiantsRepository.get(app)
            repo.store.setLiveScoreEnabled(true)
            NotificationHelper.createChannels(app)
            val snap = runCatching { repo.refreshSnapshot(force = true) }.getOrNull()
                ?: repo.store.loadSnapshot()
            val mode = repo.store.liveDisplayMode()
            val lead = repo.store.liveLeadMinutes()
            val game = NotificationHelper.liveNotificationGame(
                snap,
                allowUpcoming = true,
                leadMinutes = lead,
                ignoreLeadWindow = true,
            ) ?: return false
            val n = NotificationHelper.buildLiveNotification(
                app,
                game,
                mode,
                snap?.winProbSeries.orEmpty(),
            )
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, n)
            start(app, forceShow = true)
            return true
        }
    }
}
