package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"
    var isFirebaseAvailable: Boolean = false
        private set

    fun initialize(context: Context) {
        try {
            val app = FirebaseApp.initializeApp(context)
            isFirebaseAvailable = (app != null)
            Log.d(TAG, "Firebase initialized successfully. Available: $isFirebaseAvailable")
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.w(TAG, "Firebase initialization failed. Falling back to local offline-first high-fidelity mode. Reason: ${e.message}")
        }
    }
}
