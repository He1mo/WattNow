package com.jerry.wattnow.protocol

import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import com.jerry.wattnow.PlugType
import java.util.Locale
import kotlin.math.max

enum class ProtocolCategory(val displayName: String, val shortBadge: String, val priority: Int) {
    XIAOMI_TURBO("小米澎湃秒充 (Mi Turbo Charge)", "⚡ 澎湃秒充", priority = 90),
    PD_PPS("USB-PD 3.0 / PPS 极速快充", "⚡ PD/PPS", priority = 80),
    WIRELESS_TURBO("小米无线秒充 (Mi Wireless Turbo)", "🌀 无线秒充", priority = 75),
    QC_FAST("高通 QC 4+ / 18W-22.5W 快充", "⚡ QC 4+ / PD", priority = 70),
    QC_STANDARD("高通 QC 3.0 / 9V 快速充电", "⚡ QC 3.0 (9V)", priority = 60),
    WIRELESS_QI("Qi 标准无线充电", "Qi 无线充", priority = 50),
    STANDARD_AC("DCP 标准有线充电 (10W 档)", "标充 10W", priority = 40),
    USB_BC("USB BC 1.2 (5V/1.5A)", "USB 快充", priority = 30),
    SLOW_USB("标准 USB 2.0 / PC 慢充", "USB 慢充", priority = 20),
    TRICKLE_AC("涓流充电 / 握手阶段", "涓流握手", priority = 10),
    UNKNOWN("未知充电协议", "未知协议", priority = 5),
    DISCHARGING("未连接充电器", "未连接", priority = 0)
}

data class ChargingProtocolInfo(
    val protocolName: String = "未连接",
    val shortBadge: String = "未连接",
    val category: ProtocolCategory = ProtocolCategory.DISCHARGING,
    val maxNegotiatedPowerW: Double? = null,
    val estimatedPeakW: Double = 0.0,
    val details: String = ""
) {
    val theoreticalPowerLabel: String?
        get() {
            maxNegotiatedPowerW?.takeIf { it > 0.0 }?.let { power ->
                return if (power % 1.0 < 0.05) {
                    "${power.toInt()}W"
                } else {
                    String.format(Locale.US, "%.1fW", power)
                }
            }

            return when (category) {
                ProtocolCategory.XIAOMI_TURBO -> when {
                    protocolName.contains("120W") -> "120W"
                    protocolName.contains("90W") -> "90W"
                    protocolName.contains("67W") -> "67W"
                    else -> "40W+"
                }
                ProtocolCategory.PD_PPS -> "25W+"
                ProtocolCategory.QC_FAST -> "18–22.5W"
                ProtocolCategory.QC_STANDARD -> "18W"
                ProtocolCategory.STANDARD_AC -> "10W"
                ProtocolCategory.SLOW_USB -> "2.5W"
                ProtocolCategory.USB_BC -> "7.5W"
                ProtocolCategory.WIRELESS_TURBO -> "15W+"
                ProtocolCategory.WIRELESS_QI -> "5–10W"
                ProtocolCategory.TRICKLE_AC,
                ProtocolCategory.DISCHARGING,
                ProtocolCategory.UNKNOWN -> null
            }
        }

    fun isSuperiorTo(other: ChargingProtocolInfo): Boolean {
        if (this.category.priority != other.category.priority) {
            return this.category.priority > other.category.priority
        }
        val thisPower = maxNegotiatedPowerW ?: estimatedPeakW
        val otherPower = other.maxNegotiatedPowerW ?: other.estimatedPeakW
        return thisPower >= otherPower
    }
}

object ChargingProtocolDetector {

    private val isXiaomiFamily: Boolean by lazy {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }

    private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
    private const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"

    fun extractMaxNegotiatedPower(intent: Intent?): Double? {
        if (intent == null) return null
        val maxCurrentUa = intent.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1)
        val maxVoltageUv = intent.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1)
        if (maxCurrentUa > 0 && maxVoltageUv > 0) {
            val currentA = maxCurrentUa / 1_000_000.0
            val voltageV = maxVoltageUv / 1_000_000.0
            val power = currentA * voltageV
            if (power in 1.0..250.0) return power
        }
        return null
    }

    fun detect(
        isCharging: Boolean,
        plugType: PlugType,
        currentPowerW: Double?,
        sessionPeakPowerW: Double?,
        voltageV: Double?,
        maxNegotiatedPowerW: Double?
    ): ChargingProtocolInfo {
        if (!isCharging || plugType == PlugType.NONE) {
            return ChargingProtocolInfo(
                protocolName = "未连接充电器",
                shortBadge = "未连接",
                category = ProtocolCategory.DISCHARGING,
                details = "电池处于放电或闲置状态"
            )
        }

        val effectivePeak = max(sessionPeakPowerW ?: 0.0, currentPowerW ?: 0.0)
        val hasHighVoltage = voltageV != null && voltageV > 7.5 // 9V/12V QC step-up

        when (plugType) {
            PlugType.WIRELESS -> {
                return if (effectivePeak >= 15.0 || (maxNegotiatedPowerW ?: 0.0) >= 15.0) {
                    ChargingProtocolInfo(
                        protocolName = if (isXiaomiFamily) "小米无线秒充 (Mi Wireless Turbo)" else "高速无线快充 (EPP 15W+)",
                        shortBadge = "🌀 无线秒充",
                        category = ProtocolCategory.WIRELESS_TURBO,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "磁吸/线圈大功率无线充电协议"
                    )
                } else {
                    ChargingProtocolInfo(
                        protocolName = "Qi 标准无线充电",
                        shortBadge = "Qi 无线充",
                        category = ProtocolCategory.WIRELESS_QI,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "通用 Qi 基础功率配置文件 (BPP 5W-10W)"
                    )
                }
            }

            PlugType.USB -> {
                return if (effectivePeak <= 3.0) {
                    ChargingProtocolInfo(
                        protocolName = "标准 USB 2.0 / PC 慢充",
                        shortBadge = "USB 慢充",
                        category = ProtocolCategory.SLOW_USB,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "电脑 USB 端口供电 (5V 0.5A 约 2.5W)"
                    )
                } else if (effectivePeak <= 8.0) {
                    ChargingProtocolInfo(
                        protocolName = "USB BC 1.2 快充规范",
                        shortBadge = "USB 快充",
                        category = ProtocolCategory.USB_BC,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "USB 专用充电端口模式 (5V 1.5A 约 7.5W)"
                    )
                } else {
                    ChargingProtocolInfo(
                        protocolName = "USB-PD Type-C 供电",
                        shortBadge = "Type-C 供电",
                        category = ProtocolCategory.PD_PPS,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "USB Type-C 端口供电"
                    )
                }
            }

            PlugType.AC, PlugType.UNKNOWN -> {
                // 1. Xiaomi / Redmi proprietary Turbo Charge detection:
                // Xiaomi phones lock standard PD/PPS at ~27W-30W. Anything >= 38W or negotiated >= 40W
                // is unmistakably Xiaomi Mi Turbo Charge (电荷泵 67W/90W/120W 私有协议).
                if (isXiaomiFamily && (effectivePeak >= 38.0 || (maxNegotiatedPowerW ?: 0.0) >= 40.0)) {
                    val specLabel = when {
                        effectivePeak >= 95.0 -> "120W 极速秒充"
                        effectivePeak >= 55.0 -> "90W 澎湃秒充"
                        effectivePeak >= 38.0 -> "67W 澎湃秒充"
                        else -> "澎湃秒充"
                    }
                    return ChargingProtocolInfo(
                        protocolName = "小米澎湃秒充 ($specLabel)",
                        shortBadge = "⚡ 澎湃秒充",
                        category = ProtocolCategory.XIAOMI_TURBO,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "小米私有电荷泵快充协议 · 峰值 ${String.format("%.1f", effectivePeak)}W"
                    )
                }

                // 2. High-speed USB-PD 3.0 / PPS (Programmable Power Supply): 22W - 38W
                if (effectivePeak >= 22.0 || (maxNegotiatedPowerW ?: 0.0) >= 25.0) {
                    return ChargingProtocolInfo(
                        protocolName = "USB-PD 3.0 / PPS 极速快充",
                        shortBadge = "⚡ PD/PPS",
                        category = ProtocolCategory.PD_PPS,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "USB-PD 3.0 可编程电源握手协议 (PPS)"
                    )
                }

                // 3. QC 4+ / 18W-22.5W Fast Charging: 14.5W - 22W
                if (effectivePeak >= 14.5 || (maxNegotiatedPowerW ?: 0.0) >= 18.0) {
                    return ChargingProtocolInfo(
                        protocolName = "高通 QC 4+ / 18W-22.5W 快充",
                        shortBadge = "⚡ QC 4+ / PD",
                        category = ProtocolCategory.QC_FAST,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "Qualcomm Quick Charge 4+ / USB-PD 双模兼容"
                    )
                }

                // 4. QC 3.0 / 9V Step-up Fast Charging: 9.5W - 14.5W or voltage > 7.5V
                if (effectivePeak >= 9.5 || hasHighVoltage) {
                    return ChargingProtocolInfo(
                        protocolName = "高通 QC 3.0 / 9V 快速充电",
                        shortBadge = "⚡ QC 3.0 (9V)",
                        category = ProtocolCategory.QC_STANDARD,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "Qualcomm Quick Charge 3.0 高压快充协议"
                    )
                }

                // 5. Standard DCP 5V/2A 10W charging: 4.5W - 9.5W
                if (effectivePeak >= 4.5) {
                    return ChargingProtocolInfo(
                        protocolName = "DCP 标准有线充电 (10W 档)",
                        shortBadge = "标充 10W",
                        category = ProtocolCategory.STANDARD_AC,
                        maxNegotiatedPowerW = maxNegotiatedPowerW,
                        estimatedPeakW = effectivePeak,
                        details = "USB DCP 标准 5V/2A 充电协议"
                    )
                }

                // 6. Low power / Trickle / Initial handshake stage: < 4.5W
                return ChargingProtocolInfo(
                    protocolName = "涓流充电 / 握手阶段",
                    shortBadge = "涓流握手",
                    category = ProtocolCategory.TRICKLE_AC,
                    maxNegotiatedPowerW = maxNegotiatedPowerW,
                    estimatedPeakW = effectivePeak,
                    details = "低功率供电或快充协议握手缓冲期"
                )
            }
            PlugType.NONE -> {
                return ChargingProtocolInfo(
                    protocolName = "未连接",
                    shortBadge = "未连接",
                    category = ProtocolCategory.DISCHARGING,
                    details = "未接入电源"
                )
            }
        }
    }

    fun getCategoryFromProtocolName(name: String?): ProtocolCategory {
        if (name.isNullOrBlank()) return ProtocolCategory.UNKNOWN
        return when {
            name.contains("秒充") && !name.contains("无线") -> ProtocolCategory.XIAOMI_TURBO
            name.contains("无线秒充") -> ProtocolCategory.WIRELESS_TURBO
            name.contains("PD") || name.contains("PPS") -> ProtocolCategory.PD_PPS
            name.contains("QC 4") -> ProtocolCategory.QC_FAST
            name.contains("QC 3") -> ProtocolCategory.QC_STANDARD
            name.contains("Qi") || name.contains("无线") -> ProtocolCategory.WIRELESS_QI
            name.contains("10W") || name.contains("DCP") -> ProtocolCategory.STANDARD_AC
            name.contains("BC 1.2") -> ProtocolCategory.USB_BC
            name.contains("USB 2.0") || name.contains("PC 慢充") -> ProtocolCategory.SLOW_USB
            name.contains("涓流") || name.contains("握手") -> ProtocolCategory.TRICKLE_AC
            name == "未连接" || name == "未连接充电器" -> ProtocolCategory.DISCHARGING
            else -> ProtocolCategory.UNKNOWN
        }
    }

    private fun extractWattageFromName(name: String): Double {
        val match = Regex("""(\d+(\.\d+)?)W""").find(name)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    fun selectHigherProtocol(existingName: String?, candidate: ChargingProtocolInfo): String {
        if (candidate.category == ProtocolCategory.DISCHARGING ||
            candidate.category == ProtocolCategory.UNKNOWN) {
            return existingName ?: candidate.protocolName
        }
        if (existingName.isNullOrBlank() ||
            existingName == "未连接" ||
            existingName == "未知充电协议" ||
            existingName == "涓流充电 / 握手阶段" ||
            existingName == "正在识别协议"
        ) {
            return candidate.protocolName
        }
        if (candidate.category == ProtocolCategory.TRICKLE_AC) {
            return existingName
        }

        val existingCat = getCategoryFromProtocolName(existingName)
        if (candidate.category.priority > existingCat.priority) {
            return candidate.protocolName
        } else if (candidate.category.priority == existingCat.priority) {
            val candidateWatts = candidate.maxNegotiatedPowerW ?: candidate.estimatedPeakW
            val existingWatts = extractWattageFromName(existingName)
            if (candidateWatts > existingWatts) {
                return candidate.protocolName
            }
        }
        return existingName
    }
}
