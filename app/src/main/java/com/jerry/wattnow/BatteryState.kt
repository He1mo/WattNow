package com.jerry.wattnow

import com.jerry.wattnow.protocol.ChargingProtocolInfo

enum class PlugType(val label: String) {
    NONE("未连接"),
    AC("有线充电器"),
    USB("USB 端口"),
    WIRELESS("无线充电"),
    UNKNOWN("已连接")
}

enum class BatteryChargingStatus(val label: String) {
    CHARGING("正在充电"),
    DISCHARGING("放电中"),
    FULL("已充满"),
    NOT_CHARGING("未充电"),
    UNKNOWN("未知状态")
}

enum class ThermalStatusLevel(val code: Int, val label: String, val shortLabel: String) {
    NONE(0, "正常 (无限制)", "正常"),
    LIGHT(1, "轻微发热 (轻度调节)", "轻微"),
    MODERATE(2, "中度发热 (性能受限)", "中度"),
    SEVERE(3, "严重发热 (显著降频)", "严重"),
    CRITICAL(4, "极度过热 (即将保护)", "过热"),
    EMERGENCY(5, "紧急过热 (危险状态)", "紧急"),
    SHUTDOWN(6, "热保护 (即将关机)", "关机"),
    UNKNOWN(-1, "未知状态", "未知");

    companion object {
        fun fromCode(code: Int): ThermalStatusLevel {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

data class DeviceThermalState(
    val batteryTempC: Double? = null,
    val thermalStatus: ThermalStatusLevel = ThermalStatusLevel.NONE,
    val thermalHeadroom: Float? = null,
    val cpuTempC: Double? = null,
    val gpuTempC: Double? = null,
    val skinTempC: Double? = null
)

data class BatteryState(
    val isCharging: Boolean = false,
    val powerW: Double? = null,
    val peakPowerW: Double? = null,
    val currentA: Double? = null,
    val voltageV: Double? = null,
    val batteryLevel: Int? = null,
    val temperatureC: Double? = null,
    val chargingStatus: BatteryChargingStatus = BatteryChargingStatus.UNKNOWN,
    val plugType: PlugType = PlugType.NONE,
    val thermalState: DeviceThermalState = DeviceThermalState(),
    val chargingProtocol: ChargingProtocolInfo = ChargingProtocolInfo()
)