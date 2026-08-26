package com.bossxor.lottegiants.wear

import android.app.Application

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SnapshotRepository.hydrate(this)
    }
}
