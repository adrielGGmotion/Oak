package com.oak.app.sandbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.oak.app.shared.R

internal const val SANDBOX_SETUP_CHANNEL_ID = "oak_sandbox_setup_channel"

class SandboxSetupService : Service() {

    companion object {
        const val NOTIFICATION_ID = 9003
        const val EXTRA_DETAIL = "detail"
    }

    private var currentDetail: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra(EXTRA_DETAIL) == true) {
            currentDetail = intent.getStringExtra(EXTRA_DETAIL)
        }
        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Don't stop — sandbox setup can take several minutes
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            SANDBOX_SETUP_CHANNEL_ID,
            getString(R.string.sandbox_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.sandbox_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = Notification.Builder(this, SANDBOX_SETUP_CHANNEL_ID)
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(currentDetail ?: getString(R.string.sandbox_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }
}
