package com.bossxor.lottegiants

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 홈 화면 런처 아이콘을 응원팀 구장 배지로 바꾼다.
 *
 * 매니페스트 `activity-alias` 10개 중 하나만 켠다.
 * 켤 alias를 먼저 활성화한 뒤 나머지를 끈다. 순서가 반대면
 * 런처 컴포넌트가 0개인 순간이 생겨 앱이 홈에서 사라진다.
 *
 * 화면이 떠 있는 동안 컴포넌트를 바꾸면 런처가 태스크를 정리할 수 있어,
 * [request]로만 기억해 두고 [MainActivity.onStop]에서 [applyPending]한다.
 */
object LauncherIcon {

    val TEAM_CODES = listOf("LT", "OB", "LG", "SS", "HH", "KT", "HT", "NC", "SK", "WO")

    @Volatile
    private var pendingCode: String? = null

    fun normalize(teamCode: String): String {
        val code = teamCode.trim().uppercase()
        return if (code in TEAM_CODES) code else "LT"
    }

    fun aliasClassName(teamCode: String): String =
        "${BuildConfig.APPLICATION_ID}.Launcher_${normalize(teamCode)}"

    fun request(teamCode: String) {
        pendingCode = normalize(teamCode)
    }

    fun applyPending(context: Context) {
        val code = pendingCode ?: return
        pendingCode = null
        apply(context, code)
    }

    fun apply(context: Context, teamCode: String) {
        val want = normalize(teamCode)
        val pm = context.packageManager
        val flag = PackageManager.DONT_KILL_APP
        val enable = ComponentName(context, aliasClassName(want))
        pm.setComponentEnabledSetting(
            enable,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            flag,
        )
        for (code in TEAM_CODES) {
            if (code == want) continue
            pm.setComponentEnabledSetting(
                ComponentName(context, aliasClassName(code)),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flag,
            )
        }
    }
}
