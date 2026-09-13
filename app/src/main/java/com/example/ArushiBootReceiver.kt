package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ArushiBootReceiver safely handles device restart / reboot.
 * In accordance with Android background limits, it restores preferences and notifies
 * or prepares service readiness without secretly recording audio.
 */
class ArushiBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        Log.i("ArushiBootReceiver", "Boot broadcast received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)
            val autoStartService = prefs.getBoolean("auto_start_service", false)

            if (autoStartService) {
                try {
                    Log.i("ArushiBootReceiver", "Restoring Arushi foreground voice service on boot...")
                    ArushiForegroundService.start(context)
                } catch (e: Exception) {
                    Log.w("ArushiBootReceiver", "Could not automatically start service on boot: ${e.message}")
                }
            }
        }
    }
}
