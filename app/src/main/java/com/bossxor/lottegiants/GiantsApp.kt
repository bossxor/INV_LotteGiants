package com.bossxor.lottegiants

import android.app.Application
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.IMAGE_USER_AGENT
import com.bossxor.lottegiants.domain.imageRefererForHost
import com.bossxor.lottegiants.domain.kboNow
import com.bossxor.lottegiants.live.EventDetector
import com.bossxor.lottegiants.live.GameSchedulerWorker
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.live.NotificationHelper
import com.bossxor.lottegiants.live.WearBridge
import com.bossxor.lottegiants.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GiantsApp : Application(), ImageLoaderFactory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        GameSchedulerWorker.enqueue(this)
        scope.launch {
            runCatching {
                val repo = GiantsRepository.get(this@GiantsApp)
                repo.store.migrateToScorecardModeIfNeeded()
                val snap = repo.refreshSnapshot(force = true)
                WidgetUpdater.updateAll(this@GiantsApp)
                WearBridge.syncSnapshot(this@GiantsApp, snap)
                val detector = EventDetector(repo.store)
                detector.process(this@GiantsApp, snap.lotteGame)
                // 워커가 도는 15분을 기다리지 않고 앱을 열자마자 새 공시를 알린다
                runCatching {
                    detector.processRosterMoves(this@GiantsApp, repo.fetchRecentRosterMoves(3))
                }
                if (repo.store.isLiveScoreEnabled()) {
                    val game = snap.lotteGame
                    // 어제 끝난 경기를 다시 띄우면 다음날 새벽까지 스코어카드가 남는다. 오늘 경기만.
                    val today = kboNow().toLocalDate().toString()
                    val showCard = game != null && when (game.status) {
                        GameStatus.LIVE -> true
                        GameStatus.BEFORE, GameStatus.ENDED -> game.gameDate == today
                        GameStatus.CANCELED -> false
                    }
                    if (game != null && showCard) {
                        val n = NotificationHelper.buildLiveNotification(
                            this@GiantsApp,
                            game,
                            repo.store.liveDisplayMode(),
                            snap.winProbSeries,
                        )
                        getSystemService(NotificationManager::class.java)
                            .notify(NotificationHelper.LIVE_NOTIFICATION_ID, n)
                    }
                    if (game?.status == GameStatus.LIVE) {
                        LiveScoreService.start(this@GiantsApp)
                    }
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder().header("User-Agent", IMAGE_USER_AGENT)
                imageRefererForHost(req.url.host)?.let { builder.header("Referer", it) }
                chain.proceed(builder.build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
