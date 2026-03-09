package com.example.screen_timetracker

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class FriendDashboardActivity : AppCompatActivity() {

    private lateinit var adapter: RequestAdapter
    private val db = FirebaseFirestore.getInstance()

    // We now need a LIST of listeners, one for each friend
    private val activeListeners = mutableListOf<ListenerRegistration>()

    // A Map to keep each friend's requests organized before combining them
    private val multiFriendRequests = mutableMapOf<String, List<ExtensionRequest>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_dashboard)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewRequests)
        val etFriendUid = findViewById<EditText>(R.id.etFriendUid)
        val btnConnect = findViewById<Button>(R.id.btnConnect)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RequestAdapter(emptyList())
        recyclerView.adapter = adapter

        // 1. Load ALL saved friends (using a Set instead of a single String)
        val prefs = getSharedPreferences("FriendPrefs", Context.MODE_PRIVATE)
        val savedFriends = prefs.getStringSet("friend_uids", mutableSetOf()) ?: mutableSetOf()

        // 2. Start listening to every friend we have saved
        for (uid in savedFriends) {
            listenForRequests(uid)
        }

        // 3. The Connect Button Logic for adding NEW friends
        btnConnect.setOnClickListener {
            val targetUid = etFriendUid.text.toString().trim()

            if (targetUid.isNotEmpty()) {
                if (!savedFriends.contains(targetUid)) {
                    // Add the new UID to our set and save it
                    savedFriends.add(targetUid)
                    prefs.edit().putStringSet("friend_uids", savedFriends).apply()

                    // Start listening to the new friend
                    listenForRequests(targetUid)

                    etFriendUid.text.clear() // Clear the box for the next friend
                    Toast.makeText(this, "Added new friend!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Already connected to this friend!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a UID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForRequests(targetUid: String) {
        val listener = db.collection("users").document(targetUid).collection("extension_requests")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firebase", "Listen failed for $targetUid.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requestsList = ArrayList<ExtensionRequest>()
                    for (doc in snapshot.documents) {
                        val req = ExtensionRequest(
                            documentId = doc.id,
                            appPackage = doc.getString("appPackage") ?: "Unknown App",
                            status = doc.getString("status") ?: "unknown",
                            timeRequestedMins = doc.getLong("time_requested_mins")?.toInt() ?: 0,
                            userId = targetUid,
                            userName = doc.getString("userName") ?: "Unknown Friend"
                        )
                        requestsList.add(req)
                    }

                    // 1. Save this specific friend's list into our Map
                    multiFriendRequests[targetUid] = requestsList

                    // 2. Combine ALL lists from ALL friends into one massive list
                    val combinedList = multiFriendRequests.values.flatten()

                    // 3. Send the combined list to the UI
                    adapter.updateData(combinedList)
                }
            }

        // Keep track of the listener so we can kill it later to prevent memory leaks
        activeListeners.add(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kill all active listeners when closing the screen
        activeListeners.forEach { it.remove() }
    }
}