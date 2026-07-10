package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.FriendRequest
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = ChatRepository(application, database)

    // Current logged-in user id
    private val _currentUserId = MutableStateFlow<String>("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    // Active screen recipient user id
    private val _activeChatUserId = MutableStateFlow<String?>(null)
    val activeChatUserId: StateFlow<String?> = _activeChatUserId.asStateFlow()

    // Selected message reference for replies
    private val _replyingToMessage = MutableStateFlow<Message?>(null)
    val replyingToMessage: StateFlow<Message?> = _replyingToMessage.asStateFlow()

    // Searching text inside the current chat
    private val _messageQuery = MutableStateFlow("")
    val messageQuery: StateFlow<String> = _messageQuery.asStateFlow()

    // Active contacts/users (only accepted friends)
    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: StateFlow<List<User>> = _currentUserId
        .flatMapLatest { myId ->
            if (myId.isNotEmpty()) repository.getFriendsFlow(myId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Blocked list
    val blockedUserIds: StateFlow<Set<String>> = repository.blockedUserIds

    // Pending friend requests
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingRequests: StateFlow<List<FriendRequest>> = _currentUserId
        .flatMapLatest { myId ->
            if (myId.isNotEmpty()) repository.getPendingRequests(myId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active chat logs
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<Message>> = combine(_currentUserId, _activeChatUserId) { myId, otherId ->
        Pair(myId, otherId)
    }.flatMapLatest { (myId, otherId) ->
        if (myId.isNotEmpty() && otherId != null) {
            flow<List<Message>> {
                val group = repository.getGroupById(otherId)
                if (group != null) {
                    emitAll(repository.getGroupMessagesFlow(otherId))
                } else {
                    emitAll(repository.getMessagesFlow(myId, otherId))
                }
            }
        } else {
            flowOf<List<Message>>(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered message results if searching
    val filteredChatMessages: StateFlow<List<Message>> = combine(activeChatMessages, _messageQuery) { msgs, query ->
        if (query.trim().isEmpty()) msgs
        else msgs.filter { it.text.contains(query, ignoreCase = true) && !it.isDeletedForEveryone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active recipient details
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatUser: StateFlow<User?> = _activeChatUserId
        .flatMapLatest { otherId ->
            if (otherId != null) database.userDao().getUserFlow(otherId) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active group details
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatGroup: StateFlow<com.example.data.model.Group?> = _activeChatUserId
        .flatMapLatest { otherId ->
            if (otherId != null) {
                flow<com.example.data.model.Group?> {
                    emit(repository.getGroupById(otherId))
                }
            } else {
                flowOf<com.example.data.model.Group?>(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // My Group memberships
    @OptIn(ExperimentalCoroutinesApi::class)
    val myGroups: StateFlow<List<com.example.data.model.Group>> = _currentUserId
        .flatMapLatest { myId ->
            if (myId.isNotEmpty()) repository.getMyGroupsFlow(myId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Groups (for discover & join)
    val allGroups: StateFlow<List<com.example.data.model.Group>> = repository.getAllGroupsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initSession(myId: String) {
        if (_currentUserId.value != myId) {
            _currentUserId.value = myId
            repository.startRealtimeSync(myId)
        }
    }

    fun setActiveChat(otherId: String?) {
        _activeChatUserId.value = otherId
        repository.setActiveChatRecipientId(otherId)
        _replyingToMessage.value = null
        _messageQuery.value = ""
        
        val myId = _currentUserId.value
        if (myId.isNotEmpty() && otherId != null) {
            viewModelScope.launch {
                val isGroup = repository.getGroupById(otherId) != null
                if (!isGroup) {
                    repository.markAsRead(otherId, myId)
                }
            }
        }
    }

    fun setMessageQuery(query: String) {
        _messageQuery.value = query
    }

    fun setReplyingTo(message: Message?) {
        _replyingToMessage.value = message
    }

    fun sendMessage(text: String) {
        val myId = _currentUserId.value
        val otherId = _activeChatUserId.value
        if (myId.isNotEmpty() && otherId != null && text.trim().isNotEmpty()) {
            val replyMsg = _replyingToMessage.value
            viewModelScope.launch {
                val isGroup = repository.getGroupById(otherId) != null
                repository.sendMessage(
                    senderId = myId,
                    receiverId = otherId,
                    text = text,
                    replyToId = replyMsg?.messageId,
                    replyToText = if (replyMsg != null) {
                        if (replyMsg.isDeletedForEveryone) "Message deleted" else replyMsg.text
                    } else null,
                    isGroupMessage = isGroup
                )
                _replyingToMessage.value = null
            }
        }
    }

    fun sendPhotoMessage(base64Image: String, caption: String = "") {
        val myId = _currentUserId.value
        val otherId = _activeChatUserId.value
        if (myId.isNotEmpty() && otherId != null) {
            viewModelScope.launch {
                val isGroup = repository.getGroupById(otherId) != null
                repository.sendMessage(
                    senderId = myId,
                    receiverId = otherId,
                    text = caption.ifEmpty { "[Photo]" },
                    imageUrl = base64Image,
                    isGroupMessage = isGroup
                )
            }
        }
    }

    fun sendVoiceMessage(base64Voice: String, durationMs: Long) {
        val myId = _currentUserId.value
        val otherId = _activeChatUserId.value
        if (myId.isNotEmpty() && otherId != null) {
            viewModelScope.launch {
                val isGroup = repository.getGroupById(otherId) != null
                repository.sendMessage(
                    senderId = myId,
                    receiverId = otherId,
                    text = "[Voice Message]",
                    voiceUrl = base64Voice,
                    voiceDurationMs = durationMs,
                    isGroupMessage = isGroup
                )
            }
        }
    }

    // --- GROUP CHAT ACTIONS ---

    fun createGroup(name: String, description: String, onComplete: (com.example.data.model.Group) -> Unit) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty() && name.trim().isNotEmpty()) {
            viewModelScope.launch {
                val groupId = "group_" + java.util.UUID.randomUUID().toString().take(6)
                val group = repository.createGroup(groupId, name.trim(), description.trim(), myId)
                onComplete(group)
            }
        }
    }

    fun joinGroup(groupId: String, onComplete: () -> Unit = {}) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            viewModelScope.launch {
                repository.joinGroup(groupId, myId)
                onComplete()
            }
        }
    }

    fun leaveGroup(groupId: String, onComplete: () -> Unit = {}) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            viewModelScope.launch {
                repository.leaveGroup(groupId, myId)
                onComplete()
            }
        }
    }

    fun updateGroup(group: com.example.data.model.Group) {
        viewModelScope.launch {
            repository.updateGroup(group)
        }
    }

    fun getGroupMembers(groupId: String): kotlinx.coroutines.flow.Flow<List<User>> {
        return repository.getGroupMembers(groupId)
    }

    fun removeGroupMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repository.leaveGroup(groupId, userId)
        }
    }

    fun setTyping(isTyping: Boolean) {
        val myId = _currentUserId.value
        val otherId = _activeChatUserId.value
        if (myId.isNotEmpty() && otherId != null) {
            viewModelScope.launch {
                repository.updateTypingStatus(myId, otherId, isTyping)
            }
        }
    }

    fun deleteMessageForMe(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForMe(messageId)
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(messageId)
        }
    }

    fun addMessageReaction(messageId: String, emoji: String?) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            viewModelScope.launch {
                repository.addMessageReaction(messageId, myId, emoji)
            }
        }
    }

    fun forwardMessage(message: Message, targetUserId: String) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            viewModelScope.launch {
                repository.sendMessage(
                    senderId = myId,
                    receiverId = targetUserId,
                    text = if (message.isDeletedForEveryone) "Message deleted" else message.text
                )
            }
        }
    }

    // --- FRIENDS & BLOCK ACTIONS ---

    fun searchUser(userId: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = repository.searchUser(userId)
            onResult(user)
        }
    }

    fun getRequestBetweenUsers(otherUserId: String, onResult: (FriendRequest?) -> Unit) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            viewModelScope.launch {
                val req = repository.getRequestBetweenUsers(myId, otherUserId)
                onResult(req)
            }
        } else {
            onResult(null)
        }
    }

    fun getUserFlow(userId: String): kotlinx.coroutines.flow.Flow<User?> {
        return repository.getUserFlow(userId)
    }

    fun sendFriendRequest(targetUserId: String, senderName: String, onComplete: (Result<Boolean>) -> Unit) {
        val myId = _currentUserId.value
        if (myId.isNotEmpty() && targetUserId.trim().isNotEmpty()) {
            viewModelScope.launch {
                val res = repository.sendFriendRequest(myId, senderName, targetUserId.trim())
                onComplete(res)
            }
        }
    }

    fun respondToFriendRequest(request: FriendRequest, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToRequest(request, accept)
        }
    }

    fun blockUser(userId: String) {
        repository.blockUser(userId)
    }

    fun unblockUser(userId: String) {
        repository.unblockUser(userId)
    }

    override fun onCleared() {
        super.onCleared()
        val myId = _currentUserId.value
        if (myId.isNotEmpty()) {
            repository.stopRealtimeSync(myId)
        }
    }
}
