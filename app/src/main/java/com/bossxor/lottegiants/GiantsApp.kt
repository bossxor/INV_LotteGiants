package com.bossxor.lottegiants

import android.app.Application
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

class GiantsApp : Application() {
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
                EventDetector(repo.store).process(this@GiantsApp, snap.lotteGame)
                if (snap.lotteGame?.status == GameStatus.LIVE) {
                    LiveScoreService.start(this@GiantsApp)
                }
            }
        }
    }
}
