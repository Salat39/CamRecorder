package com.salat.preview.presentation.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal fun Context.hasCameraPermission() = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.CAMERA
) == PackageManager.PERMISSION_GRANTED

internal fun Context.openManageAllFilesAccessSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    val appSettingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        startActivity(appSettingsIntent)
    } catch (_: ActivityNotFoundException) {
        startActivity(fallbackIntent)
    }
}
