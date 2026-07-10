package com.example.service

import android.content.Context
import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed token: $token")
        val sharedPrefs = getSharedPreferences("com.example.CHAT_PREFS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        
        val myId = sharedPrefs.getString("logged_in_user_id", null)
        if (!myId.isNullOrEmpty()) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("users").document(myId).update("fcmToken", token)
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Error updating token on new token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val senderId = data["senderId"] ?: "unknown"
            val senderName = data["senderName"] ?: "Someone"
            val body = data["body"] ?: data["message"] ?: "New message received"
            
            NotificationHelper.showNotification(this, senderId, senderName, body)
        } else {
            remoteMessage.notification?.let {
                val title = it.title ?: "New Message"
                val body = it.body ?: "Click to view"
                val senderId = data["senderId"] ?: "unknown"
                NotificationHelper.showNotification(this, senderId, title, body)
            }
        }
    }
}
