package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.User
import com.example.ui.components.Avatar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chatViewModel: ChatViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val contacts by chatViewModel.contacts.collectAsState()
    val pendingRequests by chatViewModel.pendingRequests.collectAsState()
    val myId by chatViewModel.currentUserId.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var targetFriendId by remember { mutableStateOf("") }
    var friendRequestError by remember { mutableStateOf<String?>(null) }
    var friendRequestSuccess by remember { mutableStateOf(false) }
    
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }

    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var groupLinkInput by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Chats, 1 = My Groups, 2 = Explore

    // Filter contacts by search query (excluding yourself)
    val filteredContacts = contacts.filter {
        it.userId != myId &&
        (it.username.contains(searchQuery, ignoreCase = true) || it.userId.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("i chat", fontWeight = FontWeight.Bold) },
                actions = {
                    // Profile/Requests Link
                    Box {
                        IconButton(onClick = onNavigateToProfile) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Friend Requests")
                        }
                        if (pendingRequests.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pendingRequests.size.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Settings Link
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddFriendDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_chat_fab")
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = { showJoinGroupDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("join_group_fab")
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Join Group")
                    }
                    FloatingActionButton(
                        onClick = { showCreateGroupDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("create_group_fab")
                    ) {
                        Icon(Icons.Default.Group, contentDescription = "Create Group")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { 
                    when (selectedTab) {
                        0 -> Text("Search chats or User ID...")
                        1 -> Text("Search your groups...")
                        else -> Text("Search public groups...")
                    }
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("chat_list_search")
            )

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chats", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("My Groups", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Explore", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    if (filteredContacts.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching chats" else "No active chats yet",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Tap the FAB to search or add friends!",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        // List of Chats
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredContacts) { contact ->
                                val chatMessagesFlow = remember(myId, contact.userId) {
                                    chatViewModel.repository.getMessagesFlow(myId, contact.userId)
                                }
                                val chatMessages by chatMessagesFlow.collectAsState(initial = emptyList())
                                val unreadCount by chatViewModel.repository.getUnreadCount(contact.userId, myId).collectAsState(initial = 0)

                                val lastMessage = chatMessages.lastOrNull { !it.isDeletedForMe }
                                
                                ChatListItem(
                                    contact = contact,
                                    lastMessage = lastMessage,
                                    unreadCount = unreadCount,
                                    myId = myId,
                                    onClick = { onNavigateToChat(contact.userId) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val myGroups by chatViewModel.myGroups.collectAsState()
                    val filteredGroups = myGroups.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching groups" else "You haven't joined any groups yet",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Tap the + FAB to create a group or go to Explore to join one!",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredGroups) { group ->
                                val groupMessagesFlow = remember(group.groupId) {
                                    chatViewModel.repository.getGroupMessagesFlow(group.groupId)
                                }
                                val groupMessages by groupMessagesFlow.collectAsState(initial = emptyList())
                                val lastMessage = groupMessages.lastOrNull { !it.isDeletedForMe }

                                GroupListItem(
                                    group = group,
                                    lastMessage = lastMessage,
                                    unreadCount = 0,
                                    onClick = { onNavigateToChat(group.groupId) },
                                    onLeaveClick = {
                                        chatViewModel.leaveGroup(group.groupId)
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
                2 -> {
                    val allGroups by chatViewModel.allGroups.collectAsState()
                    val myGroups by chatViewModel.myGroups.collectAsState()
                    val filteredAllGroups = allGroups.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredAllGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching public groups" else "No public groups available yet",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Be the first to create one by tapping the + FAB!",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredAllGroups) { group ->
                                val isMember = myGroups.any { it.groupId == group.groupId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = group.name.take(2).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = group.description.ifEmpty { "Public Group" },
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isMember) {
                                        Button(
                                            onClick = { onNavigateToChat(group.groupId) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Enter")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                chatViewModel.joinGroup(group.groupId) {
                                                    onNavigateToChat(group.groupId)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Join")
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateGroupDialog = false
                groupName = ""
                groupDescription = ""
            },
            title = { Text("Create New Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        placeholder = { Text("e.g. Android Developers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = { groupDescription = it },
                        label = { Text("Description") },
                        placeholder = { Text("What is this group about?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.trim().isNotEmpty()) {
                            chatViewModel.createGroup(groupName, groupDescription) { group ->
                                showCreateGroupDialog = false
                                groupName = ""
                                groupDescription = ""
                                onNavigateToChat(group.groupId)
                            }
                        }
                    },
                    enabled = groupName.trim().isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateGroupDialog = false
                    groupName = ""
                    groupDescription = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Join Group by Link/ID Dialog
    if (showJoinGroupDialog) {
        var joinError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showJoinGroupDialog = false
                groupLinkInput = ""
                joinError = null
            },
            title = { Text("Join Group by Link or ID", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter a Group ID or copy/paste a shareable Group Link to join the group instantly.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = groupLinkInput,
                        onValueChange = { 
                            groupLinkInput = it
                            joinError = null
                        },
                        label = { Text("Group Link or ID") },
                        placeholder = { Text("e.g. group_a1b2c3 or link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (joinError != null) {
                        Text(
                            text = joinError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = groupLinkInput.trim()
                        if (input.isNotEmpty()) {
                            // Extract group ID from link if user pasted a full link
                            val parsedId = if (input.contains("/group/join/")) {
                                input.substringAfter("/group/join/").substringBefore("?").trim()
                            } else {
                                input
                            }

                            if (parsedId.startsWith("group_")) {
                                chatViewModel.joinGroup(parsedId) {
                                    showJoinGroupDialog = false
                                    groupLinkInput = ""
                                    onNavigateToChat(parsedId)
                                }
                            } else {
                                joinError = "Invalid Group Link or ID. Group IDs start with 'group_'"
                            }
                        }
                    },
                    enabled = groupLinkInput.trim().isNotEmpty()
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinGroupDialog = false
                    groupLinkInput = ""
                    joinError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Friend Dialog
    if (showAddFriendDialog) {
        var searchedUser by remember { mutableStateOf<User?>(null) }
        var isSearching by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }
        var friendRequestInfo by remember { mutableStateOf<com.example.data.model.FriendRequest?>(null) }

        AlertDialog(
            onDismissRequest = {
                showAddFriendDialog = false
                friendRequestError = null
                friendRequestSuccess = false
                targetFriendId = ""
                searchedUser = null
                hasSearched = false
            },
            title = { Text("Search & Add Friends", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter the exact alphanumeric User ID of the person you want to chat with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetFriendId,
                            onValueChange = { 
                                targetFriendId = it.filter { char -> char.isLetterOrDigit() }.lowercase()
                                searchedUser = null
                                hasSearched = false
                                friendRequestError = null
                                friendRequestSuccess = false
                            },
                            placeholder = { Text("e.g. alice, bob, user123") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("friend_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (targetFriendId.isNotEmpty()) {
                                    isSearching = true
                                    friendRequestError = null
                                    friendRequestSuccess = false
                                    chatViewModel.searchUser(targetFriendId) { user ->
                                        searchedUser = user
                                        isSearching = false
                                        hasSearched = true
                                        if (user != null) {
                                            chatViewModel.getRequestBetweenUsers(user.userId) { req ->
                                                friendRequestInfo = req
                                            }
                                        } else {
                                            friendRequestError = "User not found."
                                        }
                                    }
                                }
                            },
                            enabled = targetFriendId.isNotEmpty() && !isSearching,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Search")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (hasSearched && searchedUser != null) {
                        val user = searchedUser!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(user = user, size = 56.dp, showOnlineIndicator = false)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "@${user.userId}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = user.bio,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val req = friendRequestInfo
                        if (req == null) {
                            Button(
                                onClick = {
                                    chatViewModel.sendFriendRequest(user.userId, myId) { result ->
                                        result.fold(
                                            onSuccess = {
                                                friendRequestSuccess = true
                                                friendRequestError = null
                                                chatViewModel.getRequestBetweenUsers(user.userId) { r ->
                                                    friendRequestInfo = r
                                                }
                                            },
                                            onFailure = { error ->
                                                friendRequestError = error.message ?: "Failed to send request."
                                                friendRequestSuccess = false
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Send Friend Request")
                            }
                        } else if (req.status == "PENDING") {
                            if (req.senderId == myId) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Waiting for @${user.userId} to accept...",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            chatViewModel.respondToFriendRequest(req, accept = true)
                                            friendRequestSuccess = true
                                            chatViewModel.getRequestBetweenUsers(user.userId) { r ->
                                                friendRequestInfo = r
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Accept")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            chatViewModel.respondToFriendRequest(req, accept = false)
                                            chatViewModel.getRequestBetweenUsers(user.userId) { r ->
                                                friendRequestInfo = r
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Reject")
                                    }
                                }
                            }
                        } else if (req.status == "ACCEPTED") {
                            Button(
                                onClick = {
                                    showAddFriendDialog = false
                                    onNavigateToChat(user.userId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Friends! Chat Now")
                            }
                        }
                    }

                    AnimatedVisibility(visible = friendRequestError != null) {
                        Text(
                            text = friendRequestError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = friendRequestSuccess && !isSearching) {
                        Text(
                            text = if (friendRequestInfo?.status == "ACCEPTED") "Connected! You are now friends." else "Friend request sent successfully!",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddFriendDialog = false
                    friendRequestError = null
                    friendRequestSuccess = false
                    targetFriendId = ""
                    searchedUser = null
                    hasSearched = false
                }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ChatListItem(
    contact: User,
    lastMessage: Message?,
    unreadCount: Int,
    myId: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Presence indicator
        Avatar(user = contact, size = 52.dp, showOnlineIndicator = true)

        Spacer(modifier = Modifier.width(16.dp))

        // Text Info Block
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name
                Text(
                    text = contact.username,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Timestamp
                val timeText = if (lastMessage != null) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(Date(lastMessage.timestamp))
                } else {
                    ""
                }
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Message Snippet or Typing Indicator
                if (contact.typingToId == myId) {
                    Text(
                        text = "typing...",
                        color = Color(0xFF25D366), // WhatsApp Green Typing Indicator
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display status ticks if message was sent by current user
                        if (lastMessage != null && lastMessage.senderId == myId) {
                            val tickIcon = if (lastMessage.status == "READ") Icons.Default.DoneAll else Icons.Default.Check
                            val tickColor = if (lastMessage.status == "READ") Color(0xFF53BDEB) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = null,
                                tint = tickColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        val snippetText = when {
                            lastMessage == null -> contact.bio
                            lastMessage.isDeletedForEveryone -> "🚫 This message was deleted"
                            else -> lastMessage.text
                        }
                        Text(
                            text = snippetText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Unread Count Badge
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupListItem(
    group: com.example.data.model.Group,
    lastMessage: Message?,
    unreadCount: Int,
    onClick: () -> Unit,
    onLeaveClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group Avatar
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = group.name.take(2).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastMessage != null) {
                    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val timeString = dateFormat.format(Date(lastMessage.timestamp))
                    Text(
                        text = timeString,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lastMessage != null) {
                        if (lastMessage.isDeletedForEveryone) "Message deleted"
                        else if (lastMessage.imageUrl != null) "📷 Photo"
                        else if (lastMessage.voiceUrl != null) "🎤 Voice Message"
                        else lastMessage.text
                    } else group.description.ifEmpty { "No messages yet" },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onLeaveClick != null) {
                    IconButton(
                        onClick = onLeaveClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Leave Group",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
