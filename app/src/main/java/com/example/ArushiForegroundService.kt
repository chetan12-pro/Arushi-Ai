package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * ArushiForegroundService enables background voice readiness for "Hello Arushi".
 * Uses legitimate Android Foreground Service with type FOREGROUND_SERVICE_MICROPHONE.
 */
class ArushiForegroundService : Service(), WakeWordListener {

    private var wakeWordEngine: WakeWordEngine? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Log.i(TAG, "ArushiForegroundService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ArushiForegroundService onStartCommand action: ${intent?.action}")

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ / 15 / 16 microphone foreground service type
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Initialize local wake word engine if not already active
        if (wakeWordEngine == null) {
            wakeWordEngine = AndroidSpeechWakeWordEngine(applicationContext)
            wakeWordEngine?.startListening(this)
        }

        isRunning = true
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ArushiForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arushi V3 Enterprise")
            .setContentText("Listening for 'Hello Arushi' • Background active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onWakeWordDetected(phrase: String) {
        Log.i(TAG, "Wake word detected in foreground service: $phrase")
        // Bring MainActivity to front if needed or notify active session
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("WAKE_TRIGGERED", true)
            putExtra("WAKE_PHRASE", phrase)
        }
        startActivity(intent)
    }

    override fun onFastCommandDetected(command: String, result: FastCommandResult) {
        Log.i(TAG, "Fast command executed directly in background: ${result.action} - ${result.details}")
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        Log.d(TAG, "Wake word listening state: $isListening")
    }

    override fun onError(error: String) {
        Log.w(TAG, "Wake word error: $error")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeWordEngine?.stopListening()
        wakeWordEngine = null
        isRunning = false
        instance = null
        Log.i(TAG, "ArushiForegroundService destroyed.")
    }

    companion object {
        private const val TAG = "ArushiService"
        const val CHANNEL_ID = "arushi_voice_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set

        private var instance: ArushiForegroundService? = null

        fun start(context: Context) {
            val intent = Intent(context, ArushiForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ArushiForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
