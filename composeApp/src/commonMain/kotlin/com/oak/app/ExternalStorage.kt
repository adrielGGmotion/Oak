package com.oak.app

expect fun getExternalOakRoot(): String?

expect fun ensureExternalOakDirectories(): Boolean

expect fun writeOakLocationMarker()
