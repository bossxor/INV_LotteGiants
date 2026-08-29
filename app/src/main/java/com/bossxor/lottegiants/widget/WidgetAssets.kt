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

    /** 알림 largeIcon / Now Bar용 B·S·O + 주자 다이아몬드. */
    fun ballCountBitmap(
        context: Context,
        ball: Int,
        strike: Int,
        out: Int,
        on1: Boolean = false,
        on2: Boolean = false,
        on3: Boolean = false,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (72f * d).toInt().coerceAtLeast(144)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF21A1C1E.toInt() }
        canvas.drawCircle(cx, cy, size / 2f, bg)

        val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.10f
            textAlign = Paint.Align.LEFT
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val emptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        val emptyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.012f
            color = 0x66FFFFFF
        }

        fun row(y: Float, label: String, color: Int, filled: Int, total: Int) {
            letter.color = color
            canvas.drawText(label, size * 0.18f, y + letter.textSize * 0.35f, letter)
            val r = size * 0.048f
            val startX = size * 0.40f
            val gap = size * 0.115f
            fill.color = color
            repeat(total) { i ->
                val x = startX + i * gap
                if (i < filled) {
                    canvas.drawCircle(x, y, r, fill)
                } else {
                    canvas.drawCircle(x, y, r, emptyFill)
                    canvas.drawCircle(x, y, r, emptyStroke)
                }
            }
        }

        row(size * 0.22f, "B", 0xFF2EA35C.toInt(), ball.coerceIn(0, 4), 4)
        row(size * 0.38f, "S", 0xFFC9A227.toInt(), strike.coerceIn(0, 3), 3)
        row(size * 0.54f, "O", 0xFFC8102E.toInt(), out.coerceIn(0, 3), 3)

        val occupied = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCFF00.toInt() }
        val vacant = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6A7380.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.012f
            color = 0xFF1A3300.toInt()
        }
        val s = size * 0.055f
        val baseCy = size * 0.78f
        fun diamond(x: Float, y: Float, on: Boolean) {
            val p = Path().apply {
                moveTo(x, y - s)
                lineTo(x + s, y)
                lineTo(x, y + s)
                lineTo(x - s, y)
                close()
            }
            canvas.drawPath(p, if (on) occupied else vacant)
            if (on) canvas.drawPath(p, stroke)
        }
        diamond(cx, baseCy - size * 0.09f, on2)
        diamond(cx - size * 0.11f, baseCy + size * 0.01f, on3)
        diamond(cx + size * 0.11f, baseCy + size * 0.01f, on1)
        return bmp
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

    /** 좌(원정)·우(홈) 실시간 승률 바 */
    fun winProbBarBitmap(
        leftProb: Float,
        leftColor: Int,
        rightColor: Int,
        width: Int = 480,
        height: Int = 16,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val leftW = (width * leftProb.coerceIn(0.02f, 0.98f)).toInt().coerceIn(2, width - 2)
        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leftColor }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rightColor }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
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
