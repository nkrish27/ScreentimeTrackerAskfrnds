package com.example.screen_timetracker

data class ExtensionRequest(
    val documentId: String,
    val appPackage: String,
    val status: String,
    val timeRequestedMins: Int
)
