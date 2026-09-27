package com.bossxor.lottegiants.live

import android.app.Application
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 미처리 예외로 프로세스가 죽어도 알람·워커를 다시 걸어,
 * 수동으로 앱을 열기 전까지 감시가 멈추지 않게 한다.
 *
 * FGS([LiveScoreService]/[AlertWatchService])는 크래시 핸들러에서 절대 재기동하지 않는다.
 * 죽어가는 프로세스에서 startForegroundService 하면 dataSync 한도·재크래시 루프가 난다.
 *
 * 참고: 시스템 **강제종료(FORCE_STOP)** 는 알람·워커까지 취소하므로 앱을 다시 열어야 복구된다.
 */
object CrashGuard {
    private const val TAG = "CrashGuard"
    private const val PREFS = "crash_guard"
    private const val KEY_MSG = "last_crash_msg"
    private const val KEY_STACK = "last_crash_stack"
    private const val KEY_AT = "last_crash_at"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "uncaught in ${thread.name}", error)
            runCatching {
                recordCrash(app, error)
                // 알람·워커만. FGS 즉시 재기동은 금지(한도 소진·재크래시 루프).
                GameSchedulerWorker.enqueue(app)
                GameSchedulerWorker.scheduleKboDayRollover(app)
                GameSchedulerWorker.scheduleRosterPoll(app)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordCrash(context: Context, error: Throwable) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val msg = (error.javaClass.simpleName + ": " + (error.message ?: "")).take(400)
        val stack = error.stackTrace
            .take(2)
            .joinToString(" ← ") {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
            .take(240)
        prefs.edit()
            .putString(KEY_MSG, msg)
            .putString(KEY_STACK, stack)
            .putLong(KEY_AT, System.currentTimeMillis())
            .commit()
    }

    /** 설정 진단용. 없으면 null. */
    fun lastCrashSummary(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L) return null
        val msg = prefs.getString(KEY_MSG, null)?.takeIf { it.isNotBlank() } ?: "알 수 없는 오류"
        val stack = prefs.getString(KEY_STACK, null)?.takeIf { it.isNotBlank() }
        val whenStr = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(at))
        return buildString {
            append("$whenStr · $msg")
            if (!stack.isNullOrBlank()) append("\n$stack")
        }
    }

    fun clearCrash(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MSG)
            .remove(KEY_STACK)
            .remove(KEY_AT)
            .apply()
    }
}
