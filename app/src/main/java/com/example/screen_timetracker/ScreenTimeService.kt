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
    // Phase 6: Push Notification Workaround Variables
    private val friendListeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private val serviceStartTime = System.currentTimeMillis()

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

        listenForFriendRequests()

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

        // Wire up the beg button so it doesn't crash later
        val begButton = overlayView?.findViewById<android.widget.Button>(R.id.btnBegFriend)
        begButton?.setOnClickListener {
            // Give instant UI feedback and prevent spam-clicking
            begButton.text = "Sending Request..."
            begButton.isEnabled = false

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // This is the "Document" we are pushing to the cloud
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val myName = currentUser?.displayName ?: "Unknown Friend"

            val requestData = hashMapOf(
                "status" to "pending",
                "appPackage" to lastForegroundPackage,
                "time_requested_mins" to 10,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "userName" to myName // Add this to the map!
            )
            // Push it to a collection named 'extension_requests'


            if (currentUser != null) {
                val myUid = currentUser.uid

                // NEW PATH: users -> [myUid] -> extension_requests
                val myRequestsRef = db.collection("users").document(myUid).collection("extension_requests")

                myRequestsRef.add(requestData)
                    .addOnSuccessListener { documentReference ->
                        android.widget.Toast.makeText(this@ScreenTimeService, "Sent! Waiting for mercy...", android.widget.Toast.LENGTH_SHORT).show()
                        begButton.text = "Waiting for friend..."

                        // PHASE 4: The Listener (now pointing to the private path)
                        currentListener = myRequestsRef.document(documentReference.id)
                            .addSnapshotListener { snapshot, e ->
                                if (e != null) {
                                    Log.w("Firebase", "Listen failed.", e)
                                    return@addSnapshotListener
                                }

                                if (snapshot != null && snapshot.exists()) {
                                    val status = snapshot.getString("status")
                                    Log.d("Firebase", "Current status: $status")

                                    if (status == "approved") {
                                        // 1. Give them 1 minute of freedom for testing (60,000 ms)
                                        temporaryUnlockEndTime = System.currentTimeMillis() + 60000

                                        // 2. Hide the overlay
                                        handler.post {
                                            hideOverlay()
                                            android.widget.Toast.makeText(this@ScreenTimeService, "ACCESS GRANTED! You have 1 minute.", android.widget.Toast.LENGTH_LONG).show()
                                        }

                                        // 3. Kill the listener so it stops watching the database
                                        currentListener?.remove()

                                    } else if (status == "declined") {
                                        // DECLINE LOGIC WE ADDED EARLIER
                                        handler.post {
                                            begButton.text = "Request Declined"
                                            begButton.isEnabled = true
                                            android.widget.Toast.makeText(this@ScreenTimeService, "Your friend denied the request.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                        currentListener?.remove()
                                    }
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error adding document", e)
                        android.widget.Toast.makeText(this@ScreenTimeService, "Failed to send.", android.widget.Toast.LENGTH_SHORT).show()
                        begButton.text = "Beg Friend for 10 Mins"
                        begButton.isEnabled = true
                    }
            } else {
                // Failsafe in case the user's login session expired
                handler.post {
                    android.widget.Toast.makeText(this@ScreenTimeService, "Error: Not logged in!", android.widget.Toast.LENGTH_SHORT).show()
                    begButton.text = "Error: Must be logged in"
                }
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
    private fun listenForFriendRequests() {
        val prefs = getSharedPreferences("FriendPrefs", Context.MODE_PRIVATE)
        val savedFriends = prefs.getStringSet("friend_uids", mutableSetOf()) ?: mutableSetOf()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Clear old listeners if this is restarted
        friendListeners.forEach { it.remove() }
        friendListeners.clear()

        for (uid in savedFriends) {
            val listener = db.collection("users").document(uid).collection("extension_requests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener

                    for (change in snapshot.documentChanges) {
                        // Only look at BRAND NEW requests
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {

                            val timestamp = change.document.getTimestamp("timestamp")?.toDate()?.time ?: 0

                            // CRITICAL: Ignore old pending requests from before the phone turned on
                            if (timestamp > serviceStartTime) {
                                val friendName = change.document.getString("userName") ?: "A friend"
                                val appPackage = change.document.getString("appPackage") ?: "an app"

                                sendLocalNotification(friendName, appPackage)
                            }
                        }
                    }
                }
            friendListeners.add(listener)
        }
    }

    private fun sendLocalNotification(friendName: String, appPackage: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // This makes tapping the notification open the Friend Dashboard
        val intent = android.content.Intent(this, FriendDashboardActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Time Request!")
            .setContentText("$friendName is begging to use $appPackage.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Built-in Android icon
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismisses when tapped
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH) // Pops up on screen
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL) // Plays sound/vibrate
            .build()

        // Use a random ID so multiple requests stack up instead of overwriting each other
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}