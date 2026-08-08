package com.bossxor.lottegiants.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 단순 스파크라인.
 * @param invertY true면 값이 작을수록 위 (순위 차트용)
 */
@Composable
fun SparklineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillAlpha: Float = 0.08f,
    strokeWidth: Float = 2f,
    invertY: Boolean = false,
    yMin: Double? = null,
    yMax: Double? = null,
) {
    if (values.size < 2) return
    val minV = yMin ?: values.min()
    val maxV = yMax ?: values.max()
    val range = (maxV - minV).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val padY = 3f
        val usableH = h - padY * 2
        fun yOf(v: Double): Float {
            val t = ((v - minV) / range).toFloat().coerceIn(0f, 1f)
            return if (invertY) padY + t * usableH else padY + (1f - t) * usableH
        }
        val step = w / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = yOf(v)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo((values.size - 1) * step, h)
        fill.close()
        drawPath(fill, lineColor.copy(alpha = fillAlpha))
        drawPath(path, lineColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        val lastX = (values.size - 1) * step
        val lastY = yOf(values.last())
        drawCircle(lineColor, radius = 2.8f, center = Offset(lastX, lastY))
    }
}
