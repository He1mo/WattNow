package com.jerry.wattnow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jerry.wattnow.service.ChargingMonitorService
import com.jerry.wattnow.session.SessionManager
import com.jerry.wattnow.ui.SessionDetailScreen
import com.jerry.wattnow.ui.theme.WattNowTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var sessionManager: SessionManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission granted or denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        batteryMonitor = BatteryMonitor(this)
        sessionManager = SessionManager(this)

        requestNotificationPermissionIfNeeded()

        // Ensure resident service is running silently in background
        ChargingMonitorService.startService(this)

        // Observe battery states for SessionManager
        lifecycleScope.launch {
            batteryMonitor.batteryState.collectLatest { state ->
                sessionManager.onBatteryStateChanged(state, lifecycleScope)
            }
        }

        setContent {
            WattNowTheme {
                val state by batteryMonitor.batteryState.collectAsState()
                val historySessions by sessionManager.getAllCompletedSessions().collectAsState(initial = emptyList())

                // 30 seconds rolling buffer (60 points at 500ms)
                val recentPowerPoints = remember { mutableStateListOf<Double>() }
                val recentTempPoints = remember { mutableStateListOf<Double>() }
                val currentPower = state.powerW ?: 0.0
                val currentTemp = state.temperatureC ?: (state.thermalState.batteryTempC ?: 0.0)

                androidx.compose.runtime.LaunchedEffect(state.powerW, state.temperatureC) {
                    recentPowerPoints.add(currentPower)
                    if (recentPowerPoints.size > 60) {
                        recentPowerPoints.removeAt(0)
                    }
                    recentTempPoints.add(currentTemp)
                    if (recentTempPoints.size > 60) {
                        recentTempPoints.removeAt(0)
                    }
                }

                var selectedSessionId by remember { mutableStateOf<Long?>(null) }

                if (selectedSessionId != null) {
                    BackHandler {
                        selectedSessionId = null
                    }
                    SessionDetailScreen(
                        sessionId = selectedSessionId!!,
                        sessionManager = sessionManager,
                        onBack = { selectedSessionId = null }
                    )
                } else {
                    MainScreen(
                        batteryState = state,
                        recentPowerPoints = recentPowerPoints,
                        recentTempPoints = recentTempPoints,
                        historySessions = historySessions,
                        onSessionClick = { id -> selectedSessionId = id }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryMonitor.startMonitoring(lifecycleScope)
    }

    override fun onPause() {
        super.onPause()
        batteryMonitor.stopMonitoring()
    }
}