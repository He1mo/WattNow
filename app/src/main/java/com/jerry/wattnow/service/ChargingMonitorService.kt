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
        const val CHANNEL_ID = "charging_monitor_resident_v4"
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

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(BatteryState(isCharging = false)))

        batteryMonitor.startMonitoring(serviceScope)

        // Observe battery states
        serviceScope.launch {
            batteryMonitor.batteryState.collectLatest { state ->
                val prevCharging = currentState.isCharging
                currentState = state
                sessionManager.onBatteryStateChanged(state, serviceScope)
                // When state changes or charging status transitions, update resident notification immediately
                if (!state.isCharging || prevCharging != state.isCharging) {
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Clean up legacy high-importance channels to prevent heads-up banners
            try {
                notificationManager.deleteNotificationChannel("charging_monitor_v2")
                notificationManager.deleteNotificationChannel("charging_monitor_channel")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "实时电池监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在通知栏与锁屏常驻展示实时功率与电池状态"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
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

        val title: String
        val content: String
        val subText: String

        if (state.isCharging) {
            val powerText = if (state.powerW != null) String.format(Locale.US, "%.1f W", state.powerW) else "-- W"
            val levelText = if (state.batteryLevel != null) "${state.batteryLevel}%" else "--%"
            val currentText = if (state.currentA != null) String.format(Locale.US, "%.2f A", state.currentA) else "-- A"
            val tempText = if (state.temperatureC != null) String.format(Locale.US, "%.1f°C", state.temperatureC) else "--°C"
            val voltageText = if (state.voltageV != null) String.format(Locale.US, "%.2f V", state.voltageV) else "-- V"

            title = "$powerText · 电量 $levelText"
            content = "电流 $currentText · 电压 $voltageText · 温度 $tempText"
            subText = "WattNow 充电中"
        } else {
            val levelText = if (state.batteryLevel != null) "${state.batteryLevel}%" else "--%"
            val tempText = if (state.temperatureC != null) String.format(Locale.US, "%.1f°C", state.temperatureC) else "--°C"
            val voltageText = if (state.voltageV != null) String.format(Locale.US, "%.2f V", state.voltageV) else "-- V"

            title = "未在充电 · 电量 $levelText"
            content = "电压 $voltageText · 温度 $tempText · 常驻待机中"
            subText = "WattNow 监测就绪"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

        return builder.build()
    }
}