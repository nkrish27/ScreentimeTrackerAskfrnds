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

        val tvFriendName: TextView = itemView.findViewById(R.id.tvFriendName)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val btnDecline: Button = itemView.findViewById(R.id.btnDecline) // Added Decline Button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val currentRequest = requests[position]
        val pm = holder.itemView.context.packageManager
        val packageName = currentRequest.appPackage
        holder.tvFriendName.text = "${currentRequest.userName} wants to use:"

        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            val appIcon = pm.getApplicationIcon(packageName)

            holder.tvAppName.text = appName
            holder.ivAppIcon.setImageDrawable(appIcon)

        } catch (e: PackageManager.NameNotFoundException) {
            holder.tvAppName.text = packageName
        }

        val currentStatus = currentRequest.status

        // Explicitly set ALL visual states for Approved, Declined, and Pending
        when (currentStatus) {
            "approved" -> {
                holder.tvStatus.text = "Status: Approved"
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
                holder.btnApprove.text = "Approved"
                holder.btnApprove.isEnabled = false
                holder.btnDecline.visibility = View.GONE // Hide decline button
            }
            "declined" -> {
                holder.tvStatus.text = "Status: Declined"
                holder.tvStatus.setTextColor(Color.parseColor("#F44336")) // Red
                holder.btnApprove.visibility = View.GONE // Hide approve button
                holder.btnDecline.text = "Declined"
                holder.btnDecline.isEnabled = false
                holder.btnDecline.visibility = View.VISIBLE
            }
            else -> {
                holder.tvStatus.text = "Status: Pending"
                holder.tvStatus.setTextColor(Color.parseColor("#FFA500")) // Orange
                holder.btnApprove.text = "Approve"
                holder.btnDecline.text = "Decline"
                holder.btnApprove.isEnabled = true
                holder.btnDecline.isEnabled = true
                holder.btnApprove.visibility = View.VISIBLE
                holder.btnDecline.visibility = View.VISIBLE
            }
        }

        val db = FirebaseFirestore.getInstance()
        val documentId = currentRequest.documentId

        // The Approve Click Listener
        holder.btnApprove.setOnClickListener {
            holder.btnApprove.isEnabled = false
            holder.btnDecline.isEnabled = false
            holder.btnApprove.text = "Approving..."

            // Update the status in the database
            db.collection("users").document(currentRequest.userId).collection("extension_requests").document(documentId)
                .update("status", "approved")
                .addOnFailureListener {
                    holder.btnApprove.isEnabled = true
                    holder.btnDecline.isEnabled = true
                    holder.btnApprove.text = "Approve"
                }
        }

        // The Decline Click Listener
        holder.btnDecline.setOnClickListener {
            holder.btnApprove.isEnabled = false
            holder.btnDecline.isEnabled = false
            holder.btnDecline.text = "Declining..."

            db.collection("users").document(currentRequest.userId).collection("extension_requests").document(documentId)
                .update("status", "declined")
                .addOnFailureListener {
                    holder.btnApprove.isEnabled = true
                    holder.btnDecline.isEnabled = true
                    holder.btnDecline.text = "Decline"
                }
        }
    }

    fun updateData(newRequests: List<ExtensionRequest>) {
        this.requests = newRequests
        notifyDataSetChanged()
    }
}