package com.oak.app

import android.os.Build
import android.os.Environment
import java.io.File

actual fun getExternalOakRoot(): String? {
    if (!isExternalStorageAccessible()) return null
    return File(Environment.getExternalStorageDirectory(), "Oak").absolutePath
}

actual fun ensureExternalOakDirectories(): Boolean {
    val root = getExternalOakRoot() ?: return false
    File(root, "models").mkdirs()
    File(root, "sandbox-home").mkdirs()
    val ok = File(root, "models").exists() && File(root, "sandbox-home").exists()
    if (ok) writeOakLocationMarker()
    return ok
}

actual fun writeOakLocationMarker() {
    val root = getExternalOakRoot() ?: return
    try {
        File(root, ".oak-location").writeText("Oak data directory\n")
    } catch (_: Exception) { }
}

fun isExternalStorageAccessible(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    return Environment.isExternalStorageManager()
}
