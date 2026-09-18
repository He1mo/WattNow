package com.jerry.wattnow

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ThermalMonitor(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // Sysfs thermal nodes cache
    private val cpuNodePaths = mutableListOf<File>()
    private val gpuNodePaths = mutableListOf<File>()
    private val skinNodePaths = mutableListOf<File>()

    private val isScanning = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    @Volatile
    private var isSysfsSupported = false

    fun initScanner(scope: CoroutineScope) {
        if (isInitialized.get() || isScanning.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            try {
                scanThermalZones()
            } catch (_: Throwable) {
                isSysfsSupported = false
            } finally {
                isInitialized.set(true)
                isScanning.set(false)
            }
        }
    }

    private fun scanThermalZones() {
        val baseDirs = listOf(
            File("/sys/class/thermal"),
            File("/sys/devices/virtual/thermal")
        )

        val foundZoneDirs = mutableListOf<File>()
        for (baseDir in baseDirs) {
            if (!baseDir.exists()) continue
            val list = try {
                baseDir.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            } catch (_: Throwable) {
                null
            }

            if (!list.isNullOrEmpty()) {
                foundZoneDirs.addAll(list)
                break
            } else {
                // In case listFiles() is restricted by SELinux, attempt direct index access (0..70)
                var accessibleCount = 0
                for (i in 0..70) {
                    val candidate = File(baseDir, "thermal_zone$i")
                    if (candidate.exists() && File(candidate, "temp").canReadSafely()) {
                        foundZoneDirs.add(candidate)
                        accessibleCount++
                    }
                }
                if (accessibleCount > 0) break
            }
        }

        for (zoneDir in foundZoneDirs) {
            val typeFile = File(zoneDir, "type")
            val tempFile = File(zoneDir, "temp")

            val type = try {
                if (typeFile.exists()) typeFile.readText().trim().lowercase() else ""
            } catch (_: Throwable) {
                ""
            }

            if (type.isEmpty()) continue

            // Quick check if temp file is readable and returns valid temperature
            if (readSingleTemp(tempFile) == null) continue

            // Categorize into CPU / GPU / Skin
            when {
                isGpuZone(type) -> {
                    gpuNodePaths.add(tempFile)
                }
                isCpuZone(type) -> {
                    cpuNodePaths.add(tempFile)
                }
                isSkinZone(type) -> {
                    skinNodePaths.add(tempFile)
                }
            }
        }

        isSysfsSupported = cpuNodePaths.isNotEmpty() || gpuNodePaths.isNotEmpty() || skinNodePaths.isNotEmpty()
    }

    private fun File.canReadSafely(): Boolean {
        return try {
            canRead()
        } catch (_: Throwable) {
            false
        }
    }

    private fun isGpuZone(type: String): Boolean {
        return (type.contains("gpu") || type.contains("kgsl") || type.contains("soc_gpu"))
    }

    private fun isCpuZone(type: String): Boolean {
        if (isGpuZone(type)) return false
        return type.contains("cpu") ||
                type.contains("tsens") ||
                type.contains("krait") ||
                type.contains("ap-therm") ||
                type.contains("soc_max") ||
                type.startsWith("cluster")
    }

    private fun isSkinZone(type: String): Boolean {
        return type.contains("skin") ||
                type.contains("shell") ||
                type.contains("case") ||
                type.contains("xo-therm") ||
                type.contains("quiet-therm") ||
                type.contains("chg-skin")
    }

    private fun readSingleTemp(file: File): Double? {
        return try {
            val text = file.readText().trim()
            parseTemp(text)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseTemp(rawText: String): Double? {
        val raw = rawText.toDoubleOrNull() ?: return null
        val temp = when {
            // Millidegree Celsius, e.g. 42350 -> 42.35°C
            raw in 10000.0..120000.0 -> raw / 1000.0
            // Tenths of degree, e.g. 425 -> 42.5°C
            raw in 100.0..1000.0 -> raw / 10.0
            // Direct Celsius, e.g. 42.0 -> 42.0°C
            raw in 10.0..115.0 -> raw
            else -> null
        } ?: return null

        // Physical sanity check: phone chips operate within 15°C to 110°C
        return if (temp in 15.0..110.0) {
            (temp * 10.0).roundToInt() / 10.0
        } else {
            null
        }
    }

    fun getThermalState(batteryTempC: Double?): DeviceThermalState {
        // 1. Thermal Status (API 29+)
        val statusCode = try {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } catch (_: Throwable) {
            PowerManager.THERMAL_STATUS_NONE
        }
        val thermalStatus = ThermalStatusLevel.fromCode(statusCode)

        // 2. Thermal Headroom (API 30+)
        val thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val headroom = powerManager?.getThermalHeadroom(0) ?: Float.NaN
                if (!headroom.isNaN() && headroom >= 0.0f) {
                    (headroom * 100f).roundToInt() / 100f
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        // 3. Sysfs Temperatures (CPU / GPU / Skin)
        var cpuTemp: Double? = null
        var gpuTemp: Double? = null
        var skinTemp: Double? = null

        if (isSysfsSupported) {
            if (cpuNodePaths.isNotEmpty()) {
                val temps = cpuNodePaths.mapNotNull { readSingleTemp(it) }
                if (temps.isNotEmpty()) {
                    cpuTemp = temps.maxOrNull()
                }
            }

            if (gpuNodePaths.isNotEmpty()) {
                val temps = gpuNodePaths.mapNotNull { readSingleTemp(it) }
                if (temps.isNotEmpty()) {
                    gpuTemp = temps.maxOrNull()
                }
            }

            if (skinNodePaths.isNotEmpty()) {
                val temps = skinNodePaths.mapNotNull { readSingleTemp(it) }
                if (temps.isNotEmpty()) {
                    skinTemp = temps.maxOrNull()
                }
            }
        }

        return DeviceThermalState(
            batteryTempC = batteryTempC,
            thermalStatus = thermalStatus,
            thermalHeadroom = thermalHeadroom,
            cpuTempC = cpuTemp,
            gpuTempC = gpuTemp,
            skinTempC = skinTemp
        )
    }
}
