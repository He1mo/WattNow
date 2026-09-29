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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.jerry.wattnow.protocol.ChargingProtocolDetector
import kotlin.math.max

data class ActiveSessionInfo(
    val sessionId: Long,
    val startTime: Long,
    val startBatteryLevel: Int,
    val currentBatteryLevel: Int,
    val deltaBatteryLevel: Int,
    val peakPowerW: Double,
    val chargerProtocol: String,
    val durationMillis: Long,
    val chargeRatePercentPerHour: Double? = null
)

class SessionManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val dbHelper = AppDatabaseHelper(context)
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeSessionId: Long? = null
    private var sampleJob: Job? = null
    private var lastSampleTime: Long = 0L
    private var lastSessionEndTime: Long = 0L
    private var hasInitialized = false

    @Volatile
    private var latestBatteryState: BatteryState = BatteryState()

    private val _activeSessionFlow = MutableStateFlow<ActiveSessionInfo?>(null)
    val activeSessionFlow: StateFlow<ActiveSessionInfo?> = _activeSessionFlow.asStateFlow()

    fun getAllCompletedSessions() = dbHelper.sessionsFlow

    suspend fun getSessionDetail(sessionId: Long): Pair<ChargingSessionEntity?, List<ChargingSampleEntity>> =
        withContext(Dispatchers.IO) {
            val session = dbHelper.getSessionById(sessionId)
            val samples = dbHelper.getSamplesForSession(sessionId)
            Pair(session, samples)
        }

    fun onBatteryStateChanged(newState: BatteryState) {
        val previousState = latestBatteryState
        latestBatteryState = newState

        managerScope.launch {
            handleStateTransition(previousState, newState)
        }
    }

    private suspend fun handleStateTransition(
        previous: BatteryState,
        current: BatteryState
    ) {
        if (!hasInitialized) {
            hasInitialized = true
            val uncompleted = dbHelper.getActiveSession()
            if (uncompleted != null) {
                if (current.isCharging) {
                    activeSessionId = uncompleted.id
                    lastSampleTime = System.currentTimeMillis()
                    val curLvl = current.batteryLevel ?: uncompleted.startBatteryLevel
                    val dur = System.currentTimeMillis() - uncompleted.startTime
                    _activeSessionFlow.value = ActiveSessionInfo(
                        sessionId = uncompleted.id,
                        startTime = uncompleted.startTime,
                        startBatteryLevel = uncompleted.startBatteryLevel,
                        currentBatteryLevel = curLvl,
                        deltaBatteryLevel = curLvl - uncompleted.startBatteryLevel,
                        peakPowerW = uncompleted.peakPowerW,
                        chargerProtocol = uncompleted.chargerProtocol,
                        durationMillis = dur,
                        chargeRatePercentPerHour = if (dur >= 60_000L) ((curLvl - uncompleted.startBatteryLevel).toDouble() / (dur / 3_600_000.0)) else null
                    )
                    startPeriodicSampling(uncompleted.id)
                } else {
                    closeInterruptedSession(uncompleted)
                }
            } else if (current.isCharging) {
                // If App opens while already charging and no uncompleted session exists
                startNewSession(current)
                return
            }
        }

        val now = System.currentTimeMillis()

        // State transition: Not charging -> Charging
        if (!previous.isCharging && current.isCharging && activeSessionId == null) {
            // Anti-glitch cooldown: Prevent ghost session immediately after unplugging (system broadcast latency/jitter)
            if (now - lastSessionEndTime < 3000L) {
                Log.w("WattNow", "Ignoring transient charging transition within 3s cooldown (${now - lastSessionEndTime}ms)")
                return
            }
            startNewSession(current)
        }
        // Self-healing Watchdog: If charging is active but sampleJob is unexpectedly dead, revive it immediately!
        else if (current.isCharging && activeSessionId != null) {
            if (sampleJob == null || sampleJob?.isActive != true) {
                Log.w("WattNow", "SessionManager watchdog: sampleJob was inactive while charging, reviving now for session $activeSessionId")
                startPeriodicSampling(activeSessionId!!)
            }
        }
        // State transition: Charging -> Not charging
        else if (previous.isCharging && !current.isCharging && activeSessionId != null) {
            endCurrentSession(current)
        }
    }

    private suspend fun startNewSession(state: BatteryState) {
        val now = System.currentTimeMillis()
        val level = state.batteryLevel ?: 0
        val session = ChargingSessionEntity(
            startTime = now,
            startBatteryLevel = level,
            startTemperatureC = state.temperatureC ?: 0.0,
            maxTemperatureC = state.temperatureC ?: 0.0,
            plugType = state.plugType.label,
            chargerProtocol = state.chargingProtocol.protocolName,
            isCompleted = false
        )
        val id = dbHelper.insertSession(session)
        activeSessionId = id
        lastSampleTime = now

        val initialPower = state.powerW ?: 0.0
        _activeSessionFlow.value = ActiveSessionInfo(
            sessionId = id,
            startTime = now,
            startBatteryLevel = level,
            currentBatteryLevel = level,
            deltaBatteryLevel = 0,
            peakPowerW = initialPower,
            chargerProtocol = state.chargingProtocol.protocolName,
            durationMillis = 0L,
            chargeRatePercentPerHour = null
        )

        if (initialPower > 0.0) {
            recordSample(id, now, state)
        }

        startPeriodicSampling(id)
    }

    private fun startPeriodicSampling(sessionId: Long) {
        sampleJob?.cancel()
        sampleJob = managerScope.launch {
            while (isActive && activeSessionId == sessionId) {
                delay(5_000) // Sample every 5 seconds for finer curve resolution
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

        val detectedSessionProtocol = ChargingProtocolDetector.detect(
            isCharging = true,
            plugType = state.plugType,
            currentPowerW = power,
            sessionPeakPowerW = newPeak,
            voltageV = state.voltageV,
            maxNegotiatedPowerW = state.chargingProtocol.maxNegotiatedPowerW
        )

        val candidateProtocol = if (detectedSessionProtocol.isSuperiorTo(state.chargingProtocol)) {
            detectedSessionProtocol
        } else {
            state.chargingProtocol
        }

        val upgradedProtocol = ChargingProtocolDetector.selectHigherProtocol(
            currentSession.chargerProtocol,
            candidateProtocol
        )

        dbHelper.updateSession(
            currentSession.copy(
                peakPowerW = newPeak,
                maxTemperatureC = newMaxTemp,
                estimatedEnergyWh = newEnergy,
                averagePowerW = avgPower,
                chargerProtocol = upgradedProtocol
            )
        )

        val duration = timestamp - currentSession.startTime
        val deltaLvl = level - currentSession.startBatteryLevel
        val ratePerHour = if (duration >= 60_000L) {
            (deltaLvl.toDouble() / (duration / 3_600_000.0))
        } else null
        _activeSessionFlow.value = ActiveSessionInfo(
            sessionId = sessionId,
            startTime = currentSession.startTime,
            startBatteryLevel = currentSession.startBatteryLevel,
            currentBatteryLevel = level,
            deltaBatteryLevel = deltaLvl,
            peakPowerW = newPeak,
            chargerProtocol = upgradedProtocol,
            durationMillis = duration,
            chargeRatePercentPerHour = ratePerHour
        )
    }

    private suspend fun endCurrentSession(state: BatteryState) = withContext(NonCancellable + Dispatchers.IO) {
        val sessionId = activeSessionId ?: return@withContext
        sampleJob?.cancel()
        sampleJob = null
        activeSessionId = null
        val now = System.currentTimeMillis()
        lastSessionEndTime = now
        _activeSessionFlow.value = null

        val currentSession = dbHelper.getSessionById(sessionId) ?: return@withContext
        val duration = now - currentSession.startTime
        val samples = dbHelper.getSamplesForSession(sessionId)

        // If session was brief (< 5 seconds) or had <= 1 sample, discard it as a transient connection glitch or ghost session
        if (duration < 5000L || samples.size <= 1) {
            Log.i("WattNow", "Discarding transient ghost session $sessionId (duration=${duration}ms, samples=${samples.size})")
            dbHelper.deleteSession(sessionId)
            return@withContext
        }

        val avgPower = dbHelper.getAvgPowerForSession(sessionId) ?: currentSession.averagePowerW
        val maxPower = dbHelper.getMaxPowerForSession(sessionId) ?: currentSession.peakPowerW
        val maxTemp = dbHelper.getMaxTemperatureForSession(sessionId) ?: currentSession.maxTemperatureC

        val detectedEndProtocol = ChargingProtocolDetector.detect(
            isCharging = true,
            plugType = state.plugType,
            currentPowerW = maxPower,
            sessionPeakPowerW = maxPower,
            voltageV = state.voltageV,
            maxNegotiatedPowerW = state.chargingProtocol.maxNegotiatedPowerW
        )

        val candidateEndProtocol = if (detectedEndProtocol.isSuperiorTo(state.chargingProtocol)) {
            detectedEndProtocol
        } else {
            state.chargingProtocol
        }

        val finalProtocol = ChargingProtocolDetector.selectHigherProtocol(
            currentSession.chargerProtocol,
            candidateEndProtocol
        )

        dbHelper.updateSession(
            currentSession.copy(
                endTime = now,
                endBatteryLevel = state.batteryLevel ?: currentSession.startBatteryLevel,
                endTemperatureC = state.temperatureC ?: currentSession.startTemperatureC,
                maxTemperatureC = maxTemp,
                peakPowerW = maxPower,
                averagePowerW = avgPower,
                durationMillis = duration,
                chargerProtocol = finalProtocol,
                isCompleted = true
            )
        )
    }

    private suspend fun closeInterruptedSession(session: ChargingSessionEntity) {
        _activeSessionFlow.value = null
        val samples = dbHelper.getSamplesForSession(session.id)
        val lastSample = samples.lastOrNull()
        val finishTime = lastSample?.timestamp ?: session.startTime
        val duration = finishTime - session.startTime

        if (duration < 5000L || samples.size <= 1) {
            Log.i("WattNow", "Discarding uncompleted ghost session ${session.id} (duration=${duration}ms, samples=${samples.size})")
            dbHelper.deleteSession(session.id)
            return
        }

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
