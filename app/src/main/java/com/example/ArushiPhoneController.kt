package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.util.Log

data class ControllerResult(
    val success: Boolean,
    val state: String,
    val message: String,
    val spokenReply: String? = null,
    val data: Map<String, Any>? = null
)

/**
 * ArushiPhoneController handles safe, direct native phone controls:
 * - App Launching (YouTube, WhatsApp, Instagram, Camera, etc.)
 * - Deep Links (YouTube Search, WhatsApp Search)
 * - Hardware features (Flashlight torch, Volume up/down)
 * - Navigation (Global Back, Home, Recents via Accessibility)
 */
object ArushiPhoneController {
    private const val TAG = "ArushiPhoneController"
    private var isTorchOn: Boolean = false

    fun openApp(context: Context, rawName: String): ControllerResult {
        val target = rawName.trim().lowercase()
            .replace(Regex("(ખોલો|ચાલુ કરો|ખોલ|ઓપન કરો|ખુલ્લો કરો|kholo|open karo|chalu karo|start karo)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(open|launch|start|chalu|khol)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        if (target.isBlank()) {
            return ControllerResult(false, "FAILURE", "Application name was not specified.", "Kripya app ka naam batayein.")
        }

        val pm = context.packageManager
        var intent: Intent? = null
        var resolvedName = target

        when {
            target.contains("youtube") || target.contains("યૂટ્યુબ") || target.contains("યુટ્યુબ") -> {
                intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                resolvedName = "YouTube"
            }
            target.contains("whatsapp") || target.contains("વોટ્સએપ") || target.contains("વોટ્સઅપ") -> {
                intent = pm.getLaunchIntentForPackage("com.whatsapp")
                    ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                resolvedName = "WhatsApp"
            }
            target.contains("instagram") || target.contains("ઇન્સ્ટાગ્રામ") || target.contains("ઇન્સ્ટા") -> {
                intent = pm.getLaunchIntentForPackage("com.instagram.android")
                resolvedName = "Instagram"
            }
            target.contains("chrome") || target.contains("ક્રોમ") -> {
                intent = pm.getLaunchIntentForPackage("com.android.chrome")
                resolvedName = "Chrome"
            }
            target == "google" || target.contains("google search") -> {
                intent = pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                    ?: Intent(Intent.ACTION_WEB_SEARCH)
                resolvedName = "Google"
            }
            target.contains("map") || target.contains("મેપ્સ") || target.contains("નકશા") -> {
                intent = pm.getLaunchIntentForPackage("com.google.android.apps.maps")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                resolvedName = "Google Maps"
            }
            target.contains("setting") || target.contains("સેટિંગ") -> {
                intent = Intent(Settings.ACTION_SETTINGS)
                resolvedName = "Settings"
            }
            target.contains("camera") || target.contains("કેમેરા") || target.contains("કેમેરો") -> {
                intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                resolvedName = "Camera"
            }
            target.contains("gallery") || target.contains("photos") || target.contains("ફોટો") -> {
                intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                resolvedName = "Gallery"
            }
            target.contains("clock") || target.contains("alarm") || target.contains("ઘડિયાળ") -> {
                intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                resolvedName = "Clock"
            }
            target.contains("calculator") || target.contains("ગણતરી") -> {
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                }
                resolvedName = "Calculator"
            }
            target.contains("contact") || target.contains("સંપર્ક") -> {
                intent = Intent(Intent.ACTION_DEFAULT, Uri.parse("content://contacts/people/"))
                resolvedName = "Contacts"
            }
            target.contains("phone") || target.contains("dialer") || target.contains("ફોન") -> {
                intent = Intent(Intent.ACTION_DIAL)
                resolvedName = "Phone"
            }
            target.contains("message") || target.contains("sms") || target.contains("મેસેજ") -> {
                val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
                if (defaultSms != null) {
                    intent = pm.getLaunchIntentForPackage(defaultSms)
                }
                if (intent == null) {
                    intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                    }
                }
                resolvedName = "Messages"
            }
        }

        // Generic search through installed launcher activities
        if (intent == null) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val appsList = pm.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in appsList) {
                val label = resolveInfo.loadLabel(pm).toString().lowercase()
                val pkgName = resolveInfo.activityInfo.packageName.lowercase()
                if (label == target || label.contains(target) || pkgName.contains(target)) {
                    intent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                    resolvedName = resolveInfo.loadLabel(pm).toString()
                    if (intent != null) break
                }
            }
        }

        return if (intent != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ControllerResult(
                    success = true,
                    state = "SUCCESS",
                    message = "$resolvedName opened successfully.",
                    spokenReply = "Ji Sir, $resolvedName open kar rahi hoon."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error launching $resolvedName", e)
                ControllerResult(
                    success = false,
                    state = "FAILURE",
                    message = "Could not launch $resolvedName: ${e.message}",
                    spokenReply = "Sir, $resolvedName open karne mein dikkat aayi."
                )
            }
        } else {
            ControllerResult(
                success = false,
                state = "APP_NOT_INSTALLED",
                message = "App '$rawName' is not installed or could not be found.",
                spokenReply = "Sir, $rawName phone mein installed nahi hai."
            )
        }
    }

    fun searchYouTube(context: Context, query: String): ControllerResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return ControllerResult(false, "FAILURE", "Search query is empty.", "Kripya YouTube par kya search karna hai batayein.")
        }

        val pm = context.packageManager
        return try {
            // First try direct YouTube search intent
            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (appIntent.resolveActivity(pm) != null) {
                context.startActivity(appIntent)
                ControllerResult(
                    success = true,
                    state = "SUCCESS",
                    message = "YouTube search opened for '$cleanQuery'.",
                    spokenReply = "Ji Sir, YouTube par $cleanQuery search kar rahi hoon."
                )
            } else {
                // Fallback to web search url
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery))
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                ControllerResult(
                    success = true,
                    state = "SUCCESS",
                    message = "YouTube web search opened for '$cleanQuery'.",
                    spokenReply = "Ji Sir, YouTube par $cleanQuery search kar rahi hoon."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed YouTube search", e)
            ControllerResult(
                false,
                "FAILURE",
                "Failed to search YouTube: ${e.message}",
                "Sir, YouTube search nahi ho paaya."
            )
        }
    }

    fun searchWhatsApp(context: Context, query: String): ControllerResult {
        val cleanQuery = query.trim()
        val pm = context.packageManager
        return try {
            val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                ControllerResult(
                    success = true,
                    state = "SUCCESS",
                    message = "WhatsApp opened for search '$cleanQuery'.",
                    spokenReply = "Ji Sir, WhatsApp open kar diya hai."
                )
            } else {
                ControllerResult(
                    false,
                    "APP_NOT_INSTALLED",
                    "WhatsApp is not installed.",
                    "Sir, WhatsApp installed nahi hai."
                )
            }
        } catch (e: Exception) {
            ControllerResult(false, "FAILURE", "Error opening WhatsApp: ${e.message}")
        }
    }

    fun setFlashlight(context: Context, enable: Boolean): ControllerResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                return ControllerResult(false, "UNSUPPORTED", "CameraManager is not available on this device.")
            }
            val cameraIds = cameraManager.cameraIdList
            var torchCameraId: String? = null
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    break
                }
            }
            if (torchCameraId == null && cameraIds.isNotEmpty()) {
                torchCameraId = cameraIds[0]
            }

            if (torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, enable)
                isTorchOn = enable
                val reply = if (enable) "Ji Sir, flashlight on kar di hai." else "Ji Sir, flashlight off kar di hai."
                ControllerResult(
                    success = true,
                    state = "SUCCESS",
                    message = "Flashlight turned ${if (enable) "ON" else "OFF"}.",
                    spokenReply = reply
                )
            } else {
                ControllerResult(
                    false,
                    "UNSUPPORTED",
                    "No camera with flashlight found.",
                    "Sir, device mein flashlight available nahi hai."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting flashlight", e)
            ControllerResult(
                false,
                "FAILURE",
                "Could not control flashlight: ${e.message}",
                "Sir, flashlight control nahi ho paayi."
            )
        }
    }

    fun toggleFlashlight(context: Context): ControllerResult {
        return setFlashlight(context, !isTorchOn)
    }

    fun adjustVolume(context: Context, raise: Boolean): ControllerResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                return ControllerResult(false, "FAILURE", "AudioManager not available.")
            }
            val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val reply = if (raise) "Ji Sir, volume badha diya hai." else "Ji Sir, volume kam kar diya hai."
            ControllerResult(
                success = true,
                state = "SUCCESS",
                message = "Volume ${if (raise) "increased" else "decreased"}.",
                spokenReply = reply
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting volume", e)
            ControllerResult(false, "FAILURE", "Volume adjustment failed: ${e.message}")
        }
    }

    fun globalBack(context: Context): ControllerResult {
        return if (ArushiAccessibilityService.isAccessibilityServiceEnabled(context)) {
            val ok = ArushiAccessibilityService.performBack()
            if (ok) {
                ControllerResult(true, "SUCCESS", "Navigated back globally.", "Ji Sir.")
            } else {
                ControllerResult(false, "FAILURE", "Failed to execute global back gesture.", "Sir, back action perform nahi ho saka.")
            }
        } else {
            ControllerResult(
                false,
                "SERVICE_DISABLED",
                "Accessibility Service is required for global phone control. Please enable Arushi in Accessibility Settings.",
                "Sir, global phone control ke liye Accessibility Service enable karni hogi."
            )
        }
    }

    fun globalHome(context: Context): ControllerResult {
        return if (ArushiAccessibilityService.isAccessibilityServiceEnabled(context)) {
            val ok = ArushiAccessibilityService.performHome()
            if (ok) {
                ControllerResult(true, "SUCCESS", "Navigated home.", "Ji Sir.")
            } else {
                fallbackHome(context)
            }
        } else {
            fallbackHome(context)
        }
    }

    private fun fallbackHome(context: Context): ControllerResult {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ControllerResult(true, "SUCCESS", "Navigated to home screen via standard intent.", "Ji Sir.")
        } catch (e: Exception) {
            ControllerResult(false, "FAILURE", "Cannot navigate to home: ${e.message}")
        }
    }

    fun globalRecents(context: Context): ControllerResult {
        return if (ArushiAccessibilityService.isAccessibilityServiceEnabled(context)) {
            val ok = ArushiAccessibilityService.performRecents()
            if (ok) {
                ControllerResult(true, "SUCCESS", "Opened recent apps.", "Ji Sir, recent apps open kar diye hain.")
            } else {
                ControllerResult(false, "FAILURE", "Failed to open recent apps.", "Sir, recent apps open nahi ho sake.")
            }
        } else {
            ControllerResult(
                false,
                "SERVICE_DISABLED",
                "Accessibility Service is required for recent apps.",
                "Sir, recent apps ke liye Accessibility Service enable karni hogi."
            )
        }
    }
}
