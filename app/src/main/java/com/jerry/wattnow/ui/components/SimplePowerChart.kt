package com.jerry.wattnow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max

@Composable
fun SimplePowerChart(
    modifier: Modifier = Modifier,
    points: List<Double>,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showScale: Boolean = true,
    customMaxVal: Double? = null
) {
    if (points.isEmpty()) return

    val actualMax = points.maxOrNull() ?: 0.0
    val maxVal = max(customMaxVal ?: actualMax, 1.0)
    val midVal = maxVal / 2.0
    val minVal = 0.0
    val range = maxVal - minVal

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chart Line & Grid Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val topPadding = 8.dp.toPx()
                val bottomPadding = 8.dp.toPx()
                val usableHeight = height - topPadding - bottomPadding

                if (usableHeight <= 0f || width <= 0f) return@Canvas

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // 1. Grid reference lines: Top, Mid, Bottom
                // Top line (maxVal)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, topPadding),
                    end = Offset(width, topPadding),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Middle line (midVal)
                val midY = topPadding + usableHeight * 0.5f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Bottom baseline (0.0W)
                val bottomY = topPadding + usableHeight
                drawLine(
                    color = gridColor,
                    start = Offset(0f, bottomY),
                    end = Offset(width, bottomY),
                    strokeWidth = 1.dp.toPx()
                )

                if (points.size < 2) return@Canvas

                val stepX = width / (points.size - 1)

                val linePath = Path()
                val fillPath = Path()

                fillPath.moveTo(0f, bottomY)

                points.forEachIndexed { index, value ->
                    val x = index * stepX
                    val norm = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
                    val y = bottomY - (norm * usableHeight)

                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, bottomY)
                fillPath.close()

                // Draw gradient under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.22f),
                            lineColor.copy(alpha = 0.02f)
                        ),
                        startY = topPadding,
                        endY = bottomY
                    )
                )

                // Draw curve stroke
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Draw last point halo & dot
                val lastX = (points.size - 1) * stepX
                val lastNorm = ((points.last() - minVal) / range).toFloat().coerceIn(0f, 1f)
                val lastY = bottomY - (lastNorm * usableHeight)

                // Outer halo
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = 7.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
                // Inner solid dot
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }

        // Y-Axis Scale Values
        if (showScale) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fW", maxVal),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = axisTextColor,
                    textAlign = TextAlign.End
                )
                Text(
                    text = String.format(Locale.US, "%.1fW", midVal),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = axisTextColor,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "0.0W",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = axisTextColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
