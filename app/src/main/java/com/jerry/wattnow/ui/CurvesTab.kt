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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jerry.wattnow.BatteryState
import com.jerry.wattnow.ThermalStatusLevel
import com.jerry.wattnow.ui.components.ChartDisplayMode
import com.jerry.wattnow.ui.components.DualMetricChart
import com.jerry.wattnow.ui.theme.appleFrostedGlass
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CurvesTab(
    batteryState: BatteryState,
    recentPowerPoints: List<Double>,
    recentTempPoints: List<Double> = emptyList()
) {
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()
    var chartMode by remember { mutableStateOf(ChartDisplayMode.COMBINED) }

    val maxRecentPower = remember(recentPowerPoints) { recentPowerPoints.maxOrNull() ?: 0.0 }
    val curRecentPower = remember(recentPowerPoints) { recentPowerPoints.lastOrNull() ?: 0.0 }
    val maxRecentTemp = remember(recentTempPoints) { recentTempPoints.maxOrNull() ?: 0.0 }
    val curRecentTemp = remember(recentTempPoints) { recentTempPoints.lastOrNull() ?: 0.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Dual Metric Curve Card (16pt Apple Squircle Frosted Glass)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Title and Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "功率与温度双曲线",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "时序连续贝塞尔平滑样条",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Mode Toggle Capsule
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ChartDisplayMode.values().forEach { mode ->
                            val isSelected = chartMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Color.White.copy(alpha = 0.18f) else Color.White
                                        } else Color.Transparent
                                    )
                                    .clickable { chartMode = mode }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chart Canvas Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val hasPower = recentPowerPoints.size >= 2
                    val hasTemp = recentTempPoints.size >= 2
                    val canRender = when (chartMode) {
                        ChartDisplayMode.COMBINED -> hasPower || hasTemp
                        ChartDisplayMode.POWER_ONLY -> hasPower
                        ChartDisplayMode.TEMP_ONLY -> hasTemp
                    }

                    if (canRender) {
                        DualMetricChart(
                            powerPoints = recentPowerPoints,
                            tempPoints = recentTempPoints,
                            displayMode = chartMode,
                            powerLineColor = MaterialTheme.colorScheme.primary,
                            tempLineColor = Color(0xFFFF9E44)
                        )
                    } else {
                        Text(
                            text = "正在采集采样数据...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Legend & Real-time Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chartMode != ChartDisplayMode.TEMP_ONLY) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "当前 " + String.format(Locale.US, "%.1fW", curRecentPower),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (chartMode != ChartDisplayMode.POWER_ONLY) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF9E44))
                                )
                                Text(
                                    text = "当前 " + String.format(Locale.US, "%.1f°C", curRecentTemp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFF9E44)
                                )
                            }
                        }
                    }

                    Text(
                        text = "峰值 " + String.format(Locale.US, "%.1fW", maxRecentPower) + " / " + String.format(Locale.US, "%.1f°C", maxRecentTemp),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Hardware Thermal Matrix (2x2 Grid, 16pt Apple Squircle Frosted Glass)
        val ts = batteryState.thermalState
        val batteryTemp = ts.batteryTempC ?: batteryState.temperatureC

        Text(
            text = "多维硬件温度感知矩阵",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CPU Core Card
            AppleThermalCard(
                modifier = Modifier.weight(1f),
                title = "CPU 核心温度",
                value = if (ts.cpuTempC != null) String.format(Locale.US, "%.1f°C", ts.cpuTempC) else "--",
                subtitle = "最高集群温度",
                accentColor = if (ts.cpuTempC != null && ts.cpuTempC > 45.0) Color(0xFFFF9E44) else MaterialTheme.colorScheme.onSurface,
                isDark = isDark
            )

            // GPU Core Card
            AppleThermalCard(
                modifier = Modifier.weight(1f),
                title = "GPU 核心温度",
                value = if (ts.gpuTempC != null) String.format(Locale.US, "%.1f°C", ts.gpuTempC) else "--",
                subtitle = "图形渲染核心",
                accentColor = if (ts.gpuTempC != null && ts.gpuTempC > 45.0) Color(0xFFFF9E44) else MaterialTheme.colorScheme.onSurface,
                isDark = isDark
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Battery Temp Card
            AppleThermalCard(
                modifier = Modifier.weight(1f),
                title = "电池电芯温度",
                value = if (batteryTemp != null) String.format(Locale.US, "%.1f°C", batteryTemp) else "--",
                subtitle = "电芯传感器",
                accentColor = if (batteryTemp != null && batteryTemp > 42.0) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                isDark = isDark
            )

            // Skin (Shell) Temp Card
            AppleThermalCard(
                modifier = Modifier.weight(1f),
                title = "机身外壳 (Skin)",
                value = if (ts.skinTempC != null) String.format(Locale.US, "%.1f°C", ts.skinTempC) else "--",
                subtitle = "外壳感知温度",
                accentColor = MaterialTheme.colorScheme.onSurface,
                isDark = isDark
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Thermal Headroom Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "系统热节流状态 (Thermal Headroom)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusText = when (ts.thermalStatus) {
                        ThermalStatusLevel.NONE -> "正常 · 无热节流触发"
                        ThermalStatusLevel.LIGHT -> "轻度温升"
                        ThermalStatusLevel.MODERATE -> "中度热节流 · 功率受限"
                        ThermalStatusLevel.SEVERE -> "严重热节流 · 启动保护"
                        ThermalStatusLevel.CRITICAL,
                        ThermalStatusLevel.EMERGENCY,
                        ThermalStatusLevel.SHUTDOWN -> "极端高温 · 即将停机"
                        ThermalStatusLevel.UNKNOWN -> "免 Root 原生感知"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                val headroomVal = ts.thermalHeadroom
                val headroomPct = if (headroomVal != null) (headroomVal * 100).roundToInt() else null
                val badgeColor = when {
                    headroomPct == null -> MaterialTheme.colorScheme.primary
                    headroomPct < 75 -> MaterialTheme.colorScheme.secondary
                    headroomPct < 90 -> Color(0xFFFF9E44)
                    else -> Color(0xFFFF5252)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (headroomPct != null) "余量 $headroomPct%" else "正常",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }

        // Bottom space to prevent overlap with floating capsule
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun AppleThermalCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    isDark: Boolean
) {
    Box(
        modifier = modifier
            .appleFrostedGlass(cornerRadius = 16.dp, isDark = isDark)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
