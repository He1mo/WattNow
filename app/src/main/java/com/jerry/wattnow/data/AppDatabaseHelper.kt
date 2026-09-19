package com.jerry.wattnow.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ChargingSessionEntity(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int? = null,
    val startTemperatureC: Double,
    val endTemperatureC: Double? = null,
    val maxTemperatureC: Double,
    val averagePowerW: Double = 0.0,
    val peakPowerW: Double = 0.0,
    val estimatedEnergyWh: Double = 0.0,
    val plugType: String,
    val chargerProtocol: String = "未知协议",
    val durationMillis: Long = 0L,
    val isCompleted: Boolean = false
)

data class ChargingSampleEntity(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val powerW: Double,
    val currentA: Double,
    val voltageV: Double,
    val batteryLevel: Int,
    val temperatureC: Double
)

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "wattnow.db"
        const val DATABASE_VERSION = 2
        private const val TAG = "WattNowDatabase"
    }

    private val _sessionsFlow = MutableStateFlow<List<ChargingSessionEntity>>(emptyList())
    val sessionsFlow: Flow<List<ChargingSessionEntity>> = _sessionsFlow.asStateFlow()

    init {
        cleanUpInvalidSessions()
        refreshSessions()
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS charging_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                startBatteryLevel INTEGER NOT NULL,
                endBatteryLevel INTEGER,
                startTemperatureC REAL NOT NULL,
                endTemperatureC REAL,
                maxTemperatureC REAL NOT NULL,
                averagePowerW REAL NOT NULL,
                peakPowerW REAL NOT NULL,
                estimatedEnergyWh REAL NOT NULL,
                plugType TEXT NOT NULL,
                chargerProtocol TEXT NOT NULL DEFAULT '未知协议',
                durationMillis INTEGER NOT NULL,
                isCompleted INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS charging_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                powerW REAL NOT NULL,
                currentA REAL NOT NULL,
                voltageV REAL NOT NULL,
                batteryLevel INTEGER NOT NULL,
                temperatureC REAL NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES charging_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Create index for fast sampling query by session and timestamp
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_charging_samples_session ON charging_samples(sessionId, timestamp)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_charging_sessions_completed ON charging_sessions(isCompleted, startTime)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Upgrading database schema from version $oldVersion to $newVersion")
        // Incremental, lossless migrations without dropping tables
        var currentV = oldVersion
        while (currentV < newVersion) {
            when (currentV) {
                1 -> {
                    try {
                        db.execSQL("ALTER TABLE charging_sessions ADD COLUMN chargerProtocol TEXT NOT NULL DEFAULT '未知协议'")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add chargerProtocol column", e)
                    }
                }
            }
            currentV++
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Prevent accidental table deletion on downgrade
        Log.w(TAG, "Downgrade requested from $oldVersion to $newVersion. Preserving data.")
    }

    private fun Cursor.getChargerProtocolSafe(): String {
        val idx = getColumnIndex("chargerProtocol")
        return if (idx >= 0 && !isNull(idx)) getString(idx) else "未知协议"
    }

    fun cleanUpInvalidSessions() {
        try {
            val db = writableDatabase
            db.execSQL(
                "DELETE FROM charging_samples WHERE sessionId IN (SELECT id FROM charging_sessions WHERE durationMillis < 5000 AND isCompleted = 1)"
            )
            db.execSQL(
                "DELETE FROM charging_sessions WHERE durationMillis < 5000 AND isCompleted = 1"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up invalid sessions", e)
        }
    }

    fun refreshSessions() {
        val list = mutableListOf<ChargingSessionEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM charging_sessions WHERE isCompleted = 1 AND durationMillis >= 5000 ORDER BY startTime DESC",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    ChargingSessionEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
                        endTime = if (c.isNull(c.getColumnIndexOrThrow("endTime"))) null else c.getLong(c.getColumnIndexOrThrow("endTime")),
                        startBatteryLevel = c.getInt(c.getColumnIndexOrThrow("startBatteryLevel")),
                        endBatteryLevel = if (c.isNull(c.getColumnIndexOrThrow("endBatteryLevel"))) null else c.getInt(c.getColumnIndexOrThrow("endBatteryLevel")),
                        startTemperatureC = c.getDouble(c.getColumnIndexOrThrow("startTemperatureC")),
                        endTemperatureC = if (c.isNull(c.getColumnIndexOrThrow("endTemperatureC"))) null else c.getDouble(c.getColumnIndexOrThrow("endTemperatureC")),
                        maxTemperatureC = c.getDouble(c.getColumnIndexOrThrow("maxTemperatureC")),
                        averagePowerW = c.getDouble(c.getColumnIndexOrThrow("averagePowerW")),
                        peakPowerW = c.getDouble(c.getColumnIndexOrThrow("peakPowerW")),
                        estimatedEnergyWh = c.getDouble(c.getColumnIndexOrThrow("estimatedEnergyWh")),
                        plugType = c.getString(c.getColumnIndexOrThrow("plugType")),
                        chargerProtocol = c.getChargerProtocolSafe(),
                        durationMillis = c.getLong(c.getColumnIndexOrThrow("durationMillis")),
                        isCompleted = c.getInt(c.getColumnIndexOrThrow("isCompleted")) == 1
                    )
                )
            }
        }
        _sessionsFlow.value = list
    }

    suspend fun insertSession(session: ChargingSessionEntity): Long = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("startTime", session.startTime)
            put("endTime", session.endTime)
            put("startBatteryLevel", session.startBatteryLevel)
            put("endBatteryLevel", session.endBatteryLevel)
            put("startTemperatureC", session.startTemperatureC)
            put("endTemperatureC", session.endTemperatureC)
            put("maxTemperatureC", session.maxTemperatureC)
            put("averagePowerW", session.averagePowerW)
            put("peakPowerW", session.peakPowerW)
            put("estimatedEnergyWh", session.estimatedEnergyWh)
            put("plugType", session.plugType)
            put("chargerProtocol", session.chargerProtocol)
            put("durationMillis", session.durationMillis)
            put("isCompleted", if (session.isCompleted) 1 else 0)
        }
        val id = db.insert("charging_sessions", null, values)
        refreshSessions()
        id
    }

    suspend fun updateSession(session: ChargingSessionEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("startTime", session.startTime)
            put("endTime", session.endTime)
            put("startBatteryLevel", session.startBatteryLevel)
            put("endBatteryLevel", session.endBatteryLevel)
            put("startTemperatureC", session.startTemperatureC)
            put("endTemperatureC", session.endTemperatureC)
            put("maxTemperatureC", session.maxTemperatureC)
            put("averagePowerW", session.averagePowerW)
            put("peakPowerW", session.peakPowerW)
            put("estimatedEnergyWh", session.estimatedEnergyWh)
            put("plugType", session.plugType)
            put("chargerProtocol", session.chargerProtocol)
            put("durationMillis", session.durationMillis)
            put("isCompleted", if (session.isCompleted) 1 else 0)
        }
        db.update("charging_sessions", values, "id = ?", arrayOf(session.id.toString()))
        refreshSessions()
    }

    suspend fun getActiveSession(): ChargingSessionEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM charging_sessions WHERE isCompleted = 0 ORDER BY id DESC LIMIT 1",
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                ChargingSessionEntity(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
                    endTime = if (c.isNull(c.getColumnIndexOrThrow("endTime"))) null else c.getLong(c.getColumnIndexOrThrow("endTime")),
                    startBatteryLevel = c.getInt(c.getColumnIndexOrThrow("startBatteryLevel")),
                    endBatteryLevel = if (c.isNull(c.getColumnIndexOrThrow("endBatteryLevel"))) null else c.getInt(c.getColumnIndexOrThrow("endBatteryLevel")),
                    startTemperatureC = c.getDouble(c.getColumnIndexOrThrow("startTemperatureC")),
                    endTemperatureC = if (c.isNull(c.getColumnIndexOrThrow("endTemperatureC"))) null else c.getDouble(c.getColumnIndexOrThrow("endTemperatureC")),
                    maxTemperatureC = c.getDouble(c.getColumnIndexOrThrow("maxTemperatureC")),
                    averagePowerW = c.getDouble(c.getColumnIndexOrThrow("averagePowerW")),
                    peakPowerW = c.getDouble(c.getColumnIndexOrThrow("peakPowerW")),
                    estimatedEnergyWh = c.getDouble(c.getColumnIndexOrThrow("estimatedEnergyWh")),
                    plugType = c.getString(c.getColumnIndexOrThrow("plugType")),
                    chargerProtocol = c.getChargerProtocolSafe(),
                    durationMillis = c.getLong(c.getColumnIndexOrThrow("durationMillis")),
                    isCompleted = c.getInt(c.getColumnIndexOrThrow("isCompleted")) == 1
                )
            } else null
        }
    }

    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM charging_sessions WHERE id = ?",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                ChargingSessionEntity(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
                    endTime = if (c.isNull(c.getColumnIndexOrThrow("endTime"))) null else c.getLong(c.getColumnIndexOrThrow("endTime")),
                    startBatteryLevel = c.getInt(c.getColumnIndexOrThrow("startBatteryLevel")),
                    endBatteryLevel = if (c.isNull(c.getColumnIndexOrThrow("endBatteryLevel"))) null else c.getInt(c.getColumnIndexOrThrow("endBatteryLevel")),
                    startTemperatureC = c.getDouble(c.getColumnIndexOrThrow("startTemperatureC")),
                    endTemperatureC = if (c.isNull(c.getColumnIndexOrThrow("endTemperatureC"))) null else c.getDouble(c.getColumnIndexOrThrow("endTemperatureC")),
                    maxTemperatureC = c.getDouble(c.getColumnIndexOrThrow("maxTemperatureC")),
                    averagePowerW = c.getDouble(c.getColumnIndexOrThrow("averagePowerW")),
                    peakPowerW = c.getDouble(c.getColumnIndexOrThrow("peakPowerW")),
                    estimatedEnergyWh = c.getDouble(c.getColumnIndexOrThrow("estimatedEnergyWh")),
                    plugType = c.getString(c.getColumnIndexOrThrow("plugType")),
                    chargerProtocol = c.getChargerProtocolSafe(),
                    durationMillis = c.getLong(c.getColumnIndexOrThrow("durationMillis")),
                    isCompleted = c.getInt(c.getColumnIndexOrThrow("isCompleted")) == 1
                )
            } else null
        }
    }

    suspend fun insertSample(sample: ChargingSampleEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("sessionId", sample.sessionId)
            put("timestamp", sample.timestamp)
            put("powerW", sample.powerW)
            put("currentA", sample.currentA)
            put("voltageV", sample.voltageV)
            put("batteryLevel", sample.batteryLevel)
            put("temperatureC", sample.temperatureC)
        }
        db.insert("charging_samples", null, values)
    }

    suspend fun getSamplesForSession(sessionId: Long): List<ChargingSampleEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChargingSampleEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM charging_samples WHERE sessionId = ? ORDER BY timestamp ASC",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    ChargingSampleEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        powerW = c.getDouble(c.getColumnIndexOrThrow("powerW")),
                        currentA = c.getDouble(c.getColumnIndexOrThrow("currentA")),
                        voltageV = c.getDouble(c.getColumnIndexOrThrow("voltageV")),
                        batteryLevel = c.getInt(c.getColumnIndexOrThrow("batteryLevel")),
                        temperatureC = c.getDouble(c.getColumnIndexOrThrow("temperatureC"))
                    )
                )
            }
        }
        list
    }

    suspend fun getLastSampleForSession(sessionId: Long): ChargingSampleEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM charging_samples WHERE sessionId = ? ORDER BY timestamp DESC LIMIT 1",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                ChargingSampleEntity(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    powerW = c.getDouble(c.getColumnIndexOrThrow("powerW")),
                    currentA = c.getDouble(c.getColumnIndexOrThrow("currentA")),
                    voltageV = c.getDouble(c.getColumnIndexOrThrow("voltageV")),
                    batteryLevel = c.getInt(c.getColumnIndexOrThrow("batteryLevel")),
                    temperatureC = c.getDouble(c.getColumnIndexOrThrow("temperatureC"))
                )
            } else null
        }
    }

    suspend fun getAvgPowerForSession(sessionId: Long): Double? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT AVG(powerW) FROM charging_samples WHERE sessionId = ?",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
        }
    }

    suspend fun getMaxPowerForSession(sessionId: Long): Double? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX(powerW) FROM charging_samples WHERE sessionId = ?",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
        }
    }

    suspend fun getMaxTemperatureForSession(sessionId: Long): Double? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX(temperatureC) FROM charging_samples WHERE sessionId = ?",
            arrayOf(sessionId.toString())
        )
        cursor.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null
        }
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("charging_samples", "sessionId = ?", arrayOf(sessionId.toString()))
        db.delete("charging_sessions", "id = ?", arrayOf(sessionId.toString()))
        refreshSessions()
    }
}
