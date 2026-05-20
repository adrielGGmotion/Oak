package com.oak.app

actual fun getExternalOakRoot(): String? = null

actual fun ensureExternalOakDirectories(): Boolean = false

actual fun writeOakLocationMarker() {}
