package com.bossxor.lottegiants.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.glance.ImageProvider
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.domain.IMAGE_USER_AGENT
import com.bossxor.lottegiants.domain.imageRefererForHost
import com.bossxor.lottegiants.domain.playerPhotoCandidates
import com.bossxor.lottegiants.domain.kboTeamEmblemUrl
import com.bossxor.lottegiants.domain.resolveTeamLogoUrl
import com.bossxor.lottegiants.domain.teamLogoUrl
import com.bossxor.lottegiants.domain.teamNameToCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object WidgetAssets {

    @DrawableRes
    fun basesDrawable(on1: Boolean, on2: Boolean, on3: Boolean): Int {
        val key = (if (on1) 1 else 0) or (if (on2) 2 else 0) or (if (on3) 4 else 0)
        return when (key) {
            1 -> R.drawable.ic_bases_1
            2 -> R.drawable.ic_bases_2
            3 -> R.drawable.ic_bases_12
            4 -> R.drawable.ic_bases_3
            5 -> R.drawable.ic_bases_13
            6 -> R.drawable.ic_bases_23
            7 -> R.drawable.ic_bases_123
            else -> R.drawable.ic_bases_0
        }
    }

    @DrawableRes
    fun countDot(filled: Boolean, kind: Char): Int = when {
        !filled -> R.drawable.ic_dot_empty
        kind == 'B' -> R.drawable.ic_dot_ball
        kind == 'S' -> R.drawable.ic_dot_strike
        else -> R.drawable.ic_dot_out
    }

    suspend fun logoProvider(
        context: Context,
        teamCode: String,
        url: String,
        teamName: String = "",
    ): ImageProvider {
        val code = teamCode.trim().uppercase().ifBlank { teamNameToCode(teamName) }
        val urls = listOfNotNull(
            resolveTeamLogoUrl(code, url).takeIf { it.isNotBlank() },
            teamLogoUrl(code).takeIf { code.isNotBlank() },
            kboTeamEmblemUrl(code).takeIf { code.isNotBlank() },
            url.takeIf { it.isNotBlank() },
        ).distinct()
        val cacheKey = code.ifBlank { teamName.take(8).ifBlank { "UNK" } }
        val bmp = loadCachedBitmap(context, "team_logos", "$cacheKey.png", urls)
        return if (bmp != null) {
            ImageProvider(bmp)
        } else {
            ImageProvider(teamInitialBitmap(code, teamName))
        }
    }

    fun teamInitialBitmap(teamCode: String, teamName: String): Bitmap {
        val label = teamName.trim().take(1).ifBlank { teamCode.take(1) }.ifBlank { "?" }.uppercase()
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF18243A.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bmp
    }

    suspend fun playerProvider(context: Context, playerCode: String, url: String): ImageProvider {
        if (playerCode.isBlank()) return ImageProvider(R.drawable.ic_notification)
        val urls = (listOf(url) + playerPhotoCandidates(playerCode)).distinct().filter { it.isNotBlank() }
        val bmp = loadCachedBitmap(context, "player_photos", "$playerCode.png", urls)
        return if (bmp != null) ImageProvider(bmp) else ImageProvider(R.drawable.ic_notification)
    }

    suspend fun loadPlayerBitmap(context: Context, playerCode: String, url: String): Bitmap? {
        if (playerCode.isBlank()) return null
        val urls = (listOf(url) + playerPhotoCandidates(playerCode)).distinct().filter { it.isNotBlank() }
        return loadCachedBitmap(context, "player_photos", "$playerCode.png", urls)
    }

    suspend fun loadTeamLogoBitmap(
        context: Context,
        teamCode: String,
        url: String = "",
        teamName: String = "",
    ): Bitmap {
        val code = teamCode.trim().uppercase().ifBlank { teamNameToCode(teamName) }
        val urls = listOfNotNull(
            resolveTeamLogoUrl(code, url).takeIf { it.isNotBlank() },
            teamLogoUrl(code).takeIf { code.isNotBlank() },
            kboTeamEmblemUrl(code).takeIf { code.isNotBlank() },
            url.takeIf { it.isNotBlank() },
        ).distinct()
        val cacheKey = code.ifBlank { teamName.take(8).ifBlank { "UNK" } }
        return loadCachedBitmap(context, "team_logos", "$cacheKey.png", urls)
            ?: teamInitialBitmap(code, teamName)
    }

    /** 알림 RemoteViews용 — 디스크 캐시만 읽고 네트워크는 타지 않는다 (FGS 메인 스레드 안전). */
    fun loadTeamLogoBitmapCachedOnly(
        context: Context,
        teamCode: String,
        teamName: String = "",
    ): Bitmap {
        val code = teamCode.trim().uppercase().ifBlank { teamNameToCode(teamName) }
        val cacheKey = code.ifBlank { teamName.take(8).ifBlank { "UNK" } }
        val file = File(context.cacheDir, "team_logos/$cacheKey.png")
        if (file.exists() && file.length() > 8_000L) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()?.let { return it }
        }
        return teamInitialBitmap(code, teamName)
    }

    /** 좌(원정)·우(홈) 실시간 승률 바. 어두운 팀색은 알림 배경에 묻히지 않게 밝힌다. */
    fun winProbBarColor(teamCode: String): Int {
        val c = com.bossxor.lottegiants.domain.teamAccentColor(teamCode)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        if (lum >= 48) return c
        fun mix(ch: Int) = (ch + ((255 - ch) * 0.42)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
    }

    fun winProbBarBitmap(
        leftProb: Float,
        leftColor: Int,
        rightColor: Int,
        width: Int = 480,
        height: Int = 20,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = height / 2f
        val clip = Path().apply {
            addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        val leftW = (width * leftProb.coerceIn(0.02f, 0.98f)).toInt().coerceIn(2, width - 2)
        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leftColor }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rightColor }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
        canvas.drawRect(0f, 0f, leftW.toFloat(), height.toFloat(), leftPaint)
        canvas.drawRect(leftW.toFloat(), 0f, width.toFloat(), height.toFloat(), rightPaint)
        canvas.drawRect((leftW - 1).toFloat(), 0f, (leftW + 1).toFloat(), height.toFloat(), divider)
        return bmp
    }

    private suspend fun loadCachedBitmap(
        context: Context,
        dirName: String,
        fileName: String,
        url: String,
    ): Bitmap? = loadCachedBitmap(context, dirName, fileName, listOf(url))

    private suspend fun loadCachedBitmap(
        context: Context,
        dirName: String,
        fileName: String,
        urls: List<String>,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, dirName).also { it.mkdirs() }
                val file = File(dir, fileName)
                // 예전 스코어보드 이니셜(1~2KB) 캐시가 남아 있으면 다시 받는다.
                val stale = dirName == "team_logos" && file.exists() && file.length() < 8_000L
                val tooSmallPhoto = dirName == "player_photos" && file.exists() && file.length() < 5_000L
                if (!file.exists() || file.length() == 0L || stale || tooSmallPhoto) {
                    var saved = false
                    for (candidate in urls) {
                        if (candidate.isBlank()) continue
                        saved = downloadToFile(candidate, file)
                        if (saved) break
                    }
                    if (!saved) return@withContext null
                }
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
                null
            }
        }

    private fun downloadToFile(url: String, file: File): Boolean {
        return try {
            val parsed = URL(url)
            val conn = (parsed.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", IMAGE_USER_AGENT)
                imageRefererForHost(parsed.host)?.let { setRequestProperty("Referer", it) }
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return false
            }
            val contentType = conn.contentType.orEmpty().lowercase()
            if (contentType.isNotBlank() && !contentType.startsWith("image/")) {
                conn.disconnect()
                return false
            }
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            file.length() > 400L
        } catch (_: Exception) {
            false
        }
    }

    fun formLetter(lotteScore: Int, oppScore: Int): String = when {
        lotteScore > oppScore -> "W"
        lotteScore < oppScore -> "L"
        else -> "D"
    }
}
