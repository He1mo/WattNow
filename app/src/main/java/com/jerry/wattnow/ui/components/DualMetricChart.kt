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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class ChartDisplayMode(val label: String) {
    COMBINED("综合"),
    POWER_ONLY("功率"),
    TEMP_ONLY("温度")
}

@Composable
fun DualMetricChart(
    modifier: Modifier = Modifier,
    powerPoints: List<Double> = emptyList(),
    tempPoints: List<Double> = emptyList(),
    sampleTimestamps: List<Long>? = null,
    sampleBatteryLevels: List<Int>? = null,
    totalDurationMillis: Long? = null,
    displayMode: ChartDisplayMode = ChartDisplayMode.COMBINED,
    powerLineColor: Color = MaterialTheme.colorScheme.primary,
    tempLineColor: Color = Color(0xFFFF9E44),
    customMaxPower: Double? = null,
    customMaxTemp: Double? = null,
    showScale: Boolean = true,
    showXAxis: Boolean = true
) {
    val showPower = (displayMode == ChartDisplayMode.COMBINED || displayMode == ChartDisplayMode.POWER_ONLY) && powerPoints.isNotEmpty()
    val showTemp = (displayMode == ChartDisplayMode.COMBINED || displayMode == ChartDisplayMode.TEMP_ONLY) && tempPoints.isNotEmpty()

    if (!showPower && !showTemp) return

    val pointsCount = max(powerPoints.size, tempPoints.size)

    // Interactive scrubber selection state
    var selectedIndex by remember(powerPoints.size, tempPoints.size) { mutableStateOf<Int?>(null) }

    // Power scale calculation
    val actualMaxPower = powerPoints.maxOrNull() ?: 0.0
    val maxPower = max(customMaxPower ?: actualMaxPower, 1.0)
    val midPower = maxPower / 2.0
    val minPower = 0.0
    val powerRange = maxPower - minPower

    // Temperature scale calculation
    val actualMinTemp = tempPoints.minOrNull() ?: 25.0
    val actualMaxTemp = tempPoints.maxOrNull() ?: 45.0
    val minTemp = floor(actualMinTemp - 2.0).coerceAtLeast(0.0)
    val maxTemp = ceil(max(customMaxTemp ?: actualMaxTemp, minTemp + 6.0))
    val midTemp = (maxTemp + minTemp) / 2.0
    val tempRange = max(maxTemp - minTemp, 1.0)

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    // Calculate total duration in seconds and minutes
    val totalDurationSec: Double = remember(sampleTimestamps, totalDurationMillis, pointsCount) {
        if (!sampleTimestamps.isNullOrEmpty() && sampleTimestamps.size >= 2) {
            val startT = sampleTimestamps.first()
            val endT = sampleTimestamps.last()
            max(1.0, (endT - startT) / 1000.0)
        } else if (totalDurationMillis != null && totalDurationMillis > 0L) {
            max(1.0, totalDurationMillis / 1000.0)
        } else {
            max(1.0, pointsCount * 5.0)
        }
    }
    val totalDurationMin: Double = totalDurationSec / 60.0

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Chart Canvas & X-Axis Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Chart Canvas Area with Gesture Detector
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
                    // Top line
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, topPadding),
                        end = Offset(width, topPadding),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    // Middle line
                    val midY = topPadding + usableHeight * 0.5f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, midY),
                        end = Offset(width, midY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    // Bottom baseline
                    val bottomY = topPadding + usableHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, bottomY),
                        end = Offset(width, bottomY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 2. Draw Temperature Curve (Background layer when in combined mode)
                    if (showTemp && tempPoints.size >= 2) {
                        drawCurveLayer(
                            points = tempPoints,
                            minValue = minTemp,
                            range = tempRange,
                            usableHeight = usableHeight,
                            width = width,
                            topPadding = topPadding,
                            bottomY = bottomY,
                            lineColor = tempLineColor,
                            fillAlphaTop = if (displayMode == ChartDisplayMode.COMBINED) 0.12f else 0.20f,
                            lineWidth = if (displayMode == ChartDisplayMode.COMBINED) 2.2.dp.toPx() else 2.5.dp.toPx()
                        )
                    }

                    // 3. Draw Power Curve (Foreground layer)
                    if (showPower && powerPoints.size >= 2) {
                        drawCurveLayer(
                            points = powerPoints,
                            minValue = minPower,
                            range = powerRange,
                            usableHeight = usableHeight,
                            width = width,
                            topPadding = topPadding,
                            bottomY = bottomY,
                            lineColor = powerLineColor,
                            fillAlphaTop = 0.22f,
                            lineWidth = 2.5.dp.toPx()
                        )
                    }

                    // 4. Draw Scrubber Indicator if user is touching / scrubbing
                    val currentSelection = selectedIndex
                    if (currentSelection != null && pointsCount > 1 && currentSelection in 0 until pointsCount) {
                        val stepX = width / (pointsCount - 1)
                        val scrubX = currentSelection * stepX

                        // Vertical dashed indicator line
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(scrubX, topPadding),
                            end = Offset(scrubX, bottomY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Highlight Power point on curve
                        if (showPower && currentSelection < powerPoints.size) {
                            val pVal = powerPoints[currentSelection]
                            val pNorm = ((pVal - minPower) / powerRange).toFloat().coerceIn(0f, 1f)
                            val pY = bottomY - (pNorm * usableHeight)
                            drawCircle(
                                color = powerLineColor.copy(alpha = 0.35f),
                                radius = 7.dp.toPx(),
                                center = Offset(scrubX, pY)
                            )
                            drawCircle(
                                color = powerLineColor,
                                radius = 4.dp.toPx(),
                                center = Offset(scrubX, pY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 1.5.dp.toPx(),
                                center = Offset(scrubX, pY)
                            )
                        }

                        // Highlight Temperature point on curve
                        if (showTemp && currentSelection < tempPoints.size) {
                            val tVal = tempPoints[currentSelection]
                            val tNorm = ((tVal - minTemp) / tempRange).toFloat().coerceIn(0f, 1f)
                            val tY = bottomY - (tNorm * usableHeight)
                            drawCircle(
                                color = tempLineColor.copy(alpha = 0.35f),
                                radius = 7.dp.toPx(),
                                center = Offset(scrubX, tY)
                            )
                            drawCircle(
                                color = tempLineColor,
                                radius = 4.dp.toPx(),
                                center = Offset(scrubX, tY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 1.5.dp.toPx(),
                                center = Offset(scrubX, tY)
                            )
                        }
                    }
                }

                // Interactive floating badge pill when scrubbed
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
                            String.format(Locale.US, "第 %.1f 分钟 (%s)", elapsedSec / 60.0, timeFmt)
                        }
                    } else {
                        val sec = if (pointsCount > 1) (sel.toDouble() / (pointsCount - 1)) * totalDurationSec else 0.0
                        if (totalDurationSec <= 120.0) {
                            String.format(Locale.US, "已运行 %d 秒", sec.roundToInt())
                        } else {
                            String.format(Locale.US, "已运行 %.1f 分钟", sec / 60.0)
                        }
                    }

                    val powerVal = if (sel < powerPoints.size) String.format(Locale.US, "%.1f W", powerPoints[sel]) else null
                    val tempVal = if (sel < tempPoints.size) String.format(Locale.US, "%.1f°C", tempPoints[sel]) else null
                    val levelVal = if (!sampleBatteryLevels.isNullOrEmpty() && sel < sampleBatteryLevels.size) "${sampleBatteryLevels[sel]}%" else null

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
                            if (powerVal != null) {
                                Text(
                                    text = powerVal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = powerLineColor
                                )
                            }
                            if (tempVal != null) {
                                Text(
                                    text = tempVal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tempLineColor
                                )
                            }
                            if (levelVal != null) {
                                Text(
                                    text = "电量 $levelVal",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF80C2FF)
                                )
                            }
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
                    .width(52.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 2.dp, bottom = if (showXAxis) 16.dp else 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                when (displayMode) {
                    ChartDisplayMode.COMBINED -> {
                        // Top label: Power & Temp
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.0fW", maxPower),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                color = powerLineColor,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f°C", maxTemp),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                color = tempLineColor,
                                textAlign = TextAlign.End
                            )
                        }

                        // Mid label
                        Text(
                            text = String.format(Locale.US, "%.0fW", midPower),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = axisTextColor,
                            textAlign = TextAlign.End
                        )

                        // Bottom label: 0W & MinTemp
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.0f°C", minTemp),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                color = tempLineColor,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = "0W",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                color = powerLineColor,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    ChartDisplayMode.POWER_ONLY -> {
                        Text(
                            text = String.format(Locale.US, "%.1fW", maxPower),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = powerLineColor,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fW", midPower),
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
                    ChartDisplayMode.TEMP_ONLY -> {
                        Text(
                            text = String.format(Locale.US, "%.1f°C", maxTemp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = tempLineColor,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f°C", midTemp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = axisTextColor,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f°C", minTemp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            color = tempLineColor,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCurveLayer(
    points: List<Double>,
    minValue: Double,
    range: Double,
    usableHeight: Float,
    width: Float,
    topPadding: Float,
    bottomY: Float,
    lineColor: Color,
    fillAlphaTop: Float,
    lineWidth: Float
) {
    if (points.size < 2) return

    val stepX = width / (points.size - 1)

    // Pre-calculate all coordinates
    val coords = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val x = i * stepX
        val norm = ((points[i] - minValue) / range).toFloat().coerceIn(0f, 1f)
        val y = bottomY - (norm * usableHeight)
        coords.add(Offset(x, y))
    }

    val linePath = Path()
    val fillPath = Path()

    linePath.moveTo(coords[0].x, coords[0].y)
    fillPath.moveTo(0f, bottomY)
    fillPath.lineTo(coords[0].x, coords[0].y)

    // Smooth Monotone Cubic Bezier Spline
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

    // 1. Draw gradient fill under curve
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = fillAlphaTop),
                lineColor.copy(alpha = fillAlphaTop * 0.35f),
                Color.Transparent
            ),
            startY = topPadding,
            endY = bottomY
        )
    )

    // 2. Draw soft ambient glow stroke for depth & polish
    drawPath(
        path = linePath,
        color = lineColor.copy(alpha = 0.22f),
        style = Stroke(
            width = lineWidth + 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    )

    // 3. Draw main curve stroke
    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(
            width = lineWidth,
            cap = StrokeCap.Round
        )
    )

    // 4. Draw last point halo & solid dot
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
}
