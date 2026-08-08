package com.bossxor.lottegiants.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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

/**
 * GitHub Releases 최신 APK 검사·다운로드·설치.
 * Release body에 `versionCode: N` 이 있어야 비교한다.
 */
object UpdateChecker {

    private const val OWNER = "bossxor"
    private const val REPO = "INV_LotteGiants"
    private const val LATEST_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val versionCodePattern = Pattern.compile(
        """versionCode\s*:\s*(\d+)""",
        Pattern.CASE_INSENSITIVE,
    )

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "sajik-score-android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                val release = json.decodeFromString(GithubRelease.serializer(), body)
                val remoteCode = parseVersionCode(release.body).takeIf { it > 0 }
                    ?: return@runCatching null
                if (remoteCode <= currentVersionCode) return@runCatching null
                val apk = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@runCatching null
                UpdateInfo(
                    versionCode = remoteCode,
                    tagName = release.tagName.orEmpty(),
                    apkUrl = apk.browserDownloadUrl,
                    releaseNotes = release.body.orEmpty().take(400),
                )
            }
        }.getOrNull()
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
                val req = Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", "sajik-score-android")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching false
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val out = File(dir, "update.apk")
                    if (out.exists()) out.delete()
                    resp.body?.byteStream()?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching false
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
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
