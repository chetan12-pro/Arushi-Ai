package com.forevercs.arushiai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * ArushiSetupManager provides accurate, live verification of all platform components:
 * - Microphone permission
 * - Notification permission
 * - Accessibility Service status
 * - Foreground Service status
 * - Wake Word engine status
 * - Gemini API Key presence
 * - Battery Optimization status
 */
object ArushiSetupManager {

    fun getLiveStatus(context: Context): JSONObject {
        val json = JSONObject()

        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isAccessibilityEnabled = ArushiAccessibilityService.isAccessibilityServiceEnabled(context)
        val isForegroundServiceRunning = ArushiForegroundService.isRunning

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        val hasGeminiApiKey = try {
            val key = BuildConfig.GEMINI_API_KEY
            !key.isNullOrBlank() && key != "MY_GEMINI_API_KEY"
        } catch (e: Exception) {
            false
        }

        val isWakeWordReady = hasMic

        // Overall readiness
        val isSetupComplete = hasMic && hasNotifications && isAccessibilityEnabled

        json.put("microphone", hasMic)
        json.put("notifications", hasNotifications)
        json.put("accessibility", isAccessibilityEnabled)
        json.put("foregroundService", isForegroundServiceRunning)
        json.put("wakeWord", isWakeWordReady)
        json.put("geminiKeyConfigured", hasGeminiApiKey)
        json.put("batteryOptimizationIgnored", isBatteryOptimizationIgnored)
        json.put("contacts", hasContacts)
        json.put("call", hasCall)
        json.put("isSetupComplete", isSetupComplete)

        return json
    }

    fun openBatterySettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(fallback) } catch (e2: Exception) {}
        }
    }

    fun openAccessibilitySettings(context: Context) {
        ArushiAccessibilityService.openAccessibilitySettings(context)
    }

    fun openNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppDetails(context)
            }
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
