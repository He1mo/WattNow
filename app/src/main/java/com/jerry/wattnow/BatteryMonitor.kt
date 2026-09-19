package com.jerry.wattnow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.jerry.wattnow.protocol.ChargingProtocolDetector
import kotlin.math.abs

class BatteryMonitor(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val thermalMonitor = ThermalMonitor(context)
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var pollJob: Job? = null
    private var peakPowerW: Double = 0.0

    @Volatile
    private var latestBatteryIntent: Intent? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                latestBatteryIntent = intent
            }
        }
    }

    fun getImmediateBatteryState(): BatteryState {
        updateBatteryState()
        return _batteryState.value
    }

    fun startMonitoring(scope: CoroutineScope) {
        thermalMonitor.initScanner(scope)
        if (pollJob?.isActive == true) return

        val initialIntent = context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (initialIntent != null) {
            latestBatteryIntent = initialIntent
        }

        pollJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                updateBatteryState()
                val isCharging = _batteryState.value.isCharging
                delay(if (isCharging) 500L else 2000L)
            }
        }
    }

    fun stopMonitoring() {
        pollJob?.cancel()
        pollJob = null
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    private fun getCurrentNowA(): Double? {
        val currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?: Int.MIN_VALUE

        val raw = if (currentNow != Int.MIN_VALUE && currentNow != 0) {
            abs(currentNow.toDouble())
        } else {
            val avg = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                ?: Int.MIN_VALUE
            if (avg != Int.MIN_VALUE && avg != 0) abs(avg.toDouble()) else null
        }

        if (raw == null) return null

        // Qualcomm Snapdragon Platform & Standard Android BatteryManager units:
        // BatteryManager returns current in microamperes (μA).
        // e.g. 295,000 μA = 0.295 A.
        // During trickle or low power charging, current can drop to ~30 mA = 30,000 μA.
        // If raw is in μA: dividing by 1,000,000.0 converts to Amperes.
        // Crucial bugfix: Previously `raw < 25,000` was divided by 1000 or treated as raw Amperes,
        // which erroneously converted 34 μA (or 34 mA) into 34.0 A, producing transient spikes of 150W-300W!
        // Qualcomm PMIC current_now is ALWAYS reported in microamperes (μA).
        val currentA = raw / 1_000_000.0

        // Sanity check: Phone battery single-cell charging current never exceeds 25A (even 120W dual cell is ~12A-24A)
        return if (currentA <= 25.0) currentA else null
    }

    private fun getVoltageV(intent: Intent?): Double? {
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        return if (rawVoltage > 0) {
            if (rawVoltage > 100) rawVoltage / 1000.0 else rawVoltage.toDouble()
        } else {
            null
        }
    }

    private fun updateBatteryState() {
        val batteryIntent = latestBatteryIntent ?: context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val statusExtra = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pluggedExtra = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isPlugged = pluggedExtra != 0

        val isCharging = isPlugged && (
                statusExtra == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusExtra == BatteryManager.BATTERY_STATUS_FULL ||
                statusExtra == BatteryManager.BATTERY_STATUS_UNKNOWN
        )

        val chargingStatus = when {
            statusExtra == BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargingStatus.CHARGING
            statusExtra == BatteryManager.BATTERY_STATUS_FULL -> BatteryChargingStatus.FULL
            statusExtra == BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargingStatus.DISCHARGING
            statusExtra == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                if (isPlugged) BatteryChargingStatus.CHARGING else BatteryChargingStatus.NOT_CHARGING
            }
            isPlugged -> BatteryChargingStatus.CHARGING
            else -> BatteryChargingStatus.NOT_CHARGING
        }

        val plugType = when (pluggedExtra) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            0 -> PlugType.NONE
            else -> PlugType.UNKNOWN
        }

        val voltageV = getVoltageV(batteryIntent)

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperatureC = if (rawTemp > 0) rawTemp / 10.0 else null

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            null
        }

        val currentA = if (isPlugged) getCurrentNowA() else null

        val calculatedPowerW: Double?
        if (isPlugged && isCharging) {
            if (voltageV != null && currentA != null) {
                val power = voltageV * currentA
                // Realistic battery power sanity check: 0.0W - 150.0W
                if (power in 0.0..150.0) {
                    calculatedPowerW = power
                    if (power > peakPowerW) {
                        peakPowerW = power
                    }
                } else {
                    calculatedPowerW = null
                }
            } else {
                calculatedPowerW = null
            }
        } else {
            calculatedPowerW = 0.0
            peakPowerW = 0.0
        }

        val thermalState = thermalMonitor.getThermalState(temperatureC)

        val maxNegotiatedPowerW = ChargingProtocolDetector.extractMaxNegotiatedPower(batteryIntent)
        val protocolInfo = ChargingProtocolDetector.detect(
            isCharging = isPlugged && isCharging,
            plugType = plugType,
            currentPowerW = calculatedPowerW,
            sessionPeakPowerW = if (peakPowerW > 0.0) peakPowerW else null,
            voltageV = voltageV,
            maxNegotiatedPowerW = maxNegotiatedPowerW
        )

        _batteryState.value = BatteryState(
            isCharging = isPlugged && isCharging,
            powerW = calculatedPowerW,
            peakPowerW = if (peakPowerW > 0.0) peakPowerW else null,
            currentA = currentA,
            voltageV = voltageV,
            batteryLevel = batteryLevel,
            temperatureC = temperatureC,
            chargingStatus = chargingStatus,
            plugType = plugType,
            thermalState = thermalState,
            chargingProtocol = protocolInfo
        )
    }
}
