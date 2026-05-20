package com.oak.app.inference

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.oak.app.getExternalOakRoot
import org.koin.java.KoinJavaComponent.inject
import java.io.File

private val context: Context by inject(Context::class.java)

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

actual fun startDownloadNotificationService() {
    try {
        val intent = Intent(context, ModelDownloadService::class.java)
        ContextCompat.startForegroundService(context, intent)
    } catch (_: Exception) {
        // Service start may fail if app is in restricted state
    }
}

actual fun stopDownloadNotificationService() {
    try {
        context.stopService(Intent(context, ModelDownloadService::class.java))
    } catch (_: Exception) { }
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "oak_model_download_channel")
        val notification = builder
            .setContentTitle(context.getString(com.oak.app.shared.R.string.app_name))
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
        manager.notify(ModelDownloadService.NOTIFICATION_ID, notification)
    } catch (_: Exception) { }
}
