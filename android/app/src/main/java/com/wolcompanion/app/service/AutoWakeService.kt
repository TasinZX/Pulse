package com.wolcompanion.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wolcompanion.app.MainActivity
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.R
import com.wolcompanion.app.automation.AutomationRegistry
import com.wolcompanion.app.core.net.NetworkMonitor
import com.wolcompanion.app.core.net.WifiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Persistent foreground service powering Automatic Mode.
 *
 * It observes Wi-Fi transitions via [NetworkMonitor] and dispatches each one to
 * every registered [Automation]. Running as a foreground service is what lets
 * this survive Doze and app-swipe so the wake fires even when the app is closed.
 */
class AutoWakeService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var monitorJob: Job? = null
    private lateinit var monitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        monitor = NetworkMonitor(applicationContext)
        startForeground(NOTIF_ID, buildNotification("Watching for home Wi-Fi"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob == null) monitorJob = scope.launch { observe() }
        return START_STICKY // relaunch if the OS kills us
    }

    private suspend fun observe() {
        val repo = (application as PulseApp).settingsRepository
        val automations = AutomationRegistry.automations(onWake = { name ->
            notify("Waking $name", "You just got home — sending the wake signal.")
        })

        var previous: WifiState? = null
        monitor.wifiStates().collect { state ->
            val settings = repo.settings.first()
            updateNotification(state, settings.homeSsid)
            for (automation in automations) {
                runCatching { automation.onWifiEvent(previous, state, settings) }
            }
            previous = state
        }
    }

    private fun updateNotification(state: WifiState, homeSsid: String) {
        val text = when {
            state.connected && state.ssid.equals(homeSsid, ignoreCase = true) ->
                "Connected to home Wi-Fi"
            state.connected -> "On ${state.ssid ?: "Wi-Fi"} — waiting for home"
            else -> "Waiting for Wi-Fi"
        }
        nm().notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto-wake active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_pulse_bolt)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Small helper so a one-off event notification is distinct from the ongoing one. */
    private fun notify(title: String, text: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_pulse_bolt)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm().notify(EVENT_NOTIF_ID, n)
    }

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        monitorJob = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "auto_wake_channel"
        private const val NOTIF_ID = 1001
        private const val EVENT_NOTIF_ID = 1002

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Auto-wake",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Watches for home Wi-Fi to wake your PC" }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }

        fun start(context: Context) {
            createChannel(context)
            val intent = Intent(context, AutoWakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoWakeService::class.java))
        }
    }
}
