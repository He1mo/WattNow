package com.jerry.wattnow.session

import android.content.Context
import android.util.Log
import com.jerry.wattnow.BatteryState
import com.jerry.wattnow.data.AppDatabaseHelper
import com.jerry.wattnow.data.ChargingSampleEntity
import com.jerry.wattnow.data.ChargingSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class SessionManager(context: Context) {

    private val dbHelper = AppDatabaseHelper(context)

    private var activeSessionId: Long? = null
    private var sampleJob: Job? = null
    private var lastSampleTime: Long = 0L
    private var hasInitialized = false

    @Volatile
    private var latestBatteryState: BatteryState = BatteryState()

    fun getAllCompletedSessions() = dbHelper.sessionsFlow

    suspend fun getSessionDetail(sessionId: Long): Pair<ChargingSessionEntity?, List<ChargingSampleEntity>> =
        withContext(Dispatchers.IO) {
            val session = dbHelper.getSessionById(sessionId)
            val samples = dbHelper.getSamplesForSession(sessionId)
            Pair(session, samples)
        }

    fun onBatteryStateChanged(newState: BatteryState, scope: CoroutineScope) {
        val previousState = latestBatteryState
        latestBatteryState = newState

        scope.launch(Dispatchers.IO) {
            handleStateTransition(previousState, newState, scope)
        }
    }

    private suspend fun handleStateTransition(
        previous: BatteryState,
        current: BatteryState,
        scope: CoroutineScope
    ) {
        if (!hasInitialized) {
            hasInitialized = true
            val uncompleted = dbHelper.getActiveSession()
            if (uncompleted != null) {
                if (current.isCharging) {
                    activeSessionId = uncompleted.id
                    lastSampleTime = System.currentTimeMillis()
                    startPeriodicSampling(scope, uncompleted.id)
                } else {
                    closeInterruptedSession(uncompleted)
                }
            } else if (current.isCharging) {
                // If App opens while already charging and no uncompleted session exists
                startNewSession(current, scope)
                return
            }
        }

        // State transition: Not charging -> Charging
        if (!previous.isCharging && current.isCharging && activeSessionId == null) {
            startNewSession(current, scope)
        }
        // State transition: Charging -> Not charging
        else if (previous.isCharging && !current.isCharging && activeSessionId != null) {
            endCurrentSession(current)
        }
    }

    private suspend fun startNewSession(state: BatteryState, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        val session = ChargingSessionEntity(
            startTime = now,
            startBatteryLevel = state.batteryLevel ?: 0,
            startTemperatureC = state.temperatureC ?: 0.0,
            maxTemperatureC = state.temperatureC ?: 0.0,
            plugType = state.plugType.label,
            isCompleted = false
        )
        val id = dbHelper.insertSession(session)
        activeSessionId = id
        lastSampleTime = now

        val initialPower = state.powerW ?: 0.0
        if (initialPower > 0.0) {
            recordSample(id, now, state)
        }

        startPeriodicSampling(scope, id)
    }

    private fun startPeriodicSampling(scope: CoroutineScope, sessionId: Long) {
        sampleJob?.cancel()
        sampleJob = scope.launch(Dispatchers.IO) {
            while (isActive && activeSessionId == sessionId) {
                delay(10_000) // Sample every 10 seconds
                val state = latestBatteryState
                if (state.isCharging && activeSessionId == sessionId) {
                    val now = System.currentTimeMillis()
                    recordSample(sessionId, now, state)
                }
            }
        }
    }

    private suspend fun recordSample(sessionId: Long, timestamp: Long, state: BatteryState) {
        val power = state.powerW ?: 0.0
        val currentA = state.currentA ?: 0.0
        val voltageV = state.voltageV ?: 0.0
        val level = state.batteryLevel ?: 0
        val temp = state.temperatureC ?: 0.0

        val sample = ChargingSampleEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            powerW = power,
            currentA = currentA,
            voltageV = voltageV,
            batteryLevel = level,
            temperatureC = temp
        )
        dbHelper.insertSample(sample)

        val dtMillis = if (lastSampleTime > 0L) timestamp - lastSampleTime else 10_000L
        lastSampleTime = timestamp
        val dtHours = max(1.0, dtMillis.toDouble()) / 3_600_000.0
        val addedEnergyWh = power * dtHours

        val currentSession = dbHelper.getSessionById(sessionId) ?: return
        val newPeak = max(currentSession.peakPowerW, power)
        val newMaxTemp = max(currentSession.maxTemperatureC, temp)
        val newEnergy = currentSession.estimatedEnergyWh + addedEnergyWh
        val avgPower = dbHelper.getAvgPowerForSession(sessionId) ?: power

        dbHelper.updateSession(
            currentSession.copy(
                peakPowerW = newPeak,
                maxTemperatureC = newMaxTemp,
                estimatedEnergyWh = newEnergy,
                averagePowerW = avgPower
            )
        )
    }

    private suspend fun endCurrentSession(state: BatteryState) {
        val sessionId = activeSessionId ?: return
        sampleJob?.cancel()
        sampleJob = null
        activeSessionId = null

        val now = System.currentTimeMillis()
        val currentSession = dbHelper.getSessionById(sessionId) ?: return
        val duration = now - currentSession.startTime

        val avgPower = dbHelper.getAvgPowerForSession(sessionId) ?: currentSession.averagePowerW
        val maxPower = dbHelper.getMaxPowerForSession(sessionId) ?: currentSession.peakPowerW
        val maxTemp = dbHelper.getMaxTemperatureForSession(sessionId) ?: currentSession.maxTemperatureC

        dbHelper.updateSession(
            currentSession.copy(
                endTime = now,
                endBatteryLevel = state.batteryLevel ?: currentSession.startBatteryLevel,
                endTemperatureC = state.temperatureC ?: currentSession.startTemperatureC,
                maxTemperatureC = maxTemp,
                peakPowerW = maxPower,
                averagePowerW = avgPower,
                durationMillis = duration,
                isCompleted = true
            )
        )
    }

    private suspend fun closeInterruptedSession(session: ChargingSessionEntity) {
        val lastSample = dbHelper.getLastSampleForSession(session.id)
        val finishTime = lastSample?.timestamp ?: session.startTime
        val duration = finishTime - session.startTime

        val avgPower = dbHelper.getAvgPowerForSession(session.id) ?: session.averagePowerW
        val maxPower = dbHelper.getMaxPowerForSession(session.id) ?: session.peakPowerW
        val maxTemp = dbHelper.getMaxTemperatureForSession(session.id) ?: session.maxTemperatureC

        dbHelper.updateSession(
            session.copy(
                endTime = finishTime,
                endBatteryLevel = lastSample?.batteryLevel ?: session.startBatteryLevel,
                endTemperatureC = lastSample?.temperatureC ?: session.startTemperatureC,
                maxTemperatureC = maxTemp,
                peakPowerW = maxPower,
                averagePowerW = avgPower,
                durationMillis = duration,
                isCompleted = true
            )
        )
    }
}
