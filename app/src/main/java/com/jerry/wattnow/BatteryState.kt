package com.jerry.wattnow

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

data class BatteryState(
    val isCharging: Boolean = false,
    val powerW: Double? = null,
    val peakPowerW: Double? = null,
    val currentA: Double? = null,
    val voltageV: Double? = null,
    val batteryLevel: Int? = null,
    val temperatureC: Double? = null,
    val chargingStatus: BatteryChargingStatus = BatteryChargingStatus.UNKNOWN,
    val plugType: PlugType = PlugType.NONE
)