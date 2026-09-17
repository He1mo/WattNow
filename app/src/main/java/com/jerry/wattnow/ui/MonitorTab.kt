package com.jerry.wattnow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jerry.wattnow.BatteryState
import com.jerry.wattnow.PlugType
import com.jerry.wattnow.ui.components.SimplePowerChart
import java.util.Locale

@Composable
fun MonitorTab(
    batteryState: BatteryState,
    recentPowerPoints: List<Double>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.4f))

        // Center Real-time Power
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val powerText = when {
                !batteryState.isCharging -> "0.0"
                batteryState.powerW != null -> String.format(Locale.US, "%.1f", batteryState.powerW)
                else -> "--"
            }

            Text(
                text = powerText,
                fontSize = 84.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = if (batteryState.isCharging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                lineHeight = 90.sp
            )

            Text(
                text = "W",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Text(
                text = "$statusLabel • $peakText",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent 30s power curve card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val maxRecent = recentPowerPoints.maxOrNull() ?: 0.0
                val avgRecent = if (recentPowerPoints.isNotEmpty()) recentPowerPoints.average() else 0.0
                val curRecent = recentPowerPoints.lastOrNull() ?: (batteryState.powerW ?: 0.0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近 30 秒功率走势",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Total metric values on the chart
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "峰值 " + String.format(Locale.US, "%.1fW", maxRecent),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "均值 " + String.format(Locale.US, "%.1fW", avgRecent),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "当前 " + String.format(Locale.US, "%.1fW", curRecent),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    if (recentPowerPoints.size >= 2) {
                        SimplePowerChart(
                            points = recentPowerPoints,
                            lineColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "正在采样中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // 2x2 Metric Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentText = if (batteryState.currentA != null) {
                    String.format(Locale.US, "%.2f A", batteryState.currentA)
                } else {
                    "--"
                }
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "电流",
                    value = currentText
                )

                val voltageText = if (batteryState.voltageV != null) {
                    String.format(Locale.US, "%.2f V", batteryState.voltageV)
                } else {
                    "--"
                }
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "电压",
                    value = voltageText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val levelText = if (batteryState.batteryLevel != null) {
                    "${batteryState.batteryLevel}%"
                } else {
                    "--"
                }
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "电池电量",
                    value = levelText
                )

                val tempText = if (batteryState.temperatureC != null) {
                    String.format(Locale.US, "%.1f°C", batteryState.temperatureC)
                } else {
                    "--"
                }
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "电池温度",
                    value = tempText
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "由 Android 系统底层接口提供的电池侧数据",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}