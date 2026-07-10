package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.User
import com.example.util.FirebaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "AuthRepository"
    private val prefs: SharedPreferences = context.getSharedPreferences("chatapp_prefs", Context.MODE_PRIVATE)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        // Attempt auto-login
        val savedUserId = prefs.getString("logged_in_user_id", null)
        if (savedUserId != null) {
            _currentUser.value = User(
                userId = savedUserId,
                username = prefs.getString("logged_in_username", savedUserId) ?: savedUserId,
                bio = prefs.getString("logged_in_bio", "Hey there! I am using i chat.") ?: "Hey there! I am using i chat.",
                profilePicBase64 = prefs.getString("logged_in_pic", null),
                isOnline = true
            )
            // If Firebase is available, update online state in Firestore
            if (FirebaseHelper.isFirebaseAvailable) {
                try {
                    val auth = FirebaseAuth.getInstance()
                    if (auth.currentUser != null) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(savedUserId)
                            .update("isOnline", true, "lastSeen", System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update Firebase status on auto-login", e)
                }
            }
        }
    }

    suspend fun checkUserExists(userId: String): Boolean = withContext(Dispatchers.IO) {
        val id = userId.trim().lowercase()
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(id).get().await()
                return@withContext doc.exists()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking user existence in Firebase", e)
            }
        }
        return@withContext database.userDao().getUserById(id) != null
    }

    suspend fun register(userId: String, username: String, password: String, bio: String, phoneOrEmail: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val id = userId.trim().lowercase()
            if (id.isEmpty() || username.trim().isEmpty() || password.trim().isEmpty()) {
                return@withContext Result.failure(Exception("Fields cannot be empty"))
            }

            // Check if user already exists
            if (checkUserExists(id)) {
                return@withContext Result.failure(Exception("User ID already exists. Please choose another one."))
            }

            val newUser = User(
                userId = id,
                username = username,
                bio = bio,
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                phoneOrEmail = phoneOrEmail
            )

            if (FirebaseHelper.isFirebaseAvailable) {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                
                // Real Firebase Auth signup
                val virtualEmail = "${id}@chatapp.com"
                auth.createUserWithEmailAndPassword(virtualEmail, password).await()
                
                // Save user info in Firestore
                firestore.collection("users").document(id).set(newUser).await()
            }

            // Save user in Room DB
            database.userDao().insertUser(newUser)

            // Save in SharedPreferences for auto-login & password verification
            prefs.edit().apply {
                putString("logged_in_user_id", id)
                putString("logged_in_username", username)
                putString("logged_in_bio", bio)
                putString("logged_in_pic", null)
                putString("pass_$id", password)
                putString("contact_$id", phoneOrEmail)
                apply()
            }

            _currentUser.value = newUser
            Result.success(newUser)
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            Result.failure(e)
        }
    }

    suspend fun login(userId: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val id = userId.trim().lowercase()
            if (id.isEmpty() || password.trim().isEmpty()) {
                return@withContext Result.failure(Exception("Fields cannot be empty"))
            }

            var loggedInUser: User? = null

            if (FirebaseHelper.isFirebaseAvailable) {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                
                val virtualEmail = "${id}@chatapp.com"
                auth.signInWithEmailAndPassword(virtualEmail, password).await()
                
                // Retrieve user info from Firestore
                val doc = firestore.collection("users").document(id).get().await()
                if (doc.exists()) {
                    loggedInUser = doc.toObject(User::class.java)?.copy(isOnline = true)
                }
            }

            // If not found in Firebase or Firebase unavailable, fall back to Local Room DB/SharedPreferences
            if (loggedInUser == null) {
                val localUser = database.userDao().getUserById(id)
                if (localUser == null) {
                    return@withContext Result.failure(Exception("User ID not registered. Please register first."))
                }
                
                // Verify password locally
                val storedPass = prefs.getString("pass_$id", "password") // default to "password" for pre-existing/demo users
                if (storedPass != password) {
                    return@withContext Result.failure(Exception("Incorrect password. Please try again."))
                }
                
                loggedInUser = localUser.copy(isOnline = true)
            }

            // Update user in Room DB
            database.userDao().insertUser(loggedInUser)

            // Save details to SharedPreferences
            prefs.edit().apply {
                putString("logged_in_user_id", loggedInUser.userId)
                putString("logged_in_username", loggedInUser.username)
                putString("logged_in_bio", loggedInUser.bio)
                putString("logged_in_pic", loggedInUser.profilePicBase64)
                apply()
            }

            _currentUser.value = loggedInUser
            Result.success(loggedInUser)
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(username: String, bio: String, profilePicBase64: String?): Result<User> = withContext(Dispatchers.IO) {
        try {
            val current = _currentUser.value ?: return@withContext Result.failure(Exception("User not logged in"))
            val updated = current.copy(
                username = username,
                bio = bio,
                profilePicBase64 = profilePicBase64,
                lastSeen = System.currentTimeMillis()
            )

            if (FirebaseHelper.isFirebaseAvailable) {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users").document(current.userId).set(updated).await()
            }

            database.userDao().insertUser(updated)

            prefs.edit().apply {
                putString("logged_in_username", username)
                putString("logged_in_bio", bio)
                putString("logged_in_pic", profilePicBase64)
                apply()
            }

            _currentUser.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Update profile error", e)
            Result.failure(e)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        if (current != null) {
            try {
                if (FirebaseHelper.isFirebaseAvailable) {
                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("users").document(current.userId).update("isOnline", false, "lastSeen", System.currentTimeMillis())
                    FirebaseAuth.getInstance().signOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase logout error", e)
            }
            
            // Mark local user offline
            val offlineUser = current.copy(isOnline = false, lastSeen = System.currentTimeMillis())
            database.userDao().insertUser(offlineUser)
        }

        prefs.edit().apply {
            remove("logged_in_user_id")
            remove("logged_in_username")
            remove("logged_in_bio")
            remove("logged_in_pic")
            apply()
        }

        _currentUser.value = null
    }

    suspend fun recoverPassword(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (FirebaseHelper.isFirebaseAvailable) {
                val virtualEmail = "${userId.trim()}@chatapp.com"
                FirebaseAuth.getInstance().sendPasswordResetEmail(virtualEmail).await()
                Result.success("Password recovery email sent to $virtualEmail. Please check your inbox.")
            } else {
                Result.success("Demo Recovery: In a local environment, passwords can be reset via DB. Your virtual recovery email would be: ${userId.trim()}@chatapp.com")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
