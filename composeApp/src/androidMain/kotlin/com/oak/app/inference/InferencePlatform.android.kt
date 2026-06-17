package com.oak.app.inference

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.oak.app.getExternalOakRoot
import org.koin.java.KoinJavaComponent.inject
import java.io.File

private val context: Context by inject(Context::class.java)
private var downloadWakeLock: PowerManager.WakeLock? = null
private var completionNotificationId = 10000

actual fun getModelStorageDirectory(): String {
    val externalRoot = getExternalOakRoot()
    if (externalRoot != null) {
        val dir = File(externalRoot, "models/litert_models")
        if (dir.exists() || dir.mkdirs()) return dir.absolutePath
    }
    return context.filesDir.absolutePath + "/litert_models"
}

actual fun getModelCacheDirectory(): String = context.cacheDir.absolutePath

private fun getMemoryInfo(): ActivityManager.MemoryInfo {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
}

actual fun getAvailableMemoryBytes(): Long = getMemoryInfo().availMem

actual fun getTotalMemoryBytes(): Long = getMemoryInfo().totalMem

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    java.io.File(path).mkdirs()
    return StatFs(path).availableBytes
}

actual fun startDownloadNotificationService(modelName: String) {
    try {
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, modelName)
        }
        ContextCompat.startForegroundService(context, intent)
        acquireDownloadWakeLock()
    } catch (_: Exception) {
        // Service start may fail if app is in restricted state
    }
}

actual fun stopDownloadNotificationService() {
    try {
        context.stopService(Intent(context, ModelDownloadService::class.java))
    } catch (_: Exception) { }
    releaseDownloadWakeLock()
}

private fun acquireDownloadWakeLock() {
    try {
        // Release any previous lock before acquiring a new one
        downloadWakeLock?.let {
            runCatching { it.release() }
            downloadWakeLock = null
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Oak:ModelDownloadWakeLock",
        )
        lock.acquire()
        downloadWakeLock = lock
    } catch (_: Exception) { }
}

private fun releaseDownloadWakeLock() {
    try {
        downloadWakeLock?.release()
    } catch (_: Exception) { }
    downloadWakeLock = null
}

actual fun updateDownloadNotificationProgress(percent: Int, modelName: String) {
    try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, MODEL_DOWNLOAD_CHANNEL_ID)
        val notification = builder
            .setContentTitle(modelName)
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
        manager.notify(ModelDownloadService.NOTIFICATION_ID, notification)
    } catch (_: Exception) { }
}

actual fun postDownloadCompleteNotification(title: String, text: String) {
    try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, MODEL_DOWNLOAD_CHANNEL_ID)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(completionNotificationId++, notification)
    } catch (_: Exception) { }
}
