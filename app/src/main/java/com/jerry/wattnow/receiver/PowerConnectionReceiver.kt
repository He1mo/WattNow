package com.jerry.wattnow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jerry.wattnow.service.ChargingMonitorService

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                ChargingMonitorService.startService(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancel(ChargingMonitorService.NOTIFICATION_ID)
                ChargingMonitorService.stopService(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val monitor = com.jerry.wattnow.BatteryMonitor(context)
                if (monitor.getImmediateBatteryState().isCharging) {
                    ChargingMonitorService.startService(context)
                }
            }
        }
    }
}
