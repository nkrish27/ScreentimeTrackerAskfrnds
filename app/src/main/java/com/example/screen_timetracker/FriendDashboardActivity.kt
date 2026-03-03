package com.example.screen_timetracker

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FriendDashboardActivity : AppCompatActivity() {

    private lateinit var adapter: RequestAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_dashboard)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Start with an empty list
        adapter = RequestAdapter(emptyList())
        recyclerView.adapter = adapter

        listenForRequests()
    }

    private fun listenForRequests() {
        // Listen to the collection in real-time, ordered by newest first
        db.collection("extension_requests")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firebase", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requestsList = ArrayList<ExtensionRequest>()

                    // Convert the raw Firebase documents into our Kotlin Data Class
                    for (doc in snapshot.documents) {
                        val req = ExtensionRequest(
                            documentId = doc.id,
                            appPackage = doc.getString("app_package") ?: "Unknown App",
                            status = doc.getString("status") ?: "unknown",
                            timeRequestedMins = doc.getLong("time_requested_mins")?.toInt() ?: 0
                        )
                        requestsList.add(req)
                    }

                    // Feed the new data to the UI
                    adapter.updateData(requestsList)
                }
            }
    }
}