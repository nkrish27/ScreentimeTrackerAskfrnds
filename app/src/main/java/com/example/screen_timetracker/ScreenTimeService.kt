package com.example.screen_timetracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenTimeService : Service() {

    private val CHANNEL_ID = "ScreenTimeChannel"
    // 1. The Loop: We need a handler to run code every second
    private val handler = Handler(Looper.getMainLooper())
    private val CHECK_INTERVAL = 1000L // 1 second
    private var lastForegroundPackage: String = ""
    // WindowManager variables
    private var windowManager: android.view.WindowManager? = null
    private var overlayView: android.view.View? = null
    private var isOverlayShowing = false

    // 2. The Runnable: This is the code that runs every second
    private val checkUsageRunnable = object : Runnable {
        override fun run() {
            checkCurrentForegroundApp()
            // Schedule the next check
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    // Phase 4 Variables
    private var temporaryUnlockEndTime: Long = 0
    private var currentListener: com.google.firebase.firestore.ListenerRegistration? = null

    // Phase 5 Variables
    private val DAILY_LIMIT_MINUTES = 1

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // As soon as the service starts, we turn it into a Foreground service
        startForegroundService()

        // 3. START THE LOOP: Begin tracking immediately
        handler.post(checkUsageRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the loop when service is killed to prevent memory leaks
        handler.removeCallbacks(checkUsageRunnable)
    }

    // 4. The Logic: This asks Android "What is on screen right now?"
    private fun checkCurrentForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()

        // FIX 1: Look back 1 hour instead of 1 minute so the OS doesn't "forget" what app is open
        val startTime = endTime - 1000 * 60 * 60

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var currentApp = ""

        // Find the absolute latest app that was brought to the foreground
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.packageName
            }
        }

        if (currentApp.isNotEmpty()) {
            // Log only when it changes (so we don't spam your Logcat 1000 times a second)
            if (currentApp != lastForegroundPackage) {
                lastForegroundPackage = currentApp
                Log.d("ScreenTimeTracker", "DETECTED APP: $currentApp")
            }

            // FIX 2: Evaluate the block logic EVERY SECOND, even if the app hasn't changed
            // FIX 2: Evaluate the block logic based on DAILY QUOTA
// 1. Grab the list of blocked apps from storage
            val prefs = getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
            val blockedApps = prefs.getStringSet("blocked_apps", setOf()) ?: setOf()

            // FIX 2: Check if the current app is in our dynamic block list
            if (blockedApps.contains(currentApp)) {

                // Keep your existing daily quota logic exactly as it is!
                val totalTodayMs = getDailyUsage(currentApp)
                val totalTodayMins = totalTodayMs / (1000 * 60)

                Log.d("ScreenTimeTracker", "$currentApp used today: $totalTodayMins / $DAILY_LIMIT_MINUTES mins")

                if (totalTodayMins >= DAILY_LIMIT_MINUTES) {
                    if (System.currentTimeMillis() > temporaryUnlockEndTime) {
                        handler.post { showOverlay() }
                    } else {
                        handler.post { hideOverlay() }
                    }
                } else {
                    handler.post { hideOverlay() }
                }

            } else if (currentApp == "com.android.launcher" || currentApp == "com.example.screen_timetracker") {
                handler.post { hideOverlay() }
            }
        }
    }

    private fun startForegroundService() {
        val manager = getSystemService(NotificationManager::class.java)

        // Only create the channel if we are on Android 8.0 (Oreo) or newer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Time Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        // Build the Notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Time Monitor")
            .setContentText("Tracking usage in background...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true) // Makes it harder to swipe away
            .build()

        // Start the service (ID must be > 0)
        startForeground(1, notification)
    }


    private fun showOverlay() {
        if (isOverlayShowing) return // Don't draw it twice

        // Inflate the XML we just made
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
        overlayView = inflater.inflate(R.layout.blocker_overlay, null)

        // Set the parameters to make it un-closable and fullscreen
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        // This actually draws the UI on the screen
        windowManager?.addView(overlayView, params)
        isOverlayShowing = true

        // (Phase 3 Prep) Wire up the beg button so it doesn't crash later
        val begButton = overlayView?.findViewById<android.widget.Button>(R.id.btnBegFriend)
        begButton?.setOnClickListener {
            // Give instant UI feedback and prevent spam-clicking
            begButton.text = "Sending Request..."
            begButton.isEnabled = false

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // This is the "Document" we are pushing to the cloud
            val requestData = hashMapOf(
                "status" to "pending",
                "app_package" to lastForegroundPackage, // Dynamically sends 'com.pinterest' or whatever is blocked
                "time_requested_mins" to 10,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Push it to a collection named 'extension_requests'
            db.collection("extension_requests")
                .add(requestData)
                .addOnSuccessListener { documentReference ->
                    android.widget.Toast.makeText(this, "Sent! Waiting for mercy...", android.widget.Toast.LENGTH_SHORT).show()
                    begButton.text = "Waiting for friend..."

                    // PHASE 4: The Listener
                    // We attach a live listener to the exact document ID we just created
                    currentListener = db.collection("extension_requests").document(documentReference.id)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.w("Firebase", "Listen failed.", e)
                                return@addSnapshotListener
                            }

                            if (snapshot != null && snapshot.exists()) {
                                val status = snapshot.getString("status")
                                Log.d("Firebase", "Current status: $status")

                                if (status == "approved") {
                                    // 1. Give them 10 minutes of freedom (600,000 milliseconds)
                                    // For testing right now, let's use 1 minute (60,000 ms) so you don't have to wait 10 mins to test it again
                                    temporaryUnlockEndTime = System.currentTimeMillis() + 60000

                                    // 2. Hide the overlay
                                    handler.post {
                                        hideOverlay()
                                        android.widget.Toast.makeText(this@ScreenTimeService, "ACCESS GRANTED! You have 1 minute.", android.widget.Toast.LENGTH_LONG).show()
                                    }

                                    // 3. Kill the listener so it stops watching the database
                                    currentListener?.remove()
                                }
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Error adding document", e)
                    android.widget.Toast.makeText(this, "Failed to send.", android.widget.Toast.LENGTH_SHORT).show()
                    begButton.text = "Beg Friend for 10 Mins"
                    begButton.isEnabled = true
                }

        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing && overlayView != null) {
            windowManager?.removeView(overlayView)
            isOverlayShowing = false
        }
    }

    private fun getDailyUsage(targetPackage: String): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Find exactly when midnight was today
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        // Use queryEvents to bypass the OS batching delay
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var totalTimeMs = 0L
        var lastForegroundTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == targetPackage) {
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundTime = event.timeStamp
                } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (lastForegroundTime > 0) {
                        totalTimeMs += (event.timeStamp - lastForegroundTime)
                        lastForegroundTime = 0L // reset for the next session
                    }
                }
            }
        }

        // CRITICAL: If Pinterest is currently open right now, add this ongoing session!
        if (lastForegroundTime > 0) {
            totalTimeMs += (endTime - lastForegroundTime)
        }

        return totalTimeMs
    }
}