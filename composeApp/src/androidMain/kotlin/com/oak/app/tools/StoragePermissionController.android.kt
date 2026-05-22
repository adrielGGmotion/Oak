package com.oak.app.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

actual class StoragePermissionController actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    actual fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    actual suspend fun requestPermission(): Boolean {
        if (hasPermission()) return true

        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60.seconds) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    actual fun onPermissionResult(granted: Boolean) {
        permissionResultFlow.value = granted
    }
}

@Composable
actual fun SetupStoragePermissionHandler(controller: StoragePermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()
    val context = LocalContext.current

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        controller.onPermissionResult(Environment.isExternalStorageManager())
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        controller.onPermissionResult(granted)
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                manageStorageLauncher.launch(intent)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
