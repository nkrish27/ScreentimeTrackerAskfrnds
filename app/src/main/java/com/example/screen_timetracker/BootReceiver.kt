package com.example.screen_timetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    // This function fires automatically when the broadcast is heard
    override fun onReceive(context: Context, intent: Intent) {

        // Double-check we are actually hearing a boot event
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("ScreenTimeTracker", "Device powered on. Starting enforcer service...")

            val serviceIntent = Intent(context, ScreenTimeService::class.java)

            // Android 8.0+ requires foreground services to be started this specific way
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
