package com.bossxor.lottegiants.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bossxor.lottegiants.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class UpdateInfo(
    val versionCode: Int,
    val tagName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

/**
 * GitHub Releases 최신 APK 검사·다운로드·설치.
 * Release body에 `versionCode: N` 이 있어야 비교한다.
 *
 * private 저장소는 local.properties / env 의 `GITHUB_TOKEN` 이
 * BuildConfig 로 주입되어야 API·다운로드가 동작한다.
 */
object UpdateChecker {

    private const val OWNER = "bossxor"
    private const val REPO = "INV_LotteGiants"
    private const val LATEST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val LIST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val versionCodePattern = Pattern.compile(
        """versionCode\s*:\s*(\d+)""",
        Pattern.CASE_INSENSITIVE,
    )

    private fun authRequest(url: String): Request {
        val b = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "sajik-score-android/${BuildConfig.VERSION_NAME}")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val token = BuildConfig.GITHUB_TOKEN.trim()
        if (token.isNotEmpty()) {
            b.header("Authorization", "Bearer $token")
        }
        return b.build()
    }

    private fun downloadRequest(url: String): Request {
        val b = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "sajik-score-android/${BuildConfig.VERSION_NAME}")
        val token = BuildConfig.GITHUB_TOKEN.trim()
        if (token.isNotEmpty()) {
            b.header("Authorization", "Bearer $token")
        }
        return b.build()
    }

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
        when (val r = check(currentVersionCode)) {
            is UpdateCheckResult.Available -> r.info
            else -> null
        }

    suspend fun check(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.GITHUB_TOKEN.isBlank()) {
            // public 저장소면 토큰 없이도 동작. private 면 404 가 난다.
            // 아래에서 응답 코드로 구분한다.
        }
        try {
            val latest = fetchRelease(LATEST_URL)
            val release = latest.getOrElse { err ->
                val errMsg = err.message ?: "릴리즈 조회 실패"
                // latest 실패 시 목록에서 최신 non-draft 탐색
                val listed = fetchReleaseList(LIST_URL).getOrElse {
                    return@withContext UpdateCheckResult.Failed(errMsg)
                }
                listed.maxByOrNull { parseVersionCode(it.body) }
                    ?: return@withContext UpdateCheckResult.Failed(errMsg)
            }
            val remoteCode = parseVersionCode(release.body)
            if (remoteCode <= 0) {
                return@withContext UpdateCheckResult.Failed(
                    "릴리즈 본문에 versionCode: N 이 없습니다.",
                )
            }
            if (remoteCode <= currentVersionCode) {
                return@withContext UpdateCheckResult.UpToDate
            }
            val apk = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    it.browserDownloadUrl.isNotBlank()
            } ?: return@withContext UpdateCheckResult.Failed("릴리즈에 APK가 없습니다.")
            UpdateCheckResult.Available(
                UpdateInfo(
                    versionCode = remoteCode,
                    tagName = release.tagName.orEmpty(),
                    apkUrl = apk.browserDownloadUrl,
                    releaseNotes = release.body.orEmpty().take(400),
                ),
            )
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "업데이트 확인 실패")
        }
    }

    private fun fetchRelease(url: String): Result<GithubRelease> {
        client.newCall(authRequest(url)).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) {
                return Result.failure(
                    IllegalStateException(
                        if (BuildConfig.GITHUB_TOKEN.isBlank()) {
                            "저장소에 접근할 수 없습니다(private일 수 있음). GITHUB_TOKEN 을 설정하세요."
                        } else {
                            "릴리즈를 찾을 수 없습니다."
                        },
                    ),
                )
            }
            if (resp.code == 401 || resp.code == 403) {
                return Result.failure(
                    IllegalStateException("GitHub 인증 실패(${resp.code}). 토큰 권한을 확인하세요."),
                )
            }
            if (!resp.isSuccessful) {
                return Result.failure(
                    IllegalStateException("GitHub API 오류 ${resp.code}"),
                )
            }
            if (body.isBlank()) {
                return Result.failure(IllegalStateException("빈 응답"))
            }
            return Result.success(json.decodeFromString(GithubRelease.serializer(), body))
        }
    }

    private fun fetchReleaseList(url: String): Result<List<GithubRelease>> {
        client.newCall(authRequest(url)).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) {
                return Result.failure(IllegalStateException("릴리즈 목록 오류 ${resp.code}"))
            }
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(GithubRelease.serializer()),
                body,
            ).filter { it.draft != true && it.prerelease != true }
            return Result.success(list)
        }
    }

    fun parseVersionCode(body: String?): Int {
        if (body.isNullOrBlank()) return 0
        val m = versionCodePattern.matcher(body)
        return if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
    }

    /**
     * APK 다운로드 후 설치 인텐트. 성공 시 true.
     * Android 8+ 는 REQUEST_INSTALL_PACKAGES 필요.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    withContext(Dispatchers.Main) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                    return@runCatching false
                }
                client.newCall(downloadRequest(apkUrl)).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching false
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val out = File(dir, "update.apk")
                    if (out.exists()) out.delete()
                    resp.body?.byteStream()?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching false
                    if (out.length() < 1024L) return@runCatching false
                    withContext(Dispatchers.Main) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            out,
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    true
                }
            }.getOrDefault(false)
        }
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    val draft: Boolean? = false,
    val prerelease: Boolean? = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
