package com.example.screen_timetracker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private val appList = mutableListOf<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewApps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadApps()

        adapter = AppAdapter(appList)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            saveSelectedApps()
        }
    }

    private fun loadApps() {
        // Load previously saved apps
        val prefs = getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
        val savedPackages = prefs.getStringSet("blocked_apps", setOf()) ?: setOf()

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)

        // Fetch all apps that show up in the app drawer
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()

            // Don't let the user block their own tracker!
            if (packageName != "com.example.screen_timetracker") {
                val isSelected = savedPackages.contains(packageName)
                appList.add(AppItem(appName, packageName, isSelected))
            }
        }

        // Sort alphabetically so it's easy to find apps
        appList.sortBy { it.appName.lowercase() }
    }

    private fun saveSelectedApps() {
        val selectedPackages = appList.filter { it.isSelected }.map { it.packageName }.toSet()

        val prefs = getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("blocked_apps", selectedPackages).apply()

        Toast.makeText(this, "Block list saved!", Toast.LENGTH_SHORT).show()
        finish() // Close the settings screen
    }
}