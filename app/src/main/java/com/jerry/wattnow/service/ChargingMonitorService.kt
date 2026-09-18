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
        const val CHANNEL_CHARGING_ID = "charging_monitor_active_v5"
        const val CHANNEL_IDLE_ID = "charging_monitor_idle_v5"
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
        startForeground(NOTIFICATION_ID, buildNotification(currentState))

        batteryMonitor.startMonitoring(serviceScope)

        // Observe battery states
        serviceScope.launch {
            var prevCharging = currentState.isCharging
            var prevLevel = currentState.batteryLevel
            batteryMonitor.batteryState.collectLatest { state ->
                val chargingChanged = (prevCharging != state.isCharging)
                val levelChanged = (prevLevel != state.batteryLevel)
                prevCharging = state.isCharging
                prevLevel = state.batteryLevel
                currentState = state

                sessionManager.onBatteryStateChanged(state, serviceScope)

                // When charging status transitions, update resident notification immediately.
                // When in idle/discharging state, only update if the battery level changes (saving CPU).
                if (chargingChanged || (!state.isCharging && levelChanged)) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(currentState))
                }
            }
        }

        // Dedicated 1-second timer ticker for notification updates while charging
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (currentState.isCharging) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(currentState))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryMonitor.stopMonitoring()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Clean up legacy channels to prevent outdated settings/banners
            val legacyChannels = listOf(
                "charging_monitor_v2",
                "charging_monitor_channel",
                "charging_monitor_resident_v4"
            )
            for (ch in legacyChannels) {
                try {
                    notificationManager.deleteNotificationChannel(ch)
                } catch (_: Exception) {}
            }

            // 1. Active charging channel: LOW importance, PUBLIC lockscreen visibility
            val chargingChannel = NotificationChannel(
                CHANNEL_CHARGING_ID,
                "充电中实时监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "充电时在通知栏与锁屏展示实时功率、电流与电量"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(chargingChannel)

            // 2. Idle / Discharging channel: MIN importance, SECRET lockscreen visibility (hidden on lockscreen)
            val idleChannel = NotificationChannel(
                CHANNEL_IDLE_ID,
                "非充电静默待机",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "非充电时在后台静默保活，不在锁屏显示"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(idleChannel)
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

        val channelId = if (state.isCharging) CHANNEL_CHARGING_ID else CHANNEL_IDLE_ID

        if (state.isCharging) {
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

            return NotificationCompat.Builder(this, channelId)
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
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setShowWhen(false)
                .build()
        } else {
            // Idle / Discharging:
            // VISIBILITY_SECRET: Completely hidden on lockscreen
            // PRIORITY_MIN: Minimized and collapsed in notification shade
            val levelText = if (state.batteryLevel != null) "${state.batteryLevel}%" else "--%"
            val tempText = if (state.temperatureC != null) String.format(Locale.US, "%.1f°C", state.temperatureC) else "--°C"
            val voltageText = if (state.voltageV != null) String.format(Locale.US, "%.2f V", state.voltageV) else "-- V"

            val title = "WattNow 待机中 · 电量 $levelText"
            val content = "电压 $voltageText · 温度 $tempText"

            return NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setShowWhen(false)
                .build()
        }
    }
}