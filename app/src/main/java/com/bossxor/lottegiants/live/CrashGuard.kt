package com.bossxor.lottegiants.live

import android.app.Application
import android.util.Log

/**
 * 미처리 예외로 프로세스가 죽어도 알람·워커를 다시 걸어, 수동으로 앱을 열기 전까지 감시가 멈추지 않게 한다.
 */
object CrashGuard {
    private const val TAG = "CrashGuard"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "uncaught in ${thread.name}", error)
            runCatching {
                GameSchedulerWorker.enqueue(app)
                GameSchedulerWorker.scheduleKboDayRollover(app)
                GameSchedulerWorker.scheduleRosterPoll(app)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
