package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.util.FirebaseHelper

class ChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Safety wrap Firebase initialization
        FirebaseHelper.initialize(this)
        
        // Initialize notification channel
        com.example.util.NotificationHelper.createNotificationChannel(this)
        
        // Warm up local database instance
        AppDatabase.getDatabase(this)
    }
}
