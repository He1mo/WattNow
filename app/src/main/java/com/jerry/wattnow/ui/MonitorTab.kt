package com.jerry.wattnow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jerry.wattnow.BatteryState
import com.jerry.wattnow.PlugType
import com.jerry.wattnow.ThermalStatusLevel
import com.jerry.wattnow.ui.components.ChartDisplayMode
import com.jerry.wattnow.ui.components.DualMetricChart
import com.jerry.wattnow.ui.components.SimplePowerChart
import com.jerry.wattnow.ui.theme.appleFrostedGlass
import java.util.Locale
import kotlin.math.roundToInt

data class ThermalMetricItem(
    val title: String,
    val value: String,
    val subtitle: String? = null,
    val highlightColor: Color? = null
)

@Composable
fun MonitorTab(
    batteryState: BatteryState,
    recentPowerPoints: List<Double>,
    recentTempPoints: List<Double> = emptyList()
) {
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Center Real-time Power Hero (Content-First, No Redundant App Title)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val powerText = when {
                !batteryState.isCharging -> "0.0"
                batteryState.powerW != null -> String.format(Locale.US, "%.1f", batteryState.powerW)
                else -> "--"
            }

            // Power Numeral
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = powerText,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = if (batteryState.isCharging) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    },
                    lineHeight = 76.sp
                )
                Text(
                    text = "W",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                )
            }

            // Status & Protocol Badge Pill
            val peakText = if (batteryState.peakPowerW != null) {
                "峰值 " + String.format(Locale.US, "%.1f W", batteryState.peakPowerW)
            } else {
                "峰值 --"
            }

            val statusLabel = if (batteryState.isCharging) {
                if (batteryState.plugType != PlugType.NONE && batteryState.plugType != PlugType.UNKNOWN) {
                    "${batteryState.chargingStatus.label} (${batteryState.plugType.label})"
                } else {
                    batteryState.chargingStatus.label
                }
            } else {
                batteryState.chargingStatus.label
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$statusLabel • $peakText",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2x2 Basic Electrical & Thermal Metrics Grid (16pt Apple Squircle Frosted Glass)
        val ts = batteryState.thermalState
        val headroomVal = ts.thermalHeadroom
        val headroomPct = if (headroomVal != null) (headroomVal * 100).roundToInt() else null

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: 电池电量
            Box(
                modifier = Modifier
                    .weight(1f)
                    .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前电量",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (batteryState.isCharging) "充电中" else "放电中",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (batteryState.isCharging) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (batteryState.batteryLevel != null) "${batteryState.batteryLevel}%" else "--",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.1f)
                                else Color.Black.copy(alpha = 0.08f)
                            )
                    ) {
                        val pctFraction = (batteryState.batteryLevel ?: 0) / 100f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pctFraction.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }
            }

            // Card 2: 母线电压
            val voltageText = if (batteryState.voltageV != null) {
                String.format(Locale.US, "%.2f V", batteryState.voltageV)
            } else {
                "--"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "母线电压 (Voltage)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = voltageText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "实时 PMIC 采样",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: 充电电流
            val currentText = if (batteryState.currentA != null) {
                String.format(Locale.US, "%.2f A", batteryState.currentA)
            } else {
                "--"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "充电电流 (Current)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "硬件硬滤波修正",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Card 4: 温控余量 (Headroom)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "温控健康 (Headroom)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (headroomPct != null) "$headroomPct%" else "正常",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = if (headroomPct != null && headroomPct > 85) Color(0xFFFF9E44) else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ts.thermalStatus.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Real-time Power Waveform Card (16pt Apple Frosted Glass)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "实时功率流动轨迹",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1.0s 平滑样条",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    if (recentPowerPoints.size >= 2) {
                        SimplePowerChart(
                            points = recentPowerPoints,
                            lineColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "正在采集高频数据...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Battery Hardware Details Card (16pt Apple Frosted Glass)
        ThermalDashboardCard(
            batteryState = batteryState
        )

        // Bottom space so floating capsule bar never blocks the bottom content
        Spacer(modifier = Modifier.height(96.dp))
    }
}


@Composable
fun ThermalDashboardCard(
    batteryState: BatteryState
) {
    val ts = batteryState.thermalState

    // Build metric items list
    val thermalItems = remember(ts, batteryState.temperatureC) {
        val list = mutableListOf<ThermalMetricItem>()

        // 1. Battery Temp (Default, Always visible)
        val batteryTemp = ts.batteryTempC ?: batteryState.temperatureC
        list.add(
            ThermalMetricItem(
                title = "电池温度",
                value = if (batteryTemp != null) String.format(Locale.US, "%.1f°C", batteryTemp) else "--",
                subtitle = "电池传感器"
            )
        )

        // 2. System Thermal Status (Default, Always visible)
        val statusColor = when (ts.thermalStatus) {
            ThermalStatusLevel.NONE -> Color(0xFF80D49E)
            ThermalStatusLevel.LIGHT -> Color(0xFF80C2FF)
            ThermalStatusLevel.MODERATE -> Color(0xFFFFB84D)
            ThermalStatusLevel.SEVERE -> Color(0xFFFF7043)
            ThermalStatusLevel.CRITICAL,
            ThermalStatusLevel.EMERGENCY,
            ThermalStatusLevel.SHUTDOWN -> Color(0xFFFF5252)
            ThermalStatusLevel.UNKNOWN -> Color.Gray
        }
        list.add(
            ThermalMetricItem(
                title = "热节流状态",
                value = ts.thermalStatus.shortLabel,
                subtitle = ts.thermalStatus.label,
                highlightColor = statusColor
            )
        )

        // 3. Thermal Headroom (Default, Always visible)
        val headroomVal = ts.thermalHeadroom
        val headroomText = if (headroomVal != null) {
            val pct = (headroomVal * 100).roundToInt()
            "$pct%"
        } else {
            "--"
        }
        val headroomSub = if (headroomVal != null) {
            when {
                headroomVal < 0.70f -> "热余量充裕"
                headroomVal < 0.90f -> "轻度温升"
                headroomVal < 1.00f -> "接近限频"
                else -> "已触发严重节流"
            }
        } else {
            "Android 11+ 原生"
        }
        list.add(
            ThermalMetricItem(
                title = "系统热余量",
                value = headroomText,
                subtitle = headroomSub
            )
        )

        // 4. Sysfs CPU Temp (Dynamic, hidden if null)
        if (ts.cpuTempC != null) {
            list.add(
                ThermalMetricItem(
                    title = "CPU 核心",
                    value = String.format(Locale.US, "%.1f°C", ts.cpuTempC),
                    subtitle = "最高核心温度"
                )
            )
        }

        // 5. Sysfs GPU Temp (Dynamic, hidden if null)
        if (ts.gpuTempC != null) {
            list.add(
                ThermalMetricItem(
                    title = "GPU 核心",
                    value = String.format(Locale.US, "%.1f°C", ts.gpuTempC),
                    subtitle = "图形处理器"
                )
            )
        }

        // 6. Sysfs Skin Temp (Dynamic, hidden if null)
        if (ts.skinTempC != null) {
            list.add(
                ThermalMetricItem(
                    title = "机身表面",
                    value = String.format(Locale.US, "%.1f°C", ts.skinTempC),
                    subtitle = "外壳温感 (Skin)"
                )
            )
        }

        list
    }

    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设备温度与热状态",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                ThermalStatusBadge(status = ts.thermalStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic 2-column Grid
            thermalItems.chunked(2).forEachIndexed { index, rowItems ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (item in rowItems) {
                        ThermalItemCard(
                            modifier = Modifier.weight(1f),
                            item = item
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalItemCard(
    modifier: Modifier = Modifier,
    item: ThermalMetricItem
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = item.highlightColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (item.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ThermalStatusBadge(status: ThermalStatusLevel) {
    val (color, label) = when (status) {
        ThermalStatusLevel.NONE -> Pair(Color(0xFF80D49E), "正常")
        ThermalStatusLevel.LIGHT -> Pair(Color(0xFF80C2FF), "轻微发热")
        ThermalStatusLevel.MODERATE -> Pair(Color(0xFFFFB84D), "中度温控")
        ThermalStatusLevel.SEVERE -> Pair(Color(0xFFFF7043), "严重降频")
        ThermalStatusLevel.CRITICAL -> Pair(Color(0xFFFF5252), "过热保护")
        ThermalStatusLevel.EMERGENCY -> Pair(Color(0xFFFF1744), "紧急状态")
        ThermalStatusLevel.SHUTDOWN -> Pair(Color(0xFFD500F9), "即将关机")
        ThermalStatusLevel.UNKNOWN -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "未知")
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = color
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}