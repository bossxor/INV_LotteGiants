package com.bossxor.lottegiants

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.UpdateChecker
import com.bossxor.lottegiants.domain.IMAGE_USER_AGENT
import com.bossxor.lottegiants.domain.imageRefererForHost
import com.bossxor.lottegiants.domain.shouldPostLiveNotification
import com.bossxor.lottegiants.live.AlertBootstrap
import com.bossxor.lottegiants.live.CrashGuard
import com.bossxor.lottegiants.live.EventDetector
import com.bossxor.lottegiants.live.GameSchedulerWorker
import com.bossxor.lottegiants.live.NotificationHelper
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
        CrashGuard.install(this)
        NotificationHelper.createChannels(this)
        // 스냅샷/엔트리 조회가 실패해도 알람은 먼저 걸어 둔다
        runCatching { GameSchedulerWorker.enqueue(this) }
        runCatching { GameSchedulerWorker.scheduleKboDayRollover(this) }
        AlertBootstrap.runAsync(this)
        UpdateChecker.prefetch(this)
        scope.launch {
            val repo = GiantsRepository.get(this@GiantsApp)
            runCatching { repo.store.migrateToScorecardModeIfNeeded() }
            val snap = runCatching { repo.refreshSnapshot(force = false) }.getOrNull()
            runCatching { WidgetUpdater.updateAll(this@GiantsApp) }
            val detector = EventDetector(repo.store)
            runCatching { detector.process(this@GiantsApp, snap?.lotteGame) }
            runCatching {
                detector.processRosterMoves(this@GiantsApp, repo.pollRosterMovesForAlert())
            }
            runCatching {
                if (repo.store.isLiveScoreEnabled()) {
                    val lead = repo.store.liveLeadMinutes()
                    val game = NotificationHelper.liveNotificationGame(
                        snap,
                        allowUpcoming = false,
                        leadMinutes = lead,
                    )
                    if (shouldPostLiveNotification(game, lead)) {
                        NotificationHelper.refreshLiveNotificationIfNeeded(this@GiantsApp)
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
            .memoryCache {
                MemoryCache.Builder(this@GiantsApp).maxSizePercent(0.12).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(40L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
