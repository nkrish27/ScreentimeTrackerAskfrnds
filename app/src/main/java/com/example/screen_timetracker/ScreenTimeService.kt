package com.example.screen_timetracker

    import android.app.Notification
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.app.Service
    import android.content.Intent
    import android.os.IBinder
    import androidx.core.app.NotificationCompat

    class ScreenTimeService : Service() {

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            // As soon as the service starts, we turn it into a Foreground service
            startForegroundService()
            return START_STICKY
        }

        private fun startForegroundService() {
            val channelId = "ScreenTimeChannel"
            val manager = getSystemService(NotificationManager::class.java)

            // FIX: Only create the channel if we are on Android 8.0 (Oreo) or newer
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Screen Time Tracker",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }

            // Build the Notification
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Screen Time Monitor")
                .setContentText("Tracking usage in background...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()

            // Start the service
            // (ID must be > 0)
            startForeground(1, notification)
        }
    }