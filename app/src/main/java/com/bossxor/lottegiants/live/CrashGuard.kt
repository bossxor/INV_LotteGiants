package com.bossxor.lottegiants.live

import android.app.Application
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 미처리 예외로 프로세스가 죽어도 알람·워커·감시를 다시 걸어,
 * 수동으로 앱을 열기 전까지 감시가 멈추지 않게 한다.
 *
 * 참고: 시스템 **강제종료(FORCE_STOP)** 는 알람·워커까지 취소하므로 앱을 다시 열어야 복구된다.
 */
object CrashGuard {
    private const val TAG = "CrashGuard"
    private const val PREFS = "crash_guard"
    private const val KEY_MSG = "last_crash_msg"
    private const val KEY_AT = "last_crash_at"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "uncaught in ${thread.name}", error)
            runCatching {
                recordCrash(app, error)
                GameSchedulerWorker.enqueue(app)
                GameSchedulerWorker.scheduleKboDayRollover(app)
                GameSchedulerWorker.scheduleRosterPoll(app)
                // 워커만으로는 LIVE FGS가 늦게 뜰 수 있어 즉시 재기동도 시도한다.
                AlertBootstrap.runAsync(app)
                LiveScoreService.start(app)
                AlertWatchService.startIfNeeded(app)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordCrash(context: Context, error: Throwable) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MSG, (error.javaClass.simpleName + ": " + (error.message ?: "")).take(400))
            .putLong(KEY_AT, System.currentTimeMillis())
            .commit()
    }

    /** 설정 진단용. 없으면 null. */
    fun lastCrashSummary(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L) return null
        val msg = prefs.getString(KEY_MSG, null)?.takeIf { it.isNotBlank() } ?: "알 수 없는 오류"
        val whenStr = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(at))
        return "$whenStr · $msg"
    }
}
