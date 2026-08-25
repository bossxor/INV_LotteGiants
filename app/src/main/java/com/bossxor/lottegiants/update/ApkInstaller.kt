package com.bossxor.lottegiants.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 시스템 패키지 설치 세션으로 자기 자신을 갱신한다.
 *
 * 외부 파일 앱을 거치지 않는다. Android 12+ 에서는 이 앱이 installer of record 이면
 * 사용자 확인 없이 설치가 끝날 수 있고, 아니면 시스템 확인 화면만 한 번 뜬다.
 */
object ApkInstaller {

    const val ACTION_INSTALL_RESULT = "com.bossxor.lottegiants.INSTALL_RESULT"

    private const val TAG = "SajikUpdate"

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                setRequestUpdateOwnership(true)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
            Log.i(TAG, "package installer session committed id=$sessionId size=${apk.length()}")
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            throw t
        } finally {
            runCatching { session.close() }
        }
    }
}
