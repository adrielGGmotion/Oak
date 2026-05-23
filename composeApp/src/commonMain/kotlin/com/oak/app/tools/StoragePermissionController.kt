package com.oak.app.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

expect class StoragePermissionController() {
    val permissionRequested: StateFlow<Boolean>
    fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupStoragePermissionHandler(controller: StoragePermissionController)
