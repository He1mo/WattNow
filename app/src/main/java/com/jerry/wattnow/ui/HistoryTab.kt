package com.jerry.wattnow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jerry.wattnow.data.ChargingSessionEntity
import com.jerry.wattnow.ui.theme.appleFrostedGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryTab(
    sessions: List<ChargingSessionEntity>,
    onSessionClick: (Long) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Empty state icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BoltIcon(modifier = Modifier.size(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无充电记录",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "连接充电器后将自动采集并记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Overall Summary Card (16pt Apple Frosted Glass)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "充电历史统计总览",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "共 ${sessions.size} 次记录",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val totalEnergy = sessions.sumOf { it.estimatedEnergyWh }
                        val maxPeak = sessions.maxOfOrNull { it.peakPowerW } ?: 0.0
                        val overallAvg = if (sessions.isNotEmpty()) sessions.map { it.averagePowerW }.average() else 0.0

                        HistoryStatItem(label = "累计充入", value = String.format(Locale.US, "%.1f Wh", totalEnergy), isDark = isDark)
                        HistoryStatItem(label = "最高功率", value = String.format(Locale.US, "%.1f W", maxPeak), isDark = isDark)
                        HistoryStatItem(label = "综合均值", value = String.format(Locale.US, "%.1f W", overallAvg), isDark = isDark)
                    }
                }
            }
        }

        items(sessions, key = { it.id }) { session ->
            HistorySessionCard(session = session, isDark = isDark, onClick = { onSessionClick(session.id) })
        }
    }
}

@Composable
fun HistoryStatItem(label: String, value: String, isDark: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HistorySessionCard(
    session: ChargingSessionEntity,
    isDark: Boolean = true,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    val startDate = Date(session.startTime)
    val endDate = session.endTime?.let { Date(it) } ?: startDate

    val dateStr = dateFormat.format(startDate)
    val timeSpan = "${timeFormat.format(startDate)} – ${timeFormat.format(endDate)}"

    val endLvl = session.endBatteryLevel ?: session.startBatteryLevel
    val levelDelta = endLvl - session.startBatteryLevel
    val deltaStr = if (levelDelta >= 0) "+$levelDelta%" else "$levelDelta%"

    val durationMins = maxOf(1L, session.durationMillis / 60_000L)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: High-tech Charging Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                BoltIcon(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Content
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Date & Plug Type Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$dateStr · $timeSpan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    // Plug Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = session.plugType,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Battery delta & Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${session.startBatteryLevel}% → $endLvl%",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = deltaStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "$durationMins 分钟",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 3: Power, Energy & Peak Temperature
                val avgPowerText = String.format(Locale.US, "%.1fW", session.averagePowerW)
                val peakPowerText = String.format(Locale.US, "%.1fW", session.peakPowerW)
                val energyText = String.format(Locale.US, "%.1fWh", session.estimatedEnergyWh)
                val maxTempText = String.format(Locale.US, "%.1f°C", session.maxTemperatureC)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "峰值 $peakPowerText · 均值 $avgPowerText · $energyText",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Temperature badge with warm amber dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9E44))
                        )
                        Text(
                            text = maxTempText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            ),
                            color = Color(0xFFFF9E44)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, modern geometric lightning bolt icon drawn via Canvas
 */
@Composable
fun BoltIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.15f, h * 0.54f)
            lineTo(w * 0.48f, h * 0.54f)
            lineTo(w * 0.42f, h * 1f)
            lineTo(w * 0.85f, h * 0.46f)
            lineTo(w * 0.52f, h * 0.46f)
            close()
        }

        drawPath(path = path, color = color)
    }
}