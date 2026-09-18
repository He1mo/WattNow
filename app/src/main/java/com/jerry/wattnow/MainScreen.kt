package com.jerry.wattnow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jerry.wattnow.data.ChargingSessionEntity
import com.jerry.wattnow.ui.CurvesTab
import com.jerry.wattnow.ui.HistoryTab
import com.jerry.wattnow.ui.MonitorTab
import com.jerry.wattnow.ui.components.CapsuleTabItem
import com.jerry.wattnow.ui.components.CurvesWaveIcon
import com.jerry.wattnow.ui.components.FloatingCapsuleBar
import com.jerry.wattnow.ui.components.HistoryClockIcon
import com.jerry.wattnow.ui.components.RealtimeBoltIcon

@Composable
fun MainScreen(
    batteryState: BatteryState,
    recentPowerPoints: List<Double>,
    recentTempPoints: List<Double>,
    historySessions: List<ChargingSessionEntity>,
    onSessionClick: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isDark = isSystemInDarkTheme()

    val capsuleTabs = remember {
        listOf(
            CapsuleTabItem("实时") { modifier, color -> RealtimeBoltIcon(modifier, color) },
            CapsuleTabItem("曲线") { modifier, color -> CurvesWaveIcon(modifier, color) },
            CapsuleTabItem("记录") { modifier, color -> HistoryClockIcon(modifier, color) }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tab Content: starts directly below system status bar, zero wasted title space
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                when (selectedTab) {
                    0 -> MonitorTab(
                        batteryState = batteryState,
                        recentPowerPoints = recentPowerPoints,
                        recentTempPoints = recentTempPoints
                    )
                    1 -> CurvesTab(
                        batteryState = batteryState,
                        recentPowerPoints = recentPowerPoints,
                        recentTempPoints = recentTempPoints
                    )
                    2 -> HistoryTab(
                        sessions = historySessions,
                        onSessionClick = onSessionClick
                    )
                }
            }

            // Modern Apple Floating Frosted Glass Capsule (No underline, compact, non-traditional)
            FloatingCapsuleBar(
                tabs = capsuleTabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                isDark = isDark,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}