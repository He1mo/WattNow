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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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
    displayMode: ChartDisplayMode = ChartDisplayMode.COMBINED,
    powerLineColor: Color = MaterialTheme.colorScheme.primary,
    tempLineColor: Color = Color(0xFFFF9E44),
    customMaxPower: Double? = null,
    customMaxTemp: Double? = null,
    showScale: Boolean = true
) {
    val showPower = (displayMode == ChartDisplayMode.COMBINED || displayMode == ChartDisplayMode.POWER_ONLY) && powerPoints.isNotEmpty()
    val showTemp = (displayMode == ChartDisplayMode.COMBINED || displayMode == ChartDisplayMode.TEMP_ONLY) && tempPoints.isNotEmpty()

    if (!showPower && !showTemp) return

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

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chart Canvas Area
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
            }
        }

        // Y-Axis Scale Values
        if (showScale) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
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
