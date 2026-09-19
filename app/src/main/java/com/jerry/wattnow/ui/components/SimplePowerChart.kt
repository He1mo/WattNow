package com.jerry.wattnow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SimplePowerChart(
    modifier: Modifier = Modifier,
    points: List<Double>,
    totalDurationMillis: Long? = null,
    sampleTimestamps: List<Long>? = null,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showScale: Boolean = true,
    showXAxis: Boolean = true,
    customMaxVal: Double? = null
) {
    if (points.isEmpty()) return

    val pointsCount = points.size
    var selectedIndex by remember(points.size) { mutableStateOf<Int?>(null) }

    val actualMax = points.maxOrNull() ?: 0.0
    val maxVal = max(customMaxVal ?: actualMax, 1.0)
    val midVal = maxVal / 2.0
    val minVal = 0.0
    val range = maxVal - minVal

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    val totalDurationSec: Double = remember(sampleTimestamps, totalDurationMillis, pointsCount) {
        if (!sampleTimestamps.isNullOrEmpty() && sampleTimestamps.size >= 2) {
            max(1.0, (sampleTimestamps.last() - sampleTimestamps.first()) / 1000.0)
        } else if (totalDurationMillis != null && totalDurationMillis > 0L) {
            max(1.0, totalDurationMillis / 1000.0)
        } else {
            max(1.0, pointsCount * 0.5) // 500ms per point
        }
    }
    val totalDurationMin: Double = totalDurationSec / 60.0

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Chart & X-Axis Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Chart Line & Grid Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pointsCount) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (pointsCount > 1 && size.width > 0) {
                                val ratio = (down.position.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (ratio * (pointsCount - 1)).roundToInt()
                            }
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed && pointsCount > 1 && size.width > 0) {
                                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                    selectedIndex = (ratio * (pointsCount - 1)).roundToInt()
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
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
                    val coords = ArrayList<Offset>(points.size)
                    for (i in points.indices) {
                        val x = i * stepX
                        val norm = ((points[i] - minVal) / range).toFloat().coerceIn(0f, 1f)
                        val y = bottomY - (norm * usableHeight)
                        coords.add(Offset(x, y))
                    }

                    val linePath = Path()
                    val fillPath = Path()

                    linePath.moveTo(coords[0].x, coords[0].y)
                    fillPath.moveTo(0f, bottomY)
                    fillPath.lineTo(coords[0].x, coords[0].y)

                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val cur = coords[i]
                        val cX = (prev.x + cur.x) / 2f
                        linePath.cubicTo(
                            x1 = cX, y1 = prev.y,
                            x2 = cX, y2 = cur.y,
                            x3 = cur.x, y3 = cur.y
                        )
                        fillPath.cubicTo(
                            x1 = cX, y1 = prev.y,
                            x2 = cX, y2 = cur.y,
                            x3 = cur.x, y3 = cur.y
                        )
                    }

                    fillPath.lineTo(width, bottomY)
                    fillPath.close()

                    // Draw gradient under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.22f),
                                lineColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            startY = topPadding,
                            endY = bottomY
                        )
                    )

                    // Draw soft ambient glow stroke
                    drawPath(
                        path = linePath,
                        color = lineColor.copy(alpha = 0.22f),
                        style = Stroke(
                            width = 5.5.dp.toPx(),
                            cap = StrokeCap.Round
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
                    val last = coords.last()
                    drawCircle(
                        color = lineColor.copy(alpha = 0.25f),
                        radius = 7.dp.toPx(),
                        center = last
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 3.5.dp.toPx(),
                        center = last
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 1.2.dp.toPx(),
                        center = last
                    )

                    // Scrubber line and highlighted point
                    val sel = selectedIndex
                    if (sel != null && sel in 0 until pointsCount) {
                        val scrubX = sel * stepX
                        val pVal = points[sel]
                        val pNorm = ((pVal - minVal) / range).toFloat().coerceIn(0f, 1f)
                        val pY = bottomY - (pNorm * usableHeight)

                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(scrubX, topPadding),
                            end = Offset(scrubX, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        drawCircle(
                            color = lineColor.copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(scrubX, pY)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(scrubX, pY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.5.dp.toPx(),
                            center = Offset(scrubX, pY)
                        )
                    }
                }

                // Floating tooltip pill when scrubbed
                val sel = selectedIndex
                if (sel != null && sel in 0 until pointsCount) {
                    val timeLabel = if (!sampleTimestamps.isNullOrEmpty() && sel < sampleTimestamps.size) {
                        val startT = sampleTimestamps.first()
                        val curT = sampleTimestamps[sel]
                        val elapsedSec = (curT - startT) / 1000.0
                        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(curT))
                        if (elapsedSec < 60) {
                            String.format(Locale.US, "第 %d 秒 (%s)", elapsedSec.roundToInt(), timeFmt)
                        } else {
                            val elapsedMin = elapsedSec / 60.0
                            String.format(Locale.US, "第 %.1f 分钟 (%s)", elapsedMin, timeFmt)
                        }
                    } else {
                        val sec = if (pointsCount > 1) (sel.toDouble() / (pointsCount - 1)) * totalDurationSec else 0.0
                        if (totalDurationSec <= 120.0) {
                            String.format(Locale.US, "已运行 %d 秒", sec.roundToInt())
                        } else {
                            String.format(Locale.US, "已运行 %.1f 分钟", sec / 60.0)
                        }
                    }
                    val pStr = String.format(Locale.US, "%.1f W", points[sel])

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E22).copy(alpha = 0.88f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = timeLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = pStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = lineColor
                            )
                        }
                    }
                }
            }

            // X-Axis Scale: Elapsed seconds/minutes along the bottom of the chart
            if (showXAxis) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ticks = if (totalDurationSec in 25.0..35.0 || totalDurationSec in 55.0..65.0) 4 else 5
                    for (i in 0 until ticks) {
                        val fraction = i.toDouble() / (ticks - 1)
                        val label = when {
                            totalDurationSec <= 120.0 -> "${(fraction * totalDurationSec).roundToInt()}s"
                            totalDurationSec < 3600.0 -> String.format(Locale.US, "%.0fm", (fraction * totalDurationSec) / 60.0)
                            else -> String.format(Locale.US, "%.1fh", (fraction * totalDurationSec) / 3600.0)
                        }
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = axisTextColor
                        )
                    }
                }
            }
        }

        // Y-Axis Scale Values
        if (showScale) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 2.dp, bottom = if (showXAxis) 16.dp else 2.dp),
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
