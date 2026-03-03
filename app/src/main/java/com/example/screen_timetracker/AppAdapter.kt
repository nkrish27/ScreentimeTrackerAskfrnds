package com.example.screen_timetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(private val appList: List<AppItem>) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val cbBlockApp: CheckBox = view.findViewById(R.id.cbBlockApp)
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

        holder.cbBlockApp.setOnCheckedChangeListener { _, isChecked ->
            appItem.isSelected = isChecked
        }
    }
}