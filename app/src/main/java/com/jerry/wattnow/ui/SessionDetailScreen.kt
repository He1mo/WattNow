package com.jerry.wattnow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jerry.wattnow.data.ChargingSampleEntity
import com.jerry.wattnow.data.ChargingSessionEntity
import com.jerry.wattnow.session.SessionManager
import com.jerry.wattnow.ui.components.ChartDisplayMode
import com.jerry.wattnow.ui.components.DualMetricChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var samples by remember { mutableStateOf<List<ChargingSampleEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var chartMode by remember { mutableStateOf(ChartDisplayMode.COMBINED) }

    LaunchedEffect(sessionId) {
        val (s, sampleList) = sessionManager.getSessionDetail(sessionId)
        session = s
        samples = sampleList
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("充电记录详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val item = session
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到该条充电记录")
            }
            return@Scaffold
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val startStr = dateFormat.format(Date(item.startTime))
        val endStr = item.endTime?.let { dateFormat.format(Date(it)) } ?: "正在充电中"
        val durationMins = maxOf(1L, item.durationMillis / 60_000L)
        val endLvl = item.endBatteryLevel ?: item.startBatteryLevel
        val levelDelta = endLvl - item.startBatteryLevel
        val deltaStr = if (levelDelta >= 0) "+$levelDelta%" else "$levelDelta%"

        val startTemp = item.startTemperatureC
        val endTemp = item.endTemperatureC ?: item.maxTemperatureC
        val maxTemp = item.maxTemperatureC
        val tempDelta = maxTemp - startTemp
        val tempDeltaStr = if (tempDelta >= 0) "+${String.format(Locale.US, "%.1f°C", tempDelta)}" else String.format(Locale.US, "%.1f°C", tempDelta)

        val powerPoints = samples.map { it.powerW }
        val tempPoints = samples.map { it.temperatureC }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Power & Temperature Curve Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Title & Mode Switcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "全周期曲线",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )

                            // Mode Switcher Pills
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                ChartDisplayMode.entries.forEach { mode ->
                                    val isSelected = chartMode == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            )
                                            .clickable { chartMode = mode }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Right: Dynamic Key Metrics based on mode
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (chartMode) {
                                ChartDisplayMode.COMBINED -> {
                                    Text(
                                        text = "峰值 " + String.format(Locale.US, "%.1fW", item.peakPowerW),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "最高 " + String.format(Locale.US, "%.1f°C", maxTemp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = Color(0xFFFF9E44)
                                    )
                                }
                                ChartDisplayMode.POWER_ONLY -> {
                                    Text(
                                        text = "峰值 " + String.format(Locale.US, "%.1fW", item.peakPowerW),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "均值 " + String.format(Locale.US, "%.1fW", item.averagePowerW),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "能量 " + String.format(Locale.US, "%.1fWh", item.estimatedEnergyWh),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                ChartDisplayMode.TEMP_ONLY -> {
                                    Text(
                                        text = "最高 " + String.format(Locale.US, "%.1f°C", maxTemp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = Color(0xFFFF9E44)
                                    )
                                    Text(
                                        text = "温升 " + tempDeltaStr,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val hasPower = powerPoints.size >= 2
                        val hasTemp = tempPoints.size >= 2
                        val canRender = when (chartMode) {
                            ChartDisplayMode.COMBINED -> hasPower || hasTemp
                            ChartDisplayMode.POWER_ONLY -> hasPower
                            ChartDisplayMode.TEMP_ONLY -> hasTemp
                        }

                        if (canRender) {
                            DualMetricChart(
                                powerPoints = powerPoints,
                                tempPoints = tempPoints,
                                displayMode = chartMode,
                                customMaxPower = item.peakPowerW,
                                customMaxTemp = item.maxTemperatureC,
                                powerLineColor = MaterialTheme.colorScheme.primary,
                                tempLineColor = Color(0xFFFF9E44)
                            )
                        } else {
                            Text(
                                text = "正在累积采样点...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 52.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = startStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = endStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Overview Key Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow(label = "开始时间", value = startStr)
                    DetailRow(label = "结束时间", value = endStr)
                    DetailRow(label = "充电时长", value = "$durationMins 分钟")
                    DetailRow(label = "电量变化", value = "${item.startBatteryLevel}% → $endLvl% ($deltaStr)", indicatorColor = Color(0xFF80C2FF))
                    DetailRow(label = "平均功率", value = String.format(Locale.US, "%.1f W", item.averagePowerW), indicatorColor = MaterialTheme.colorScheme.primary)
                    DetailRow(label = "峰值功率", value = String.format(Locale.US, "%.1f W", item.peakPowerW), indicatorColor = MaterialTheme.colorScheme.primary)
                    DetailRow(label = "充入能量", value = String.format(Locale.US, "%.2f Wh", item.estimatedEnergyWh), indicatorColor = MaterialTheme.colorScheme.primary)
                    DetailRow(
                        label = "温度变化",
                        value = "${String.format(Locale.US, "%.1f°C", startTemp)} → ${String.format(Locale.US, "%.1f°C", endTemp)} (最高 ${String.format(Locale.US, "%.1f°C", maxTemp)}, $tempDeltaStr)",
                        indicatorColor = Color(0xFFFF9E44)
                    )
                    DetailRow(label = "充电方式", value = item.plugType)
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    indicatorColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}