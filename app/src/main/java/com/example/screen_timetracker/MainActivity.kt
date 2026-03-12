package com.example.screen_timetracker

import android.app.AppOpsManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Log.d("MY_UID", FirebaseAuth.getInstance().currentUser?.uid ?: "No User")
        // Check permission on startup
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }
        // Find the button that opens the FriendDashboardActivity
        val btnDashboard = findViewById<android.widget.Button>(R.id.btnOpenDashboard)

        // Wire it up to open the FriendDashboardActivity
        btnDashboard.setOnClickListener {
            val intent = android.content.Intent(this, FriendDashboardActivity::class.java)
            startActivity(intent)
        }
        // Find the button that opens the SettingsActivity
        val btnSettings = findViewById<android.widget.Button>(R.id.btnOpenSettings)

        // Wire it up to open the SettingsActivity
        btnSettings.setOnClickListener {
            val intent = android.content.Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        // Find the views we just added
        val tvMyUid = findViewById<android.widget.TextView>(R.id.tvMyUid)
        val btnCopyUid = findViewById<android.widget.Button>(R.id.btnCopyUid)

        // Get the actual UID
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val myUid = auth.currentUser?.uid ?: "Not logged in"

        // Show it on screen
        tvMyUid.text = "My UID:\n$myUid"

        // Make the copy button work
        btnCopyUid.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("User UID", myUid)
            clipboard.setPrimaryClip(clip)

            android.widget.Toast.makeText(this, "UID Copied! Send it to your friend.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
            return
        }
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        //  Check Notifications
        // Only needed for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission()
                return
            }
        }
        startTrackerService()
    }
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    private fun requestUsageStatsPermission() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }
    private fun startTrackerService() {
        val intent = android.content.Intent(this, ScreenTimeService::class.java)
        // On Android 8.0+, we must use startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Check if we can draw overlays
    private fun hasOverlayPermission(): Boolean {
        return android.provider.Settings.canDrawOverlays(this)
    }

    // Bounce user to the "Display over other apps" settings
    private fun requestOverlayPermission() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }
    }
}