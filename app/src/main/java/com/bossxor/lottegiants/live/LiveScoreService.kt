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
 * 경기 **중** 5초 간격으로 폴링해 위젯·알림·이벤트를 갱신한다.
 * 경기 전 알림은 FGS 없이 [NotificationHelper.refreshLiveNotificationIfNeeded]만 쓴다.
 */
class LiveScoreService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var ignoreLeadWindow = false
    private var foregroundStarted = false
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

        // LIVE가 아니면 FGS를 쓰지 않는다. startForegroundService 타임아웃·깜빡임 방지.
        if (game?.status != GameStatus.LIVE) {
            if (shouldShowLive(game, lead)) {
                runBlocking { NotificationHelper.refreshLiveNotificationIfNeeded(applicationContext) }
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!shouldShowLive(game, lead)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationHelper.buildLiveNotification(
            this,
            game,
            mode,
            snap?.winProbSeries.orEmpty(),
        )
        val notifyKey = NotificationHelper.liveNotificationKey(game, mode)
        if (foregroundStarted && pollJob?.isActive == true) {
            NotificationHelper.notifyLive(this, notification, notifyKey)
            return START_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.LIVE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        foregroundStarted = true
        NotificationHelper.notifyLive(this, notification, notifyKey, force = true)
        if (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED) {
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
                val live = NotificationHelper.buildLiveNotification(
                    this@LiveScoreService,
                    game,
                    mode,
                    snap?.winProbSeries.orEmpty(),
                )
                val notifyKey = NotificationHelper.liveNotificationKey(game, mode)
                if (game?.status != GameStatus.LIVE || !shouldShowLive(game, lead)) {
                    val pinned = repo.store.isLiveNotificationPinned()
                    if (pinned) {
                        NotificationHelper.notifyLive(this@LiveScoreService, live, notifyKey)
                        ServiceCompat.stopForeground(
                            this@LiveScoreService,
                            ServiceCompat.STOP_FOREGROUND_DETACH,
                        )
                        foregroundStarted = false
                    }
                    stopSelf()
                    break
                }
                NotificationHelper.notifyLive(this@LiveScoreService, live, notifyKey)
                WidgetUpdater.updateAll(this@LiveScoreService)
                WearBridge.syncSnapshot(this@LiveScoreService, snap)
                detector.process(this@LiveScoreService, game)
                if (game.status == GameStatus.ENDED) {
                    val st = runCatching { repo.fetchStandings() }.getOrDefault(emptyList())
                    detector.processRace(
                        this@LiveScoreService,
                        st,
                        com.bossxor.lottegiants.domain.raceRelevantGames(snap),
                    )
                }
                val moves = runCatching { repo.pollRosterMovesForAlert() }.getOrDefault(emptyList())
                    .ifEmpty {
                        runCatching { repo.fetchRecentRosterMoves(2) }.getOrDefault(emptyList())
                    }
                detector.processRosterMoves(this@LiveScoreService, moves)

                when (game.status) {
                    GameStatus.LIVE -> delay(5_000L)
                    GameStatus.ENDED, GameStatus.CANCELED -> {
                        delay(3_000L)
                        detachFinished(live)
                        break
                    }
                    else -> {
                        stopSelf()
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

    private fun shouldShowLive(game: LotteGameInfo?, lead: Int): Boolean {
        if (ignoreLeadWindow) return true
        val pinned = runBlocking {
            GiantsRepository.get(this@LiveScoreService).store.isLiveNotificationPinned()
        }
        if (pinned) return true
        return shouldPostLiveNotification(game, lead)
    }

    private fun detachFinished(notification: android.app.Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        foregroundStarted = false
        nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, notification)
        stopSelf()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        foregroundStarted = false
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FORCE_SHOW = "force_show"

        /** LIVE일 때만 FGS를 켠다. 경기 전은 알림만 갱신한다. */
        fun start(context: Context, forceShow: Boolean = false) {
            val app = context.applicationContext
            val repo = GiantsRepository.get(app)
            val snap = runBlocking { repo.store.loadSnapshot() }
            val lead = runBlocking { repo.store.liveLeadMinutes() }
            val pinned = runBlocking { repo.store.isLiveNotificationPinned() }
            val game = NotificationHelper.liveNotificationGame(
                snap,
                allowUpcoming = true,
                leadMinutes = lead,
                ignoreLeadWindow = forceShow || pinned,
            )
            if (game?.status != GameStatus.LIVE) {
                runBlocking { NotificationHelper.refreshLiveNotificationIfNeeded(app) }
                return
            }
            val i = Intent(app, LiveScoreService::class.java)
            if (forceShow) i.putExtra(EXTRA_FORCE_SHOW, true)
            app.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveScoreService::class.java))
        }

        fun restart(context: Context) {
            start(context)
        }

        /** `다시 표시`: lead 창 밖 경기 전에도 알림을 고정. LIVE가 아니면 FGS를 켜지 않는다. */
        suspend fun reshow(context: Context): Boolean {
            val app = context.applicationContext
            val repo = GiantsRepository.get(app)
            repo.store.setLiveScoreEnabled(true)
            repo.store.setLiveNotificationPinned(true)
            NotificationHelper.createChannels(app)
            val snap = runCatching { repo.refreshSnapshot(force = true) }.getOrNull()
                ?: repo.store.loadSnapshot()
            val lead = repo.store.liveLeadMinutes()
            val game = NotificationHelper.liveNotificationGame(
                snap,
                allowUpcoming = true,
                leadMinutes = lead,
                ignoreLeadWindow = true,
            ) ?: return false
            NotificationHelper.refreshLiveNotificationIfNeeded(app)
            return true
        }
    }
}
