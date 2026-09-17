package com.jerry.wattnow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jerry.wattnow.service.ChargingMonitorService

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ChargingMonitorService.startService(context)
            }
        }
    }
}
