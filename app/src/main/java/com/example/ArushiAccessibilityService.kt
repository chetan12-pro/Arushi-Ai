package com.example

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * ArushiAccessibilityService enables legitimate, user-authorized system phone controls:
 * - GLOBAL_ACTION_BACK
 * - GLOBAL_ACTION_HOME
 * - GLOBAL_ACTION_RECENTS
 */
class ArushiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Arushi Accessibility Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used for UI interaction if requested by user
    }

    override fun onInterrupt() {
        Log.w(TAG, "Arushi Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "Arushi Accessibility Service destroyed.")
    }

    companion object {
        private const val TAG = "ArushiAccessibility"
        private var instance: ArushiAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName = ComponentName(context, ArushiAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }

        fun performBack(): Boolean {
            val service = instance
            return if (service != null) {
                val res = service.performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_BACK) -> $res")
                res
            } else {
                Log.w(TAG, "AccessibilityService instance is null, cannot perform global back.")
                false
            }
        }

        fun performHome(): Boolean {
            val service = instance
            return if (service != null) {
                val res = service.performGlobalAction(GLOBAL_ACTION_HOME)
                Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_HOME) -> $res")
                res
            } else {
                Log.w(TAG, "AccessibilityService instance is null, cannot perform global home.")
                false
            }
        }

        fun performRecents(): Boolean {
            val service = instance
            return if (service != null) {
                val res = service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_RECENTS) -> $res")
                res
            } else {
                Log.w(TAG, "AccessibilityService instance is null, cannot perform global recents.")
                false
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
