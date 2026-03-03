package com.example.screen_timetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ImageView
import android.content.pm.PackageManager

class RequestAdapter(private var requests: List<ExtensionRequest>) : RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon) // Make sure this line is here!
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        // 1. Grab the specific request for this row using the 'position'
        val currentRequest = requests[position]

        val pm = holder.itemView.context.packageManager
        // Note: ensure 'packageName' is the actual variable name inside your ExtensionRequest data class
        val packageName = currentRequest.appPackage

        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            val appIcon = pm.getApplicationIcon(packageName)

            // 2. Update these to match the IDs in your RequestViewHolder
            holder.tvAppName.text = appName
            holder.ivAppIcon.setImageDrawable(appIcon)

        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback
            holder.tvAppName.text = packageName
        }

        // 1. Read the actual status from your database object
        // (Make sure 'status' matches the variable name in your ExtensionRequest data class)
        val currentStatus = currentRequest.status

        // 2. Explicitly set ALL visual states based on that status
        if (currentStatus == "approved") {
            holder.tvStatus.text = "Status: Approved"
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            holder.btnApprove.text = "Approved"
            holder.btnApprove.isEnabled = false // Don't let them click it again
        } else {
            holder.tvStatus.text = "Status: Pending"
            holder.tvStatus.setTextColor(Color.parseColor("#FFA500")) // Orange
            holder.btnApprove.text = "Approve"
            holder.btnApprove.isEnabled = true // Ready to be clicked
        }

        // 3. The Click Listener
        holder.btnApprove.setOnClickListener {
            holder.btnApprove.isEnabled = false
            holder.btnApprove.text = "Approving..."

            val db = FirebaseFirestore.getInstance()

            // NOTE: Make sure currentRequest.documentId matches your actual ID variable!
            val documentId = currentRequest.documentId

            db.collection("requests").document(documentId)
                .update("status", "approved")
                .addOnSuccessListener {
                    // Update visuals on success
                    holder.tvStatus.text = "Status: Approved"
                    holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    holder.btnApprove.text = "Approved"
                }
                .addOnFailureListener { e ->
                    // Reset visuals if it fails
                    holder.btnApprove.isEnabled = true
                    holder.btnApprove.text = "Approve"
                    holder.tvStatus.text = "Error: Try again"
                    holder.tvStatus.setTextColor(Color.parseColor("#FF0000"))
                }
        }
    }

    // Function to update the list when new data arrives from the cloud
    fun updateData(newRequests: List<ExtensionRequest>) {
        this.requests = newRequests
        notifyDataSetChanged()
    }
}