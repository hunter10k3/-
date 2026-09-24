package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionManager {

    /**
     * Checks if the app has sufficient access to scan device documents.
     * On Android 11+ (API 30+), checks MANAGE_APP_ALL_FILES_ACCESS_PERMISSION or SAF persisted folder.
     * On Android 10 and below, checks READ_EXTERNAL_STORAGE.
     */
    fun hasFullStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if user has either full storage access OR has granted SAF folder access.
     */
    fun hasAnyStorageAccess(context: Context): Boolean {
        if (hasFullStorageAccess(context)) return true
        // Check if any SAF tree permission is persisted
        return context.contentResolver.persistedUriPermissions.isNotEmpty()
    }

    /**
     * Returns Intent to request All Files Access on Android 11+
     */
    fun createManageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Fallback to general App Settings screen
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}
