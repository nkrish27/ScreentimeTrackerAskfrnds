package com.example.screen_timetracker

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
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

        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("BlockPrefs", Context.MODE_PRIVATE)
        val limitKey = "limit_${appItem.packageName}"

        // 1. CLEAR OLD LISTENERS to prevent RecyclerView glitches
        holder.cbBlockApp.setOnCheckedChangeListener(null)
        if (holder.etAppLimit.tag is TextWatcher) {
            holder.etAppLimit.removeTextChangedListener(holder.etAppLimit.tag as TextWatcher)
        }

        // 2. RETRIEVE AND DISPLAY SAVED LIMIT
        val savedLimit = prefs.getInt(limitKey, 0)

        if (savedLimit > 0) {
            holder.etAppLimit.setText(savedLimit.toString())
            holder.cbBlockApp.isChecked = true
            appItem.isSelected = true
        } else {
            holder.etAppLimit.text.clear()
            holder.cbBlockApp.isChecked = appItem.isSelected
        }

        // 3. CHECKBOX LISTENER
        holder.cbBlockApp.setOnCheckedChangeListener { _, isChecked ->
            appItem.isSelected = isChecked
            val blockedApps = prefs.getStringSet("blocked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            if (isChecked) {
                blockedApps.add(appItem.packageName)
                val inputStr = holder.etAppLimit.text.toString()
                val limitMins = if (inputStr.isNotEmpty()) inputStr.toInt() else 30
                prefs.edit().putInt(limitKey, limitMins).apply()
            } else {
                blockedApps.remove(appItem.packageName)
                prefs.edit().remove(limitKey).apply()
                holder.etAppLimit.text.clear()
            }
            prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
        }

        // 4. TEXT WATCHER (Saves as you type)
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Only save the typed limit if the checkbox is actually checked
                if (holder.cbBlockApp.isChecked) {
                    val inputStr = s.toString()
                    if (inputStr.isNotEmpty()) {
                        val limitMins = inputStr.toInt()
                        prefs.edit().putInt(limitKey, limitMins).apply()
                    }
                }
            }
        }

        // Attach the new watcher and save it to the tag so we can remove it later
        holder.etAppLimit.addTextChangedListener(textWatcher)
        holder.etAppLimit.tag = textWatcher
    }
}