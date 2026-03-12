package com.example.screen_timetracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(private val appList: List<AppItem>) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val cbBlockApp: CheckBox = view.findViewById(R.id.cbBlockApp)
        // 1. ADDED: The EditText for the time limit from your XML
        val etAppLimit: EditText = view.findViewById(R.id.etAppLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun getItemCount(): Int = appList.size

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appItem = appList[position]
        holder.tvAppName.text = appItem.appName

        // Remove listener temporarily so we don't trigger it while setting up
        holder.cbBlockApp.setOnCheckedChangeListener(null)
        holder.cbBlockApp.isChecked = appItem.isSelected

        // 2. ADDED: Get context from the itemView to initialize SharedPreferences
        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)

        // 3. INTEGRATED SNIPPET: Replaced the basic listener with your SharedPreferences logic
        holder.cbBlockApp.setOnCheckedChangeListener { _, isChecked ->
            appItem.isSelected = isChecked // Keep your original state tracking

            // Get the Set of currently blocked apps
            val blockedApps = prefs.getStringSet("blocked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            if (isChecked) {
                // Changed 'currentApp' to 'appItem' to match your data class
                blockedApps.add(appItem.packageName)

                // Grab the minutes they typed (default to 30 if they left it blank)
                val inputStr = holder.etAppLimit.text.toString()
                val limitMins = if (inputStr.isNotEmpty()) inputStr.toInt() else 30

                // Save the specific limit for this exact package
                prefs.edit().putInt("limit_${appItem.packageName}", limitMins).apply()

            } else {
                blockedApps.remove(appItem.packageName)
                // Clean up the limit if they unblock the app
                prefs.edit().remove("limit_${appItem.packageName}").apply()
            }

            // Save the updated Set
            prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
        }
    }
}