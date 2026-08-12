package com.bossxor.lottegiants.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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

sealed class InstallResult {
    data object Launched : InstallResult()
    data object NeedsPermission : InstallResult()
    data class DownloadFailed(val message: String) : InstallResult()
}

/**
 * 앱 업데이트 검사·다운로드·설치.
 *
 * 우선순위:
 * 1. 고정 채널 `latest` 릴리스의 `update.json` (+ APK)
 *    → main 푸시마다 CI/스크립트가 덮어쓰므로 수동 버전 릴리스가 필요 없다.
 * 2. GitHub `/releases/latest` (본문에 `versionCode: N`)
 *
 * public 저장소는 토큰 없이 동작한다.
 * private 이면 local.properties / env 의 `GITHUB_TOKEN` 을 BuildConfig 로 주입한다.
 *
 * Android는 일반 앱이 사용자 확인 없이 조용히 설치를 끝낼 수 없다.
 * 대신 실행 시 자동으로 받아 시스템 설치 화면까지 연다.
 */
object UpdateChecker {

    private const val TAG = "SajikUpdate"
    private const val OWNER = "bossxor"
    private const val REPO = "INV_LotteGiants"
    private const val LATEST_CHANNEL_TAG = "latest"
    private const val LATEST_CHANNEL_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/tags/$LATEST_CHANNEL_TAG"
    private const val LATEST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val LIST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"
    private const val MANIFEST_NAME = "update.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
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
        try {
            val fromChannel = checkLatestChannel(currentVersionCode)
            if (fromChannel !is UpdateCheckResult.Failed) {
                return@withContext fromChannel
            }
            Log.i(TAG, "latest channel unavailable: ${(fromChannel as UpdateCheckResult.Failed).message}")
            checkTaggedReleases(currentVersionCode)
        } catch (e: Exception) {
            Log.e(TAG, "check failed", e)
            UpdateCheckResult.Failed(e.message ?: "업데이트 확인 실패")
        }
    }

    /** 고정 `latest` 채널 — update.json 우선, 없으면 릴리스 본문·APK로 판단 */
    private fun checkLatestChannel(currentVersionCode: Int): UpdateCheckResult {
        val release = fetchRelease(LATEST_CHANNEL_URL).getOrElse {
            return UpdateCheckResult.Failed(it.message ?: "latest 채널 없음")
        }
        val manifestAsset = release.assets.firstOrNull {
            it.name.equals(MANIFEST_NAME, ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }
        if (manifestAsset != null) {
            val manifest = downloadManifest(manifestAsset.browserDownloadUrl)
                ?: return UpdateCheckResult.Failed("update.json을 읽지 못했습니다.")
            if (manifest.versionCode <= 0) {
                return UpdateCheckResult.Failed("update.json에 versionCode가 없습니다.")
            }
            if (manifest.versionCode <= currentVersionCode) {
                Log.i(TAG, "result=UpToDate channel current=$currentVersionCode remote=${manifest.versionCode}")
                return UpdateCheckResult.UpToDate
            }
            val apkName = manifest.apkFileName.ifBlank { "LotteGiants.apk" }
            val apk = release.assets.firstOrNull {
                it.name.equals(apkName, ignoreCase = true)
            } ?: release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true)
            } ?: return UpdateCheckResult.Failed("latest 채널에 APK가 없습니다.")
            Log.i(
                TAG,
                "result=Available channel current=$currentVersionCode remote=${manifest.versionCode}",
            )
            return UpdateCheckResult.Available(
                UpdateInfo(
                    versionCode = manifest.versionCode,
                    tagName = manifest.versionName.ifBlank { LATEST_CHANNEL_TAG },
                    apkUrl = apk.browserDownloadUrl,
                    releaseNotes = manifest.notes.take(400),
                ),
            )
        }
        return releaseToResult(release, currentVersionCode)
    }

    private fun checkTaggedReleases(currentVersionCode: Int): UpdateCheckResult {
        val latest = fetchRelease(LATEST_URL)
        val release = latest.getOrElse { err ->
            val errMsg = err.message ?: "릴리즈 조회 실패"
            Log.w(TAG, "latest failed: $errMsg")
            val listed = fetchReleaseList(LIST_URL).getOrElse {
                return UpdateCheckResult.Failed(errMsg)
            }
            listed.maxByOrNull { parseVersionCode(it.body) }
                ?: return UpdateCheckResult.Failed(errMsg)
        }
        return releaseToResult(release, currentVersionCode)
    }

    private fun releaseToResult(release: GithubRelease, currentVersionCode: Int): UpdateCheckResult {
        val remoteCode = parseVersionCode(release.body)
        if (remoteCode <= 0) {
            val msg = "릴리즈 본문에 versionCode: N 이 없습니다."
            Log.i(TAG, "result=Failed current=$currentVersionCode msg=$msg")
            return UpdateCheckResult.Failed(msg)
        }
        if (remoteCode <= currentVersionCode) {
            Log.i(TAG, "result=UpToDate current=$currentVersionCode remote=$remoteCode")
            return UpdateCheckResult.UpToDate
        }
        val apk = release.assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) &&
                it.browserDownloadUrl.isNotBlank()
        } ?: return UpdateCheckResult.Failed("릴리즈에 APK가 없습니다.")
        Log.i(
            TAG,
            "result=Available current=$currentVersionCode remote=$remoteCode tag=${release.tagName}",
        )
        return UpdateCheckResult.Available(
            UpdateInfo(
                versionCode = remoteCode,
                tagName = release.tagName.orEmpty(),
                apkUrl = apk.browserDownloadUrl,
                releaseNotes = release.body.orEmpty().take(400),
            ),
        )
    }

    private fun downloadManifest(url: String): UpdateManifest? {
        client.newCall(downloadRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "manifest http=${resp.code}")
                return null
            }
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            return runCatching {
                json.decodeFromString(UpdateManifest.serializer(), body)
            }.getOrNull()
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
                return Result.failure(IllegalStateException("GitHub API 오류 ${resp.code}"))
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
            ).filter { it.draft != true && it.prerelease != true && it.tagName != LATEST_CHANNEL_TAG }
            return Result.success(list)
        }
    }

    fun parseVersionCode(body: String?): Int {
        if (body.isNullOrBlank()) return 0
        val m = versionCodePattern.matcher(body)
        return if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun updateFile(context: Context): File =
        File(File(context.cacheDir, "updates").apply { mkdirs() }, "update.apk")

    /**
     * APK 다운로드 후 설치 인텐트.
     * 권한이 없으면 APK를 먼저 받은 뒤 설정 화면을 열고 [InstallResult.NeedsPermission]을 반환한다.
     */
    suspend fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        store: SnapshotStore? = null,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            val out = downloadApk(context, info.apkUrl, onProgress)
                ?: return@withContext InstallResult.DownloadFailed("APK 다운로드에 실패했습니다.")
            store?.setPendingUpdate(out.absolutePath, info.versionCode)
            if (!canInstallPackages(context)) {
                withContext(Dispatchers.Main) {
                    openInstallPermissionSettings(context)
                }
                return@withContext InstallResult.NeedsPermission
            }
            launchInstall(context, out)
            store?.clearPendingUpdate()
            InstallResult.Launched
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndInstall failed", e)
            InstallResult.DownloadFailed(e.message ?: "설치 준비 실패")
        }
    }

    /** 권한 승인 후 대기 중인 APK를 이어서 설치. */
    suspend fun resumePendingInstall(
        context: Context,
        store: SnapshotStore,
    ): InstallResult = withContext(Dispatchers.IO) {
        val path = store.pendingUpdateApkPath()
        if (path.isBlank()) {
            return@withContext InstallResult.DownloadFailed("대기 중인 업데이트가 없습니다.")
        }
        val file = File(path)
        if (!file.exists() || file.length() < 1024L) {
            store.clearPendingUpdate()
            return@withContext InstallResult.DownloadFailed("다운로드된 APK를 찾을 수 없습니다.")
        }
        if (!canInstallPackages(context)) {
            withContext(Dispatchers.Main) {
                openInstallPermissionSettings(context)
            }
            return@withContext InstallResult.NeedsPermission
        }
        launchInstall(context, file)
        store.clearPendingUpdate()
        InstallResult.Launched
    }

    private fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)?,
    ): File? {
        client.newCall(downloadRequest(apkUrl)).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "download failed http=${resp.code}")
                return null
            }
            val body = resp.body ?: return null
            val total = body.contentLength()
            val out = updateFile(context)
            if (out.exists()) out.delete()
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (onProgress != null && total > 0L) {
                            val pct = ((downloaded * 100) / total).toInt()
                            if (pct != lastPct && (pct == 100 || pct - lastPct >= 2)) {
                                lastPct = pct
                                val d = downloaded
                                mainHandler.post { onProgress(d, total) }
                            }
                        }
                    }
                }
            }
            if (out.length() < 1024L) {
                out.delete()
                return null
            }
            return out
        }
    }

    private suspend fun launchInstall(context: Context, apk: File) {
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 하위 호환: 성공 여부만 필요할 때 */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): Boolean =
        when (
            downloadAndInstall(
                context,
                UpdateInfo(versionCode = 0, tagName = "", apkUrl = apkUrl),
                store = null,
            )
        ) {
            is InstallResult.Launched -> true
            else -> false
        }
}

@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkFileName: String = "LotteGiants.apk",
    val notes: String = "",
)

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
