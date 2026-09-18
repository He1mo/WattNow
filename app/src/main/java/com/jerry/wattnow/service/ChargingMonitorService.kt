package com.jerry.wattnow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jerry.wattnow.BatteryMonitor
import com.jerry.wattnow.BatteryState
import com.jerry.wattnow.MainActivity
import com.jerry.wattnow.R
import com.jerry.wattnow.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ChargingMonitorService : Service() {

    companion object {
        const val CHANNEL_CHARGING_ID = "charging_monitor_active_v6"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationManager: NotificationManager

    @Volatile
    private var currentState: BatteryState = BatteryState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        batteryMonitor = BatteryMonitor(this)
        sessionManager = SessionManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        currentState = batteryMonitor.getImmediateBatteryState()

        // Comply with Android Foreground Service contract
        startForeground(NOTIFICATION_ID, buildNotification(currentState))

        // If not charging, cancel notification and exit immediately
        if (!currentState.isCharging) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            stopSelf()
            return
        }

        batteryMonitor.startMonitoring(serviceScope)

        // Observe battery states
        serviceScope.launch {
            batteryMonitor.batteryState.collectLatest { state ->
                currentState = state
                sessionManager.onBatteryStateChanged(state, serviceScope)

                if (!state.isCharging) {
                    // Unplugged: save session, dismiss notification immediately and stop service
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationManager.cancel(NOTIFICATION_ID)
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }

        // Dedicated 1-second timer ticker for notification updates while charging
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (currentState.isCharging) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(currentState))
                } else {
                    break
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryMonitor.stopMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Clean up legacy and idle channels
            val legacyChannels = listOf(
                "charging_monitor_v2",
                "charging_monitor_channel",
                "charging_monitor_resident_v4",
                "charging_monitor_idle_v5",
                "charging_monitor_active_v5"
            )
            for (ch in legacyChannels) {
                try {
                    notificationManager.deleteNotificationChannel(ch)
                } catch (_: Exception) {}
            }

            // Active charging channel: DEFAULT importance (ensures display on lockscreens), silent sound & vibration
            val chargingChannel = NotificationChannel(
                CHANNEL_CHARGING_ID,
                "充电中实时监测",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "充电时在通知栏与锁屏展示实时功率、电流与电量"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(chargingChannel)
        }
    }

    private fun buildNotification(state: BatteryState): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val powerText = if (state.powerW != null) String.format(Locale.US, "%.1f W", state.powerW) else "-- W"
        val levelText = if (state.batteryLevel != null) "${state.batteryLevel}%" else "--%"
        val currentText = if (state.currentA != null) String.format(Locale.US, "%.2f A", state.currentA) else "-- A"
        val tempText = if (state.temperatureC != null) String.format(Locale.US, "%.1f°C", state.temperatureC) else "--°C"
        val voltageText = if (state.voltageV != null) String.format(Locale.US, "%.2f V", state.voltageV) else "-- V"

        val title = "$powerText · 电量 $levelText"
        val content = "电流 $currentText · 电压 $voltageText · 温度 $tempText"
        val subText = "WattNow 充电中"

        return NotificationCompat.Builder(this, CHANNEL_CHARGING_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (largeIcon != null) {
                    setLargeIcon(largeIcon)
                }
            }
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)
            .build()
    }
}