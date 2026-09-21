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
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

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
        // 스냅샷·RemoteViews보다 먼저 FGS를 올려 타임아웃 강제종료를 막는다
        if (!foregroundStarted) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.LIVE_NOTIFICATION_ID,
                NotificationHelper.buildLiveBootstrapNotification(this),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            foregroundStarted = true
        }
        // DataStore await는 메인 스레드 runBlocking 금지 — IO에서 검사 후 LIVE가 아니면 stopSelf
        scope.launch {
            val repo = GiantsRepository.get(this@LiveScoreService)
            val enabled = repo.store.isLiveScoreEnabled()
            if (!enabled) {
                stopSelf()
                return@launch
            }
            val snap = repo.store.loadSnapshot()
            val mode = repo.store.liveDisplayMode()
            val lead = repo.store.liveLeadMinutes()
            val game = liveGame(snap, lead)

            // LIVE가 아니면 FGS를 쓰지 않는다. startForegroundService 타임아웃·깜빡임 방지.
            if (game?.status != GameStatus.LIVE) {
                if (shouldShowLive(game, lead)) {
                    NotificationHelper.refreshLiveNotificationIfNeeded(applicationContext)
                }
                stopSelf()
                return@launch
            }
            if (!shouldShowLive(game, lead)) {
                stopSelf()
                return@launch
            }

            val notification = NotificationHelper.buildLiveNotification(
                this@LiveScoreService,
                game,
                mode,
                snap?.winProbSeries.orEmpty(),
            )
            val notifyKey = NotificationHelper.liveNotificationKey(game, mode)
            if (pollJob?.isActive == true) {
                NotificationHelper.notifyLive(this@LiveScoreService, notification, notifyKey)
                return@launch
            }
            NotificationHelper.notifyLive(this@LiveScoreService, notification, notifyKey, force = true)
            if (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED) {
                detachFinished(notification, game)
                return@launch
            }
            startPolling()
        }
        return START_STICKY
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            val repo = GiantsRepository.get(this@LiveScoreService)
            while (isActive) {
                try {
                    if (!repo.store.isLiveScoreEnabled()) {
                        stopSelf()
                        break
                    }
                    val mode = repo.store.liveDisplayMode()
                    val lead = repo.store.liveLeadMinutes()
                    val snap = runCatching { repo.refreshSnapshot(force = false) }.getOrNull()
                        ?: repo.store.loadSnapshot()
                    if (snap == null) {
                        delay(8_000L)
                        continue
                    }
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
                    if (game.status == GameStatus.LIVE) {
                        repo.store.clearDismissedFinishedLiveGameId()
                    }
                    NotificationHelper.notifyLive(this@LiveScoreService, live, notifyKey)
                    WidgetUpdater.updateAll(this@LiveScoreService)
                    runCatching { detector.process(this@LiveScoreService, game) }
                    if (game.status == GameStatus.ENDED) {
                        val st = runCatching { repo.fetchStandings() }.getOrDefault(emptyList())
                        runCatching {
                            detector.processRace(
                                this@LiveScoreService,
                                st,
                                com.bossxor.lottegiants.domain.raceRelevantGames(snap),
                            )
                        }
                    }

                    when (game.status) {
                        GameStatus.LIVE -> delay(if (game.isSuspended) 20_000L else 5_000L)
                        GameStatus.ENDED, GameStatus.CANCELED -> {
                            delay(3_000L)
                            detachFinished(live, game)
                            break
                        }
                        else -> {
                            stopSelf()
                            break
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "live poll failed", t)
                    delay(8_000L)
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

    private suspend fun shouldShowLive(game: LotteGameInfo?, lead: Int): Boolean {
        if (ignoreLeadWindow) return true
        val pinned = GiantsRepository.get(this).store.isLiveNotificationPinned()
        if (pinned) return true
        return shouldPostLiveNotification(game, lead)
    }

    private suspend fun detachFinished(
        notification: android.app.Notification,
        game: LotteGameInfo?,
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        // 종료 직후 사용자가 먼저 지웠으면 3초 뒤 다시 올리지 않는다.
        if (game != null) {
            val dismissed = GiantsRepository.get(this).store.dismissedFinishedLiveGameId()
            if (dismissed.isNotBlank() &&
                dismissed == NotificationHelper.finishedLiveKey(game)
            ) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                NotificationHelper.cancelLive(this)
                stopSelf()
                return
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        foregroundStarted = false
        nm.notify(NotificationHelper.LIVE_NOTIFICATION_ID, notification)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 최근 앱 스와이프 후에도 알람·워커는 남겨 둔다 (OEM이 FGS를 같이 죽일 수 있음).
        runCatching {
            GameSchedulerWorker.enqueue(applicationContext)
            GameSchedulerWorker.scheduleKboDayRollover(applicationContext)
            GameSchedulerWorker.scheduleRosterPoll(applicationContext)
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        foregroundStarted = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveScoreService"
        private const val EXTRA_FORCE_SHOW = "force_show"

        /** LIVE일 때만 FGS를 켠다. 경기 전은 알림만 갱신한다. 호출 스레드는 막지 않는다. */
        fun start(context: Context, forceShow: Boolean = false) {
            val app = context.applicationContext
            val i = Intent(app, LiveScoreService::class.java)
            if (forceShow) i.putExtra(EXTRA_FORCE_SHOW, true)
            CoroutineScope(Dispatchers.IO).launch {
                val isLive = runCatching {
                    val repo = GiantsRepository.get(app)
                    val snap = repo.store.loadSnapshot()
                    val lead = repo.store.liveLeadMinutes()
                    val pinned = repo.store.isLiveNotificationPinned()
                    val game = NotificationHelper.liveNotificationGame(
                        snap,
                        allowUpcoming = true,
                        leadMinutes = lead,
                        ignoreLeadWindow = forceShow || pinned,
                    )
                    game?.status == GameStatus.LIVE
                }.getOrElse {
                    // 스냅샷 판별 실패 시 FGS를 억지로 켜면 타임아웃·깜빡임만 난다. 알림만 갱신.
                    Log.w(TAG, "live check failed; skip FGS", it)
                    false
                }
                if (!isLive) {
                    runCatching { NotificationHelper.refreshLiveNotificationIfNeeded(app) }
                    return@launch
                }
                runCatching { app.startForegroundService(i) }
            }
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
            repo.store.clearDismissedFinishedLiveGameId()
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
