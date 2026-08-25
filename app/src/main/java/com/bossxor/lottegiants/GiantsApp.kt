package com.bossxor.lottegiants

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.live.EventDetector
import com.bossxor.lottegiants.live.GameSchedulerWorker
import com.bossxor.lottegiants.live.LiveScoreService
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
        NotificationHelper.createChannels(this)
        GameSchedulerWorker.enqueue(this)
        scope.launch {
            runCatching {
                val repo = GiantsRepository.get(this@GiantsApp)
                val snap = repo.refreshSnapshot()
                WidgetUpdater.updateAll(this@GiantsApp)
                val detector = EventDetector(repo.store)
                detector.process(this@GiantsApp, snap.lotteGame)
                // 워커가 도는 15분을 기다리지 않고 앱을 열자마자 새 공시를 알린다
                runCatching {
                    detector.processRosterMoves(this@GiantsApp, repo.fetchRecentRosterMoves(3))
                }
                if (snap.lotteGame?.status == GameStatus.LIVE) {
                    LiveScoreService.start(this@GiantsApp)
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://www.koreabaseball.com/")
                        .build(),
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
