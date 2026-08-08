package com.bossxor.lottegiants.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.glance.ImageProvider
import com.bossxor.lottegiants.R
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

    suspend fun logoProvider(context: Context, teamCode: String, url: String): ImageProvider {
        val bmp = loadCachedBitmap(context, "team_logos", "${teamCode.ifBlank { "UNK" }}.png", url)
        return if (bmp != null) ImageProvider(bmp) else ImageProvider(R.drawable.ic_notification)
    }

    suspend fun playerProvider(context: Context, playerCode: String, url: String): ImageProvider {
        if (playerCode.isBlank()) return ImageProvider(R.drawable.ic_notification)
        val bmp = loadCachedBitmap(context, "player_photos", "$playerCode.png", url)
        return if (bmp != null) ImageProvider(bmp) else ImageProvider(R.drawable.ic_notification)
    }

    suspend fun loadPlayerBitmap(context: Context, playerCode: String, url: String): Bitmap? {
        if (playerCode.isBlank()) return null
        return loadCachedBitmap(context, "player_photos", "$playerCode.png", url)
    }

    private suspend fun loadCachedBitmap(
        context: Context,
        dirName: String,
        fileName: String,
        url: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, dirName).also { it.mkdirs() }
                val file = File(dir, fileName)
                if (!file.exists() || file.length() == 0L) {
                    if (url.isBlank()) return@withContext null
                    URL(url).openStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
                null
            }
        }

    fun formLetter(lotteScore: Int, oppScore: Int): String = when {
        lotteScore > oppScore -> "W"
        lotteScore < oppScore -> "L"
        else -> "D"
    }
}
