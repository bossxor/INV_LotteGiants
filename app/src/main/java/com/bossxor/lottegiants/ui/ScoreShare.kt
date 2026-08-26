package com.bossxor.lottegiants.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.focusName
import com.bossxor.lottegiants.domain.inningLabel
import java.io.File

object ScoreShare {

    fun share(context: Context, game: LotteGameInfo) {
        val deepLink = "sajik://game/${game.gameId}?tab=relay"
        val caption = buildString {
            append(game.focusName())
            append(" ${game.lotteScore}:${game.opponentScore} ")
            append(game.opponentName)
            append(" · ${game.inningLabel}")
            append("\n#사직스코어")
            append("\n$deepLink")
        }
        val bitmap = renderBoard(game)
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "sajik-score.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra(Intent.EXTRA_SUBJECT, "사직스코어")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "점수 공유"))
    }

    private fun renderBoard(g: LotteGameInfo): Bitmap {
        val w = 1080
        val h = 560
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF18243A.toInt())

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC9A227.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDEFFFFFF.toInt()
            textSize = 42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 160f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("사직스코어", 48f, 64f, title)
        val showScore = g.status == GameStatus.LIVE || g.status == GameStatus.ENDED
        val leftName = if (g.isHome) g.opponentName.ifBlank { "상대" } else g.focusName()
        val rightName = if (g.isHome) g.focusName() else g.opponentName.ifBlank { "상대" }
        val leftScore = if (g.isHome) g.opponentScore else g.lotteScore
        val rightScore = if (g.isHome) g.lotteScore else g.opponentScore
        c.drawText(leftName, w * 0.28f, 170f, namePaint)
        c.drawText(rightName, w * 0.72f, 170f, namePaint)
        if (showScore) {
            c.drawText("$leftScore", w * 0.28f, 360f, scorePaint)
            c.drawText("$rightScore", w * 0.72f, 360f, scorePaint)
            scorePaint.textSize = 72f
            c.drawText(":", w * 0.5f, 330f, scorePaint)
        }
        c.drawText(g.inningLabel.ifBlank { g.startTime }, w * 0.5f, 470f, sub)
        c.drawText("#사직스코어", w * 0.5f, 520f, sub)
        return bmp
    }
}
