package com.bossxor.lottegiants.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.focusName

/**
 * Now Bar는 커스텀 뷰를 못 쓰므로 큰 아이콘·알약 아이콘 자리에 직접 그린 비트맵을 넣는다.
 */
object NowBarArt {
    private const val RED = 0xFFD00F31.toInt()
    private const val MUTED = 0xFF8A8F98.toInt()
    private const val OFF = 0xFFB9BDC4.toInt()

    private fun paint(block: Paint.() -> Unit = {}) = Paint(Paint.ANTI_ALIAS_FLAG).apply(block)

    private fun drawLogo(c: Canvas, logo: Bitmap, cx: Float, cy: Float, r: Float) {
        c.drawBitmap(logo, null, RectF(cx - r, cy - r, cx + r, cy + r), paint { isFilterBitmap = true })
    }

    /**
     * 가로 2:1. 왼쪽 원정, 오른쪽 홈. 가운데는 `vs` 또는 루상 다이아몬드.
     * largeIcon·미리보기용.
     */
    fun logoPair(left: Bitmap, right: Bitmap, middle: String, live: LotteGameInfo? = null): Bitmap {
        val w = 384
        val h = 192
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = if (live != null) 62f else 76f
        drawLogo(c, left, 6f + r, h / 2f, r)
        drawLogo(c, right, w - 6f - r, h / 2f, r)
        if (live != null) {
            drawDiamond(c, live, w / 2f, h / 2f, 0.82f)
        } else if (middle.isNotBlank()) {
            val t = paint {
                color = MUTED
                typeface = Typeface.DEFAULT_BOLD
                textSize = 44f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(middle, w / 2f, h / 2f - (t.descent() + t.ascent()) / 2f, t)
        }
        return bmp
    }

    /** (cx, cy) 중심에 루상 다이아몬드(주자 있는 베이스는 빨강) + 다이아몬드 아래 아웃카운트 점. 기준 크기 192. */
    private fun drawDiamond(c: Canvas, g: LotteGameInfo, cx: Float, cy: Float, k: Float) {
        val top = cy - 96f * k
        val bx = cx
        val by = top + 80f * k
        val d = 56f * k
        val half = 20f * k

        fun base(x: Float, y: Float, on: Boolean) {
            val p = Path().apply {
                moveTo(x, y - half); lineTo(x + half, y); lineTo(x, y + half); lineTo(x - half, y); close()
            }
            c.drawPath(p, paint { color = if (on) RED else OFF; style = Paint.Style.FILL })
        }
        base(bx, by - d, g.onBase2)
        base(bx + d, by, g.onBase1)
        base(bx - d, by, g.onBase3)
        val home = Path().apply {
            moveTo(bx - 14f * k, by + d - 10f * k); lineTo(bx + 14f * k, by + d - 10f * k)
            lineTo(bx + 14f * k, by + d + 4f * k); lineTo(bx, by + d + 16f * k); lineTo(bx - 14f * k, by + d + 4f * k); close()
        }
        c.drawPath(home, paint { color = MUTED })

        val out = g.out.coerceIn(0, 2)
        for (i in 0 until 2) {
            val x = bx - 22f * k + i * 44f * k
            c.drawCircle(x, top + 172f * k, 11f * k, paint {
                if (i < out) { color = RED; style = Paint.Style.FILL } else { color = OFF; style = Paint.Style.STROKE; strokeWidth = 4f * k }
            })
        }
    }

    /** 이닝별 점수표(원정 위, 홈 아래) + R H E. 현재 이닝 열은 빨강, 득점한 이닝은 빨간 굵은 글자. */
    fun scoreboard(g: LotteGameInfo, night: Boolean): Bitmap {
        val away = if (g.isHome) g.opponentInningScores else g.lotteInningScores
        val home = if (g.isHome) g.lotteInningScores else g.opponentInningScores
        val awayName = if (g.isHome) g.opponentName else g.focusName()
        val homeName = if (g.isHome) g.focusName() else g.opponentName
        val awayR = if (g.isHome) g.opponentScore else g.lotteScore
        val homeR = if (g.isHome) g.lotteScore else g.opponentScore
        val awayH = if (g.isHome) g.opponentHits else g.lotteHits
        val homeH = if (g.isHome) g.lotteHits else g.opponentHits
        val awayE = if (g.isHome) g.opponentErrors else g.lotteErrors
        val homeE = if (g.isHome) g.lotteErrors else g.opponentErrors
        val live = g.status == com.bossxor.lottegiants.domain.GameStatus.LIVE
        val n = maxOf(9, away.size, home.size, if (live) g.inning else 0).coerceAtMost(12)

        val ink = if (night) 0xFFF2F2F2.toInt() else 0xFF1B1B1F.toInt()
        val sub = if (night) 0xFFB5B8BF.toInt() else 0xFF6B6F77.toInt()
        val line = if (night) 0x33FFFFFF else 0x22000000

        val w = 1280
        val rowH = 80f
        val h = (rowH * 3 + 12).toInt()
        val labelW = 136f
        val statW = 64f
        val colW = (w - labelW - statW * 3) / n
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun text(color: Int, bold: Boolean, size: Float, align: Paint.Align) = paint {
            this.color = color
            textSize = size
            textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun cell(t: String, cx: Float, row: Int, p: Paint) {
            val y = row * rowH + rowH / 2f - (p.descent() + p.ascent()) / 2f
            c.drawText(t, cx, y, p)
        }
        val hdr = text(sub, false, 36f, Paint.Align.CENTER)
        val hdrNow = text(RED, true, 36f, Paint.Align.CENTER)
        for (i in 0 until n) {
            cell("${i + 1}", labelW + colW * i + colW / 2f, 0, if (live && i + 1 == g.inning) hdrNow else hdr)
        }
        listOf("R", "H", "E").forEachIndexed { k, t ->
            cell(t, labelW + colW * n + statW * k + statW / 2f, 0, text(sub, true, 36f, Paint.Align.CENTER))
        }
        c.drawRect(0f, rowH, w.toFloat(), rowH + 2f, paint { color = line })
        val nameP = text(ink, true, 38f, Paint.Align.LEFT)
        val plain = text(ink, false, 44f, Paint.Align.CENTER)
        val hot = text(RED, true, 44f, Paint.Align.CENTER)
        val rows = listOf(
            Triple(awayName, away, intArrayOf(awayR, awayH, awayE)),
            Triple(homeName, home, intArrayOf(homeR, homeH, homeE)),
        )
        rows.forEachIndexed { r, (name, sc, rhe) ->
            val row = r + 1
            val y = row * rowH + rowH / 2f - (nameP.descent() + nameP.ascent()) / 2f
            c.drawText(name.take(4), 8f, y, nameP)
            for (i in 0 until n) {
                val raw = sc.getOrNull(i)?.trim().orEmpty()
                val t = raw.ifBlank { if (i < sc.size || !live) "-" else "" }
                cell(t, labelW + colW * i + colW / 2f, row, if ((raw.toIntOrNull() ?: 0) > 0) hot else plain)
            }
            rhe.forEachIndexed { k, v ->
                cell("$v", labelW + colW * n + statW * k + statW / 2f, row, text(ink, k == 0, 44f, Paint.Align.CENTER))
            }
        }
        return bmp
    }
}