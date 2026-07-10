package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.FriendRequest
import com.example.data.model.Message
import com.example.data.model.User
import com.example.util.FirebaseHelper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "ChatRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var activeChatRecipientId: String? = null
        private set

    fun setActiveChatRecipientId(recipientId: String?) {
        activeChatRecipientId = recipientId
    }

    private var messagesListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null
    private var outgoingRequestsListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null

    // Blocked list in memory/local
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds

    init {
        // Populate standard initial demo users/contacts to guarantee the user sees something immediately
        repositoryScope.launch {
            try {
                val currentCount = database.userDao().getUserById("alice")
                if (currentCount == null) {
                    val demoUsers = listOf(
                        User("alice", "Alice Smith", "At the gym 🏋️‍♂️", null, isOnline = true, lastSeen = System.currentTimeMillis(), phoneOrEmail = "alice.smith@gmail.com"),
                        User("bob", "Bob Jones", "Coding all day and night 💻", null, isOnline = false, lastSeen = System.currentTimeMillis() - 3600000, phoneOrEmail = "9876543210"),
                        User("charlie", "Charlie Brown", "Can't talk, text only 🔇", null, isOnline = true, lastSeen = System.currentTimeMillis(), phoneOrEmail = "charlie.brown@yahoo.com")
                    )
                    database.userDao().insertUsers(demoUsers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert demo users", e)
            }
        }
    }

    /**
     * Start real-time Firestore listeners for a logged-in user
     */
    fun startRealtimeSync(myId: String) {
        val sharedPrefs = context.getSharedPreferences("com.example.CHAT_PREFS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("logged_in_user_id", myId).apply()

        if (!FirebaseHelper.isFirebaseAvailable) {
            // Setup simulator for typing indicators and message receipts
            startLocalSimulator(myId)
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()

            // Fetch and register FCM token in Firestore
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d(TAG, "Fetched FCM token: $token")
                        sharedPrefs.edit().putString("fcm_token", token).apply()
                        db.collection("users").document(myId).update("fcmToken", token)
                            .addOnSuccessListener {
                                Log.d(TAG, "Successfully updated user FCM token in Firestore")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update user FCM token in Firestore", e)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error obtaining FCM token", e)
            }

            // 1. Sync messages where the user is either the sender or receiver
            messagesListener = db.collection("messages")
                .whereIn("senderId", listOf(myId)) // Standard Firestore query split
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Messages snapshot error", error)
                        return@addSnapshotListener
                    }
                    snapshots?.documentChanges?.forEach { change ->
                        val msg = change.document.toObject(Message::class.java)
                        repositoryScope.launch {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                // For deletion
                                database.messageDao().insertMessage(msg.copy(isDeletedForMe = true))
                            } else {
                                database.messageDao().insertMessage(msg)
                            }
                        }
                    }
                }

            // Sync messages where user is receiver
            db.collection("messages")
                .whereIn("receiverId", listOf(myId))
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    snapshots?.documentChanges?.forEach { change ->
                        val msg = change.document.toObject(Message::class.java)
                        repositoryScope.launch {
                            database.messageDao().insertMessage(msg)

                            // Trigger local push notification for real-time Firestore messages
                            if (change.type == DocumentChange.Type.ADDED) {
                                val senderId = msg.senderId
                                if (senderId != myId && senderId != activeChatRecipientId && (System.currentTimeMillis() - msg.timestamp) < 60000) {
                                    val senderUser = database.userDao().getUserById(senderId)
                                    val senderName = senderUser?.username ?: "Someone"
                                    com.example.util.NotificationHelper.showNotification(
                                        context,
                                        senderId,
                                        senderName,
                                        msg.text
                                    )
                                }
                            }
                        }
                    }
                }

            // 2. Sync users details & presence
            usersListener = db.collection("users")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    snapshots?.documentChanges?.forEach { change ->
                        val user = change.document.toObject(User::class.java)
                        repositoryScope.launch {
                            database.userDao().insertUser(user)
                        }
                    }
                }

            // 3. Sync friend requests
            requestsListener = db.collection("friend_requests")
                .whereEqualTo("receiverId", myId)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    snapshots?.documentChanges?.forEach { change ->
                        val req = change.document.toObject(FriendRequest::class.java)
                        repositoryScope.launch {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                database.friendRequestDao().deleteRequestById(req.requestId)
                            } else {
                                database.friendRequestDao().insertRequest(req)
                            }
                        }
                    }
                }

            // Sync friend requests where user is sender (to see if they are accepted)
            outgoingRequestsListener = db.collection("friend_requests")
                .whereEqualTo("senderId", myId)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    snapshots?.documentChanges?.forEach { change ->
                        val req = change.document.toObject(FriendRequest::class.java)
                        repositoryScope.launch {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                database.friendRequestDao().deleteRequestById(req.requestId)
                            } else {
                                database.friendRequestDao().insertRequest(req)
                            }
                        }
                    }
                }

            // Update user status to online
            db.collection("users").document(myId).update("isOnline", true, "lastSeen", System.currentTimeMillis())

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Firestore sync", e)
        }
    }

    /**
     * Stop all active listeners
     */
    fun stopRealtimeSync(myId: String) {
        messagesListener?.remove()
        usersListener?.remove()
        requestsListener?.remove()
        outgoingRequestsListener?.remove()
        typingListener?.remove()

        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(myId)
                    .update("isOnline", false, "lastSeen", System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Firestore sync status", e)
            }
        }
    }

    // --- MESSAGES ACCESS ---

    fun getMessagesFlow(myId: String, otherId: String): Flow<List<Message>> {
        return database.messageDao().getMessagesFlow(myId, otherId)
    }

    fun getAllContactsFlow(): Flow<List<User>> {
        return database.userDao().getAllUsersFlow()
    }

    fun getFriendsFlow(myId: String): Flow<List<User>> {
        return database.userDao().getFriendsFlow(myId)
    }

    suspend fun getRequestBetweenUsers(user1: String, user2: String): FriendRequest? = withContext(Dispatchers.IO) {
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc1 = db.collection("friend_requests").document("${user1}_to_${user2}").get()
                var request = doc1.result?.toObject(FriendRequest::class.java)
                if (request == null) {
                    val doc2 = db.collection("friend_requests").document("${user2}_to_${user1}").get()
                    request = doc2.result?.toObject(FriendRequest::class.java)
                }
                if (request != null) {
                    database.friendRequestDao().insertRequest(request)
                    return@withContext request
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting request from Firebase", e)
            }
        }
        return@withContext database.friendRequestDao().getRequestBetweenUsers(user1, user2)
    }

    fun getUnreadCount(senderId: String, receiverId: String): Flow<Int> {
        return database.messageDao().getUnreadCountFlow(senderId, receiverId)
    }

    suspend fun markAsRead(senderId: String, receiverId: String) = withContext(Dispatchers.IO) {
        database.messageDao().markMessagesAsRead(senderId, receiverId)
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                val unread = database.messageDao().getUnreadMessages(receiverId, senderId)
                unread.forEach { msg ->
                    db.collection("messages").document(msg.messageId).update("status", "READ")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing read status to Firebase", e)
            }
        }
    }

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        text: String,
        replyToId: String? = null,
        replyToText: String? = null,
        imageUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationMs: Long? = null,
        isGroupMessage: Boolean = false
    ): Result<Message> = withContext(Dispatchers.IO) {
        try {
            if (text.trim().isEmpty() && imageUrl == null && voiceUrl == null) {
                return@withContext Result.failure(Exception("Message content cannot be empty"))
            }

            val msgId = UUID.randomUUID().toString()
            val message = Message(
                messageId = msgId,
                senderId = senderId,
                receiverId = receiverId,
                text = text,
                timestamp = System.currentTimeMillis(),
                status = if (FirebaseHelper.isFirebaseAvailable) "SENT" else "DELIVERED",
                replyToId = replyToId,
                replyToText = replyToText,
                imageUrl = imageUrl,
                voiceUrl = voiceUrl,
                voiceDurationMs = voiceDurationMs,
                isGroupMessage = isGroupMessage
            )

            // Save to Local DB (Offline Caching)
            database.messageDao().insertMessage(message)

            // Sync to Firestore
            if (FirebaseHelper.isFirebaseAvailable) {
                FirebaseFirestore.getInstance()
                    .collection("messages")
                    .document(msgId)
                    .set(message)
            } else {
                // local network simulator triggered
                triggerLocalResponder(message)
            }

            Result.success(message)
        } catch (e: Exception) {
            Log.e(TAG, "Send message error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(messageId: String) = withContext(Dispatchers.IO) {
        val msg = database.messageDao().getMessageById(messageId)
        if (msg != null) {
            database.messageDao().insertMessage(msg.copy(isDeletedForMe = true))
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String) = withContext(Dispatchers.IO) {
        val msg = database.messageDao().getMessageById(messageId)
        if (msg != null) {
            val updated = msg.copy(isDeletedForEveryone = true)
            database.messageDao().insertMessage(updated)

            if (FirebaseHelper.isFirebaseAvailable) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("messages")
                        .document(messageId)
                        .set(updated)
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase delete for everyone failed", e)
                }
            }
        }
    }

    suspend fun addMessageReaction(messageId: String, userId: String, emoji: String?) = withContext(Dispatchers.IO) {
        val msg = database.messageDao().getMessageById(messageId)
        if (msg != null) {
            val updated = if (userId == msg.senderId) {
                msg.copy(senderReaction = emoji)
            } else {
                msg.copy(receiverReaction = emoji)
            }
            database.messageDao().insertMessage(updated)

            if (FirebaseHelper.isFirebaseAvailable) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("messages")
                        .document(messageId)
                        .set(updated)
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase update reaction failed", e)
                }
            }
        }
    }

    // --- TYPING STATUS ---

    suspend fun updateTypingStatus(myId: String, otherId: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(myId)
                    .update("typingToId", if (isTyping) otherId else null)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating typing status", e)
            }
        } else {
            // Update local state
            val user = database.userDao().getUserById(myId)
            if (user != null) {
                database.userDao().insertUser(user.copy(typingToId = if (isTyping) otherId else null))
            }
        }
    }

    // --- FRIEND SYSTEM ---

    fun getUserFlow(userId: String): Flow<User?> {
        return database.userDao().getUserFlow(userId)
    }

    fun getPendingRequests(myId: String): Flow<List<FriendRequest>> {
        return database.friendRequestDao().getPendingRequestsFlow(myId)
    }

    suspend fun searchUser(userId: String): User? = withContext(Dispatchers.IO) {
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(userId).get()
                val user = doc.result?.toObject(User::class.java)
                if (user != null) {
                    database.userDao().insertUser(user)
                    return@withContext user
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase user search error", e)
            }
        }
        return@withContext database.userDao().getUserById(userId)
    }

    suspend fun sendFriendRequest(senderId: String, senderName: String, receiverId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val receiver = searchUser(receiverId) ?: return@withContext Result.failure(Exception("User $receiverId not found"))
            
            val reqId = "${senderId}_to_${receiverId}"
            val request = FriendRequest(
                requestId = reqId,
                senderId = senderId,
                senderName = senderName,
                receiverId = receiverId,
                status = "PENDING"
            )

            // Local cache
            database.friendRequestDao().insertRequest(request)

            if (FirebaseHelper.isFirebaseAvailable) {
                FirebaseFirestore.getInstance()
                    .collection("friend_requests")
                    .document(reqId)
                    .set(request)
            } else {
                // Simulated acceptance for testing ease
                repositoryScope.launch {
                    delay(3000)
                    acceptFriendRequestSimulated(request)
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Send friend request error", e)
            Result.failure(e)
        }
    }

    suspend fun respondToRequest(request: FriendRequest, accept: Boolean) = withContext(Dispatchers.IO) {
        val updated = request.copy(status = if (accept) "ACCEPTED" else "REJECTED")
        
        if (accept) {
            // Make sure both users are fully saved locally
            val otherUser = database.userDao().getUserById(request.senderId) ?: User(
                userId = request.senderId,
                username = request.senderName,
                bio = "Hey there! We are now friends."
            )
            database.userDao().insertUser(otherUser)
            database.friendRequestDao().insertRequest(updated)
        } else {
            database.friendRequestDao().deleteRequestById(request.requestId)
        }

        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                if (accept) {
                    db.collection("friend_requests").document(request.requestId).update("status", "ACCEPTED")
                } else {
                    db.collection("friend_requests").document(request.requestId).delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase respond request failed", e)
            }
        }
    }

    // --- BLOCK SYSTEM ---

    fun isUserBlocked(userId: String): Boolean {
        return _blockedUserIds.value.contains(userId)
    }

    fun blockUser(userId: String) {
        _blockedUserIds.value = _blockedUserIds.value + userId
        repositoryScope.launch {
            val user = database.userDao().getUserById(userId)
            if (user != null) {
                database.userDao().insertUser(user.copy(isBlocked = true))
            }
        }
    }

    fun unblockUser(userId: String) {
        _blockedUserIds.value = _blockedUserIds.value - userId
        repositoryScope.launch {
            val user = database.userDao().getUserById(userId)
            if (user != null) {
                database.userDao().insertUser(user.copy(isBlocked = false))
            }
        }
    }

    // --- LOCAL SIMULATION ENGINE (Offline / Sandbox Mode) ---

    private fun startLocalSimulator(myId: String) {
        Log.i(TAG, "Running high-fidelity offline chat simulator...")
        // Periodically update contacts online/offline and typing status randomly
        repositoryScope.launch {
            while (isActive) {
                delay(12000)
                val contacts = listOf("alice", "bob", "charlie")
                val randomContact = contacts.random()
                val user = database.userDao().getUserById(randomContact)
                if (user != null && !isUserBlocked(randomContact)) {
                    val newOnline = !user.isOnline
                    database.userDao().insertUser(user.copy(
                        isOnline = newOnline,
                        lastSeen = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    private suspend fun acceptFriendRequestSimulated(request: FriendRequest) {
        val updated = request.copy(status = "ACCEPTED")
        database.friendRequestDao().insertRequest(updated)
        
        val buddyId = request.receiverId
        val existing = database.userDao().getUserById(buddyId)
        val buddy = existing ?: User(
            userId = buddyId,
            username = buddyId.replaceFirstChar { it.uppercase() },
            bio = "Always online to chat! ✨",
            isOnline = true
        )
        database.userDao().insertUser(buddy.copy(isOnline = true))
    }

    private fun triggerLocalResponder(msg: Message) {
        if (msg.isGroupMessage) {
            triggerGroupSimulationResponder(msg)
            return
        }
        val contactId = msg.receiverId
        if (isUserBlocked(contactId)) return

        repositoryScope.launch {
            // 1. Simulate delivery ticket
            delay(1000)
            database.messageDao().insertMessage(msg.copy(status = "DELIVERED"))

            // 2. Simulate read ticket
            delay(1500)
            database.messageDao().insertMessage(msg.copy(status = "READ"))

            // 3. Simulate typing state
            delay(1000)
            val contact = database.userDao().getUserById(contactId) ?: return@launch
            database.userDao().insertUser(contact.copy(typingToId = msg.senderId))

            // 4. Send response
            delay(2500)
            database.userDao().insertUser(contact.copy(typingToId = null))

            val replyText = generateSimulationReply(contact.username, msg.text)
            val replyMsgId = UUID.randomUUID().toString()
            val replyMessage = Message(
                messageId = replyText.hashCode().toString() + "_" + System.currentTimeMillis(),
                senderId = contactId,
                receiverId = msg.senderId,
                text = replyText,
                timestamp = System.currentTimeMillis(),
                status = "READ",
                replyToId = msg.messageId,
                replyToText = msg.text
            )
            database.messageDao().insertMessage(replyMessage)

            // Trigger local notification if user is not actively looking at this contact's chat
            if (contactId != activeChatRecipientId) {
                com.example.util.NotificationHelper.showNotification(
                    context,
                    contactId,
                    contact.username,
                    replyText
                )
            }
        }
    }

    private fun generateSimulationReply(name: String, userText: String): String {
        val text = userText.lowercase()
        return when {
            text.contains("hello") || text.contains("hi") || text.contains("hey") -> {
                "Hey! How's it going? This is $name. Glad you're testing i chat! 😄"
            }
            text.contains("how are you") -> {
                "I'm doing amazing! Just busy building cool Android apps with Jetpack Compose. What about you? 🚀"
            }
            text.contains("whatsapp") -> {
                "Yes! This app is heavily inspired by WhatsApp. Clean design, instant text-only messaging, zero distractions! 🛡️"
            }
            text.contains("features") || text.contains("what can you do") -> {
                "I support instant delivery ticks, reply citations, message forwarding, copying, deleting (for everyone too!), blocking/unblocking, and searching! Go ahead and test those out! 💯"
            }
            text.contains("help") -> {
                "I'm here! Tell me what's on your mind."
            }
            text.contains("emoji") -> {
                "Here are some emojis for you! 🌟🔥🎉🎈🌻🍕🚀🎸"
            }
            else -> {
                "Awesome! I totally get it. Feel free to try deleting this message, replying to it, or searching in the conversation! 📱✨"
            }
        }
    }

    private fun triggerGroupSimulationResponder(msg: Message) {
        repositoryScope.launch {
            delay(2000)
            val potentialMembers = listOf(
                Pair("alice", "Alice Smith"),
                Pair("bob", "Bob Jones"),
                Pair("charlie", "Charlie Brown")
            ).filter { it.first != msg.senderId }

            if (potentialMembers.isNotEmpty()) {
                val member = potentialMembers.random()
                database.groupDao().insertGroupMember(com.example.data.model.GroupMember(msg.receiverId, member.first))

                val replyText = generateSimulationReply(member.second, msg.text)
                val replyMessage = Message(
                    messageId = "group_sim_" + System.currentTimeMillis() + "_" + (1..1000).random(),
                    senderId = member.first,
                    receiverId = msg.receiverId,
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    status = "READ",
                    isGroupMessage = true
                )
                database.messageDao().insertMessage(replyMessage)

                if (msg.receiverId != activeChatRecipientId) {
                    com.example.util.NotificationHelper.showNotification(
                        context,
                        msg.receiverId,
                        "Group: " + member.second,
                        replyText
                    )
                }
            }
        }
    }

    // --- GROUP CHAT SYSTEM ---

    fun getAllGroupsFlow(): Flow<List<com.example.data.model.Group>> {
        return database.groupDao().getAllGroupsFlow()
    }

    fun getMyGroupsFlow(userId: String): Flow<List<com.example.data.model.Group>> {
        return database.groupDao().getMyGroupsFlow(userId)
    }

    fun getGroupMessagesFlow(groupId: String): Flow<List<Message>> {
        return database.messageDao().getGroupMessagesFlow(groupId)
    }

    fun getGroupMembers(groupId: String): Flow<List<User>> {
        return database.groupDao().getGroupMembers(groupId)
    }

    suspend fun getGroupById(groupId: String): com.example.data.model.Group? = withContext(Dispatchers.IO) {
        return@withContext database.groupDao().getGroupById(groupId)
    }

    suspend fun createGroup(groupId: String, name: String, description: String, creatorId: String): com.example.data.model.Group = withContext(Dispatchers.IO) {
        val group = com.example.data.model.Group(
            groupId = groupId,
            name = name,
            description = description,
            createdBy = creatorId,
            createdAt = System.currentTimeMillis()
        )
        database.groupDao().insertGroup(group)
        database.groupDao().insertGroupMember(com.example.data.model.GroupMember(groupId, creatorId))
        database.groupDao().insertGroupMember(com.example.data.model.GroupMember(groupId, "alice"))
        database.groupDao().insertGroupMember(com.example.data.model.GroupMember(groupId, "bob"))
        database.groupDao().insertGroupMember(com.example.data.model.GroupMember(groupId, "charlie"))

        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("groups").document(groupId).set(group)
                db.collection("group_members").document("${groupId}_${creatorId}").set(
                    mapOf("groupId" to groupId, "userId" to creatorId, "joinedAt" to System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving group to Firebase", e)
            }
        }
        return@withContext group
    }

    suspend fun joinGroup(groupId: String, userId: String) = withContext(Dispatchers.IO) {
        val member = com.example.data.model.GroupMember(groupId, userId)
        database.groupDao().insertGroupMember(member)
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("group_members").document("${groupId}_${userId}").set(member)
            } catch (e: Exception) {
                Log.e(TAG, "Error joining group in Firebase", e)
            }
        }
    }

    suspend fun leaveGroup(groupId: String, userId: String) = withContext(Dispatchers.IO) {
        database.groupDao().removeGroupMember(groupId, userId)
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("group_members").document("${groupId}_${userId}").delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving group in Firebase", e)
            }
        }
    }

    suspend fun isUserInGroup(groupId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext database.groupDao().isUserInGroup(groupId, userId) > 0
    }

    suspend fun updateGroup(group: com.example.data.model.Group) = withContext(Dispatchers.IO) {
        database.groupDao().insertGroup(group)
        if (FirebaseHelper.isFirebaseAvailable) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("groups").document(group.groupId).set(group)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating group in Firebase", e)
            }
        }
    }
}
