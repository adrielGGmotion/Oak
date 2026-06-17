package com.oak.app.inference

expect fun getModelStorageDirectory(): String

expect fun getModelCacheDirectory(): String

expect fun getAvailableMemoryBytes(): Long

expect fun getTotalMemoryBytes(): Long

expect fun getAvailableDiskSpaceBytes(path: String): Long

expect fun startDownloadNotificationService(modelName: String)

expect fun stopDownloadNotificationService()

expect fun updateDownloadNotificationProgress(percent: Int, modelName: String)

expect fun postDownloadCompleteNotification(title: String, text: String)
